/*
 * Chat page. Same protocol as the JavaFX chat tab: the client remembers how many
 * lines it already has (its "version") and asks the server only for the lines
 * added after that. If the server answers with a smaller version than ours
 * (the server restarted), the list is rebuilt from scratch.
 */
'use strict';

(async function main() {
  if (!(await initHeader('chat'))) return;

  const list = document.getElementById('chat-lines');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-input');
  const sendButton = document.getElementById('chat-send');
  let version = 0;

  function render(entry) {
    const mine = entry.userName === Session.user;
    const item = el('li', { class: 'chat-line' + (mine ? ' mine' : '') },
      el('div', { class: 'who', text: '[' + entry.time + '] ' + entry.userName + (mine ? ' (you)' : '') }),
      el('div', { class: 'text', text: entry.text }));
    list.append(item);
  }

  function append(delta) {
    if (delta.version < version) {
      list.replaceChildren();
      version = 0;
    }
    if (delta.entries.length === 0) return;
    const placeholder = list.querySelector('.empty');
    if (placeholder) placeholder.remove();
    const stickToBottom = list.scrollTop + list.clientHeight >= list.scrollHeight - 8;
    delta.entries.forEach(render);
    version = delta.version;
    if (stickToBottom) list.scrollTop = list.scrollHeight;
  }

  Poller.register({
    fetch: () => Api.chatSince(version),
    apply: append,
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    input.disabled = true;
    sendButton.disabled = true;
    try {
      await Api.sendChat(text);
      input.value = '';
      Poller.refreshSoon(); // the next poll brings our line back as a delta, same as in JavaFX
    } catch (err) {
      showError(err);
    } finally {
      input.disabled = false;
      sendButton.disabled = false;
      input.focus();
    }
  });

  Poller.start();
  input.focus();
})();
