/*
 * Shared helpers for the logged-in pages: header (user, connection state, logout),
 * formatting, toasts, a tiny polling loop (every 1 second, redraw only when the
 * server's version changes) and a minimal line chart.
 */
'use strict';

const POLL_INTERVAL_MS = 1000;

// ---------- formatting ----------

const Fmt = {
  money(value) {
    const sign = value < 0 ? '-' : '';
    return sign + '$' + Math.abs(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },
  signedMoney(value) {
    return (value >= 0 ? '+' : '-') + '$' + Math.abs(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },
  price(value) {
    return value === null || value === undefined ? '-' : Number(value).toFixed(2);
  },
  percent(value) {
    return (value * 100).toFixed(1) + '%';
  },
  shares(value) {
    return Number(value).toLocaleString('en-US');
  },
  yesNo(flag) {
    return flag ? 'Yes' : 'No';
  },
  ids(list) {
    return list && list.length ? list.map((id) => '#' + id).join(', ') : '-';
  },
};

function el(tag, attrs, ...children) {
  const node = document.createElement(tag);
  if (attrs) {
    for (const [key, value] of Object.entries(attrs)) {
      if (key === 'class') node.className = value;
      else if (key === 'text') node.textContent = value;
      else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
      else node.setAttribute(key, value);
    }
  }
  for (const child of children) {
    if (child === null || child === undefined) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---------- toast messages ----------

let toastTimer = null;
function toast(message, kind) {
  let node = document.getElementById('toast');
  if (!node) {
    node = el('div', { id: 'toast' });
    document.body.append(node);
  }
  node.textContent = message;
  node.className = 'show ' + (kind || '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.className = ''; }, kind === 'error' ? 7000 : 5000);
}

function showError(err) {
  if (err instanceof ApiError && err.unauthorized) {
    goToLogin('Your session is no longer valid on the server. Please log in again.');
    return;
  }
  toast(err.message || String(err), 'error');
}

function goToLogin(message) {
  const query = message ? '?message=' + encodeURIComponent(message) : '';
  window.location.href = 'index.html' + query;
}

// ---------- header shared by events.html and account.html ----------

const Session = { user: null };

async function initHeader(activeTab) {
  try {
    const me = await Api.me();
    Session.user = me.userName;
  } catch (err) {
    goToLogin(err.status === 401 ? 'Please log in first.' : err.message);
    return false;
  }
  document.getElementById('user-badge').textContent = 'Logged in as: ' + Session.user;
  document.title = 'Guess Market - ' + Session.user;
  document.querySelectorAll('nav.tabs a').forEach((a) => a.classList.toggle('active', a.dataset.tab === activeTab));
  document.getElementById('logout-button').addEventListener('click', async () => {
    Poller.stop();
    try { await Api.logout(); } catch (e) { /* the session is gone either way */ }
    goToLogin('You have been logged out.');
  });
  return true;
}

function setConnection(connected, message) {
  const node = document.getElementById('conn');
  if (!node) return;
  node.classList.toggle('connected', connected);
  node.classList.toggle('disconnected', !connected);
  node.querySelector('span:last-child').textContent = connected ? 'Connected' : 'Disconnected: ' + message;
}

// ---------- polling ----------

const Poller = {
  tasks: [],
  timer: null,
  running: false,
  register(task) { this.tasks.push(task); },
  start() {
    this.running = true;
    this.tick();
  },
  stop() {
    this.running = false;
    clearTimeout(this.timer);
  },
  refreshSoon() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.tick(), 50);
  },
  async tick() {
    if (!this.running) return;
    clearTimeout(this.timer);
    let failure = null;
    for (const task of this.tasks) {
      if (task.isActive && !task.isActive()) continue;
      try {
        const value = await task.fetch();
        task.apply(value);
      } catch (err) {
        if (err instanceof ApiError && err.unauthorized) {
          goToLogin('Your session is no longer valid on the server. Please log in again.');
          return;
        }
        failure = err;
      }
    }
    setConnection(failure === null, failure ? failure.message : '');
    if (this.running) {
      this.timer = setTimeout(() => this.tick(), POLL_INTERVAL_MS);
    }
  },
};

// ---------- minimal line chart on a canvas ----------

function drawLineChart(canvas, series, options) {
  const opts = Object.assign({ min: null, max: null, colors: ['#2d6aa3', '#c0392b', '#1e8449'], labels: [] }, options || {});
  const ctx = canvas.getContext('2d');
  const width = canvas.width = canvas.clientWidth * (window.devicePixelRatio || 1);
  const height = canvas.height = canvas.clientHeight * (window.devicePixelRatio || 1);
  ctx.clearRect(0, 0, width, height);
  const all = series.flat();
  if (all.length === 0) {
    ctx.fillStyle = '#5f6f80';
    ctx.font = (12 * (window.devicePixelRatio || 1)) + 'px Segoe UI';
    ctx.fillText('No data yet', 12, 24);
    return;
  }
  let min = opts.min !== null ? opts.min : Math.min(...all);
  let max = opts.max !== null ? opts.max : Math.max(...all);
  if (max === min) { max += 1; min -= 1; }
  const pad = 28 * (window.devicePixelRatio || 1);
  const x = (i, n) => pad + (n <= 1 ? 0 : (i / (n - 1)) * (width - 2 * pad));
  const y = (v) => height - pad - ((v - min) / (max - min)) * (height - 2 * pad);

  ctx.strokeStyle = '#d9e1ea';
  ctx.lineWidth = 1;
  for (let g = 0; g <= 4; g++) {
    const gy = pad + (g / 4) * (height - 2 * pad);
    ctx.beginPath(); ctx.moveTo(pad, gy); ctx.lineTo(width - pad, gy); ctx.stroke();
    ctx.fillStyle = '#5f6f80';
    ctx.font = (11 * (window.devicePixelRatio || 1)) + 'px Segoe UI';
    const value = max - (g / 4) * (max - min);
    ctx.fillText(opts.format ? opts.format(value) : value.toFixed(2), 2, gy + 4);
  }
  series.forEach((points, s) => {
    ctx.strokeStyle = opts.colors[s % opts.colors.length];
    ctx.lineWidth = 2 * (window.devicePixelRatio || 1);
    ctx.beginPath();
    points.forEach((v, i) => { i === 0 ? ctx.moveTo(x(i, points.length), y(v)) : ctx.lineTo(x(i, points.length), y(v)); });
    ctx.stroke();
    if (opts.labels[s]) {
      ctx.fillStyle = ctx.strokeStyle;
      ctx.fillText(opts.labels[s], pad + s * 90 * (window.devicePixelRatio || 1), 14 * (window.devicePixelRatio || 1));
    }
  });
}
