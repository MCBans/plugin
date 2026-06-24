package com.mcbans.plugin.core.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcbans.plugin.core.McBansConfig;
import com.mcbans.plugin.core.model.BanSyncAction;
import com.mcbans.plugin.core.model.Notice;
import com.mcbans.plugin.core.platform.BanSyncHandler;
import com.mcbans.plugin.core.platform.CursorStore;
import com.mcbans.plugin.core.platform.PluginLogger;
import com.mcbans.plugin.core.protocol.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The generic MCBans {@code /plugin/ws} client: a persistent, authenticated WebSocket session built
 * on the JDK's {@link java.net.http.WebSocket} (so TLS is handled for free). It implements the
 * lifecycle from the protocol docs:
 *
 * <ul>
 *   <li>open → {@code register} handshake (with the persisted {@code lastBanSyncId} cursor),</li>
 *   <li>{@code ref}-correlated request/response with per-request timeouts,</li>
 *   <li>unsolicited {@code banSync}/{@code notice} push handling — apply, advance, persist cursor,</li>
 *   <li>exponential-backoff-with-jitter reconnect that always re-registers.</li>
 * </ul>
 *
 * Sends are serialized through a single chained future (a WebSocket may not have two in-flight
 * {@code sendText} calls). Callbacks run on the HttpClient executor — never the game thread — so
 * the {@link BanSyncHandler} is responsible for hopping to the main thread.
 */
public final class McBansSocketClient {

