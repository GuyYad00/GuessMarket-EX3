/*
 * Thin wrapper around the Guess Market server API (exactly the API the JavaFX client uses).
 * All calls go to /guessmarket/... on the same origin; the Node host forwards them to Tomcat.
 * Every non-2xx answer carries {"error": "..."} and is turned into a thrown ApiError.
 */
'use strict';

const API_BASE = '/guessmarket';

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
  get unauthorized() { return this.status === 401; }
}

async function apiCall(method, path, params) {
  const query = params ? '?' + new URLSearchParams(params).toString() : '';
  let response;
  try {
    response = await fetch(API_BASE + path + query, { method, credentials: 'same-origin' });
  } catch (e) {
    throw new ApiError(0, 'Cannot reach the web client host (' + e.message + ').');
  }
  const text = await response.text();
  let body = null;
  if (text) {
    try { body = JSON.parse(text); } catch (e) { body = { error: text }; }
  }
  if (!response.ok) {
    const message = body && body.error ? body.error : 'Request failed (HTTP ' + response.status + ').';
    throw new ApiError(response.status, message);
  }
  return body;
}

const Api = {
  login: (username) => apiCall('POST', '/login', { username }),
  logout: () => apiCall('POST', '/logout'),
  me: () => apiCall('GET', '/me'),
  events: () => apiCall('GET', '/events'),
  eventDetails: (id) => apiCall('GET', '/events/details', { id }),
  openEvent: (id) => apiCall('POST', '/events/open', { id }),
  closeEvent: (id, winner) => apiCall('POST', '/events/close', { id, winner }),
  buyShares: (id, option, shares) => apiCall('POST', '/events/buy', { id, option, shares }),
  placeOrder: (id, option, side, price, quantity) => apiCall('POST', '/events/order', { id, option, side, price, quantity }),
  users: () => apiCall('GET', '/users'),
  myDetails: () => apiCall('GET', '/users/details'),
  loadFunds: (amount) => apiCall('POST', '/users/funds', { amount }),
  // chat (the exercise 3 bonus; the web client simply reuses the same two endpoints)
  chatSince: (version) => apiCall('GET', '/chat', { version }),
  sendChat: (text) => apiCall('POST', '/chat', { text }),
};
