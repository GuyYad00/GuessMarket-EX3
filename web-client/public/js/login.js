/* Login screen: a single name, no password. A taken name is reported and the user tries another one. */
'use strict';

(function () {
  const form = document.getElementById('login-form');
  const input = document.getElementById('username');
  const button = document.getElementById('login-button');
  const error = document.getElementById('error');

  const message = new URLSearchParams(window.location.search).get('message');
  if (message) {
    error.textContent = message;
  }

  // Already logged in (e.g. the user pressed Back)? Go straight to the events screen.
  Api.me().then(() => { window.location.href = 'events.html'; }).catch(() => { /* not logged in: stay here */ });

  input.addEventListener('input', () => { error.textContent = ''; });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const name = input.value.trim();
    if (!name) {
      error.textContent = 'Please enter a user name.';
      return;
    }
    button.disabled = true;
    input.disabled = true;
    try {
      await Api.login(name);
      window.location.href = 'events.html';
    } catch (err) {
      error.textContent = err.message;
      button.disabled = false;
      input.disabled = false;
      input.select();
      input.focus();
    }
  });
})();