    private final McBansConfig config;
    private final PluginLogger log;
    private final BanSyncHandler handler;
    private final CursorStore cursorStore;

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mcbans-ws-scheduler");
                t.setDaemon(true);
                return t;
            });

    private final AtomicLong refSeq = new AtomicLong();
    private final ConcurrentHashMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();

    private volatile boolean running;
    private volatile boolean registered;
    private volatile int serverId = -1;
    private volatile WebSocket webSocket;
    private volatile long cursor;
    private int backoffAttempt;

    // Serializes outbound frames: each send chains onto the previous one's completion.
    private final Object sendLock = new Object();
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    public McBansSocketClient(McBansConfig config, PluginLogger log, BanSyncHandler handler,
                              CursorStore cursorStore) {
        this.config = config;
        this.log = log;
        this.handler = handler == null ? BanSyncHandler.NOOP : handler;
        this.cursorStore = cursorStore;
        this.cursor = cursorStore == null ? 0L : cursorStore.load();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Begin connecting (idempotent). Returns immediately; connection happens asynchronously. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        backoffAttempt = 0;
        connect();
    }

    /** Tear down the session and stop reconnecting. */
    public synchronized void stop() {
        running = false;
        registered = false;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").exceptionally(t -> null);
        }
        pending.values().forEach(f -> f.completeExceptionally(new IllegalStateException("client stopped")));
        pending.clear();
        scheduler.shutdownNow();
    }

    public boolean isRegistered() {
        return registered;
    }

    public int serverId() {
        return serverId;
    }

    // ---- connection ---------------------------------------------------------------------------

    private void connect() {
        if (!running) {
            return;
        }
        log.info("[MCBans] Connecting to " + config.endpoint() + " ...");
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(config.endpoint()), new Listener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        log.warn("[MCBans] Connection failed: " + rootMessage(err));
                        scheduleReconnect();
                        return;
                    }
                    this.webSocket = ws;
                    register();
                });
    }

    private void register() {
        JsonObject data = Json.obj(Map.of(
                "apiKey", config.apiKey(),
                "version", config.version(),
                "lastBanSyncId", cursor));
        sendCommand("register", data).whenComplete((reply, err) -> {
            if (err != null) {
                log.warn("[MCBans] register failed: " + rootMessage(err));
                forceReconnect();
                return;
            }
            if (!Json.bool(reply, "ok")) {
                log.error("[MCBans] register rejected: " + Json.str(reply, "error"));
                // Auth/whitelist problem won't fix itself on a tight loop — back off hard.
                forceReconnect();
                return;
            }
            JsonObject body = reply.getAsJsonObject("data");
            serverId = body != null && body.has("serverId") ? body.get("serverId").getAsInt() : -1;
            registered = true;
            backoffAttempt = 0;
            log.info("[MCBans] Registered as server #" + serverId + " (v" + config.version()
                    + ", cursor=" + cursor + ").");
        });
    }

    // ---- sending ------------------------------------------------------------------------------

    /**
     * Send a command and return a future for its reply frame. The future fails with
     * {@link TimeoutException} if no reply arrives within {@link McBansConfig#requestTimeoutMs()}.
     */
    public CompletableFuture<JsonObject> sendCommand(String cmd, JsonObject data) {
        WebSocket ws = webSocket;
        if (ws == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("socket not connected"));
        }
        long ref = refSeq.incrementAndGet();
        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", cmd);
        frame.addProperty("ref", ref);
        if (data != null) {
            frame.add("data", data);
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(ref, future);

        // Per-request timeout.
        var timeout = scheduler.schedule(() -> {
            CompletableFuture<JsonObject> f = pending.remove(ref);
            if (f != null) {
                f.completeExceptionally(new TimeoutException("no reply for '" + cmd + "' (ref " + ref + ")"));
            }
        }, config.requestTimeoutMs(), TimeUnit.MILLISECONDS);
        future.whenComplete((r, e) -> timeout.cancel(false));

        rawSend(Json.write(frame)).whenComplete((v, err) -> {
            if (err != null) {
                CompletableFuture<JsonObject> f = pending.remove(ref);
                if (f != null) {
                    f.completeExceptionally(err);
                }
            }
        });
        return future;
    }

    private CompletableFuture<?> rawSend(String text) {
        synchronized (sendLock) {
            WebSocket ws = webSocket;
            if (ws == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("socket not connected"));
            }
            sendChain = sendChain
                    .exceptionally(t -> null) // a prior send's failure must not wedge the chain
                    .thenCompose(ignored -> ws.sendText(text, true));
            return sendChain;
        }
    }

    // ---- receiving ----------------------------------------------------------------------------

    private void handleFrame(String text) {
        JsonObject frame;
        try {
            frame = Json.parse(text);
        } catch (RuntimeException e) {
            log.warn("[MCBans] Dropping unparseable frame: " + e.getMessage());
            return;
        }

        if (frame.has("push")) {
            dispatchPush(frame);
            return;
        }
        if (frame.has("ref")) {
            long ref = frame.get("ref").getAsLong();
            CompletableFuture<JsonObject> f = pending.remove(ref);
            if (f != null) {
                f.complete(frame);
            }
            return;
        }
        // Frame with neither push nor ref (e.g. a transport-level {ok:false,error:...} with no ref).
        if (frame.has("error")) {
            log.warn("[MCBans] Server error frame: " + Json.str(frame, "error"));
        }
    }

    private void dispatchPush(JsonObject frame) {
        String type = Json.str(frame, "push");
        try {
            switch (type) {
                case "banSync" -> {
                    JsonArray actions = frame.getAsJsonArray("actions");
                    if (actions != null) {
                        for (JsonElement el : actions) {
                            BanSyncAction a = BanSyncAction.fromJson(el.getAsJsonObject());
                            if (a.isBan()) {
                                handler.enforce(a);
                            } else {
                                handler.lift(a);
                            }
                        }
                    }
                    advanceCursor(frame.get("lastid"));
                }
                case "notice" -> {
                    JsonArray notices = frame.getAsJsonArray("notices");
                    if (notices != null) {
                        for (JsonElement el : notices) {
                            handler.onNotice(Notice.fromJson(el.getAsJsonObject()));
                        }
                    }
                }
                default -> log.warn("[MCBans] Unknown push type: " + type);
            }
        } catch (RuntimeException e) {
            log.error("[MCBans] Error handling '" + type + "' push", e);
        }
    }

    private void advanceCursor(JsonElement lastId) {
        if (lastId == null || lastId.isJsonNull()) {
            return;
        }
        long next;
        try {
            next = lastId.getAsLong();
        } catch (NumberFormatException e) {
            return;
        }
        if (next > cursor) {
            cursor = next;
            if (cursorStore != null) {
                cursorStore.save(cursor);
            }
        }
    }

    // ---- reconnect ----------------------------------------------------------------------------

    private void forceReconnect() {
        WebSocket ws = webSocket;
        webSocket = null;
        registered = false;
        if (ws != null) {
            ws.abort();
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        registered = false;
        // Exponential backoff 1s → 30s, plus up to 1s jitter.
        long base = Math.min(30_000L, 1_000L * (1L << Math.min(backoffAttempt, 5)));
        long delay = base + ThreadLocalRandom.current().nextLong(1_000L);
        backoffAttempt++;
        log.info("[MCBans] Reconnecting in " + delay + "ms (attempt " + backoffAttempt + ").");
        try {
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ignored) {
            // scheduler shut down during stop(); nothing to do.
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    /** WebSocket callback adapter. Accumulates partial text frames until {@code last}. */
    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String full = buffer.toString();
                buffer.setLength(0);
                try {
                    handleFrame(full);
                } catch (RuntimeException e) {
                    log.error("[MCBans] Frame handling error", e);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("[MCBans] Socket closed (" + statusCode + (reason.isEmpty() ? "" : " " + reason) + ").");
            if (webSocket == ws) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("[MCBans] Socket error: " + rootMessage(error));
            if (webSocket == ws) {
                forceReconnect();
            }
        }
    }
}
