#!/usr/bin/env node
/*
 * Zero-dependency, protocol-compatible mock of the MCBans /plugin/ws WebSocket endpoint, for the
 * end-to-end test (scripts/e2e-test.sh). It speaks just enough of the protocol to drive a real
 * plugin through a full round trip:
 *
 *   - completes the WS handshake on /plugin/ws,
 *   - answers `register` with ok + a serverId,
 *   - answers `loginNew`/`login` with a configurable ban status (clean unless the name is BANNED),
 *   - answers ban writes (globalBan/localBan/tempBan/ipBan/unBan) with result "y" and, on a ban,
 *     pushes a matching `banSync` action back to the client,
 *   - answers `verify_user`/`verifyUser` with a success tuple,
 *   - records every received command frame as one JSON line to FRAMES so the test can assert,
 *   - pushes a `notice` shortly after register so the server->client push path is exercised.
 *
 * Env: PORT (default 8085), FRAMES (path to the frames log), BANNED (a player name to deny login).
 */
'use strict';
const http = require('http');
const crypto = require('crypto');
const fs = require('fs');

const PORT = parseInt(process.env.PORT || '8085', 10);
const FRAMES = process.env.FRAMES || '/tmp/mcbans-e2e-frames.log';
const BANNED = (process.env.BANNED || 'BannedGuy').toLowerCase();
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function record(obj) {
  fs.appendFileSync(FRAMES, JSON.stringify(obj) + '\n');
}
function log(...a) { console.log('[mock]', ...a); }

// ---- minimal RFC6455 framing ----
function decodeFrames(buf, onText) {
  let offset = 0;
  while (offset + 2 <= buf.length) {
    const b0 = buf[offset], b1 = buf[offset + 1];
    const opcode = b0 & 0x0f;
    const masked = (b1 & 0x80) !== 0;
    let len = b1 & 0x7f;
    let p = offset + 2;
    if (len === 126) { if (p + 2 > buf.length) break; len = buf.readUInt16BE(p); p += 2; }
    else if (len === 127) { if (p + 8 > buf.length) break; len = Number(buf.readBigUInt64BE(p)); p += 8; }
    let mask;
    if (masked) { if (p + 4 > buf.length) break; mask = buf.slice(p, p + 4); p += 4; }
    if (p + len > buf.length) break;
    let payload = buf.slice(p, p + len);
    if (masked) { const out = Buffer.alloc(len); for (let i = 0; i < len; i++) out[i] = payload[i] ^ mask[i & 3]; payload = out; }
    offset = p + len;
    if (opcode === 0x8) return { rest: buf.slice(offset), close: true };  // close
    if (opcode === 0x9) continue;                                          // ping (ignore; demo)
    if (opcode === 0x1) onText(payload.toString('utf8'));                  // text
  }
  return { rest: buf.slice(offset), close: false };
}
function encodeText(str) {
  const data = Buffer.from(str, 'utf8');
  const n = data.length;
  let header;
  if (n < 126) header = Buffer.from([0x81, n]);
  else if (n < 65536) { header = Buffer.alloc(4); header[0] = 0x81; header[1] = 126; header.writeUInt16BE(n, 2); }
  else { header = Buffer.alloc(10); header[0] = 0x81; header[1] = 127; header.writeBigUInt64BE(BigInt(n), 2); }
  return Buffer.concat([header, data]);
}

const server = http.createServer((req, res) => { res.writeHead(426); res.end('upgrade required'); });

server.on('upgrade', (req, socket) => {
  if (!req.url.startsWith('/plugin/ws')) { socket.destroy(); return; }
  const key = req.headers['sec-websocket-key'];
  const accept = crypto.createHash('sha1').update(key + GUID).digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\nConnection: Upgrade\r\n' +
    'Sec-WebSocket-Accept: ' + accept + '\r\n\r\n');
  log('client connected');
  const send = (obj) => socket.write(encodeText(JSON.stringify(obj)));

  let buffer = Buffer.alloc(0);
  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    const { rest, close } = decodeFrames(buffer, (text) => handle(text, send));
    buffer = rest;
    if (close) socket.end();
  });
  socket.on('error', () => {});
});

function handle(text, send) {
  let f;
  try { f = JSON.parse(text); } catch (e) { return; }
  const cmd = f.cmd;
  const ref = f.ref;
  const data = f.data || {};
  if (cmd !== 'register') record({ cmd, data });      // register holds the api key; skip from asserts
  log('recv', cmd, JSON.stringify(data));

  switch (cmd) {
    case 'register':
      send({ ref, cmd, ok: true, data: { serverId: 1, version: data.version || 3, readOnly: false } });
      // exercise server->client push shortly after registration
      setTimeout(() => send({ push: 'notice', serverId: 1,
        notices: [{ id: 1, message: 'E2E mock notice: hello plugin' }] }), 400);
      break;
    case 'login': case 'loginNew': {
      const name = (data.name || '').toLowerCase();
      const banned = name === BANNED;
      send({ ref, cmd, ok: true, data: {
        banStatus: banned ? 'g' : 'n', banReason: banned ? 'e2e test ban' : '',
        playerRep: 10, altCount: 0, is_mcbans_mod: 'n', bans: [] } });
      break;
    }
    case 'globalBan': case 'localBan': case 'tempBan': {
      const player = data.player || data.player_uuid || 'unknown';
      send({ ref, cmd, ok: true, data: { result: 'y', msg: 'banned', player } });
      // push the matching ban-sync action back to the plugin
      send({ push: 'banSync', serverId: 1, lastid: 101,
        actions: [{ uuid: data.player_uuid || '', name: player, id: '101', do: 'ban' }] });
      break;
    }
    case 'ipBan':
      send({ ref, cmd, ok: true, data: { result: 'y' } });
      break;
    case 'unBan': {
      const player = data.player || data.player_uuid || 'unknown';
      send({ ref, cmd, ok: true, data: { result: 'y', msg: 'unbanned', player } });
      break;
    }
    case 'verify_user': case 'verifyUser':
      send({ ref, cmd, ok: true, data: 'y;' + (data.name || data.player || 'Player')
        + ';Registration Complete. Thank you for registering with MCBans!' });
      break;
    case 'playerDisconnect':
      send({ ref, cmd, ok: true, data: { result: 'y' } });
      break;
    default:
      send({ ref, cmd, ok: true, data: {} });
  }
}

server.listen(PORT, '127.0.0.1', () => log('listening on ws://127.0.0.1:' + PORT + '/plugin/ws (frames -> ' + FRAMES + ')'));
