/*
 * Guess Market - web client host (exercise 4).
 *
 * A tiny Node.js server with ZERO npm dependencies (only the built-in http/fs/path modules):
 *   1. serves the static files of the web client from ./public on http://localhost:3000
 *   2. forwards every request that starts with /guessmarket to the existing Tomcat server
 *      (http://localhost:8080/guessmarket), including cookies, so the browser talks to the
 *      very same API the JavaFX client uses, from the same origin (no CORS, no changes to
 *      the WAR or to the way Tomcat is started).
 *
 * Usage:  node server.js            (or run.bat)
 * Options: PORT=3000  TOMCAT=http://localhost:8080  (environment variables)
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT || '3000', 10);
const TOMCAT = new URL(process.env.TOMCAT || 'http://localhost:8080');
const PUBLIC_DIR = path.join(__dirname, 'public');
const API_PREFIX = '/guessmarket';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

function proxy(req, res) {
  const options = {
    hostname: TOMCAT.hostname,
    port: TOMCAT.port || 80,
    path: req.url,
    method: req.method,
    headers: Object.assign({}, req.headers, { host: TOMCAT.host }),
  };
  const upstream = http.request(options, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', (err) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      error: 'The Guess Market server at ' + TOMCAT.origin + ' is not reachable (' + err.code + '). '
        + 'Is Tomcat running with guessmarket.war deployed?',
    }));
  });
  req.pipe(upstream);
}

function serveStatic(req, res) {
  let urlPath;
  try {
    urlPath = decodeURIComponent(req.url.split('?')[0]);
  } catch (e) {
    // malformed percent-encoding (for example "/%zz"): answer 400 instead of throwing
    res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Bad request: malformed URL');
    return;
  }
  if (urlPath === '/') {
    urlPath = '/index.html';
  }
  const filePath = path.normalize(path.join(PUBLIC_DIR, urlPath));
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not found: ' + urlPath);
      return;
    }
    const type = MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-cache' });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  try {
    if (req.url === API_PREFIX || req.url.startsWith(API_PREFIX + '/') || req.url.startsWith(API_PREFIX + '?')) {
      proxy(req, res);
    } else {
      serveStatic(req, res);
    }
  } catch (err) {
    // a single odd request must never take the host down
    console.error('Request failed:', req.method, req.url, err.message);
    if (!res.headersSent) {
      res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    }
    res.end('Internal error');
  }
});

server.listen(PORT, () => {
  console.log('');
  console.log('  Guess Market web client is ready:');
  console.log('    open   http://localhost:' + PORT + '   in your browser');
  console.log('    API    ' + API_PREFIX + '/* is forwarded to ' + TOMCAT.origin + API_PREFIX);
  console.log('');
  console.log('  Press Ctrl+C to stop.');
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error('Port ' + PORT + ' is already in use. Set PORT=<other> and run again.');
  } else {
    console.error(err);
  }
  process.exit(1);
});
