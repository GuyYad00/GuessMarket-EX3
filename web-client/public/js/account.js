/*
 * Account screen (the JavaFX "Account" tab without the file upload):
 * all users in the system, my balance + load funds, my participations with a
 * drill-down, the account ledger and the balance-over-time chart.
 */
'use strict';

(function () {
  let usersVersion = -1;
  let detailsVersion = -1;
  let selectedParticipation = -1;
  let lastDetails = null;

  // ---------- users table ----------

  function renderUsers(list) {
    if (list.version === usersVersion) return;
    usersVersion = list.version;
    const body = document.querySelector('#users-table tbody');
    body.innerHTML = '';
    for (const u of list.users) {
      body.append(el('tr', {},
        el('td', { text: u.name + (u.name === Session.user ? ' (you)' : '') }),
        el('td', { class: 'num ' + (u.balance < 0 ? 'negative' : ''), text: Fmt.money(u.balance) }),
        el('td', { text: Fmt.yesNo(u.marketMakerEventIds.length > 0) }),
        el('td', { text: Fmt.ids(u.marketMakerEventIds) }),
        el('td', { text: Fmt.yesNo(u.connected) }),
        el('td', { text: Fmt.yesNo(u.blocked) })));
    }
    if (list.users.length === 0) {
      body.append(el('tr', {}, el('td', { class: 'empty', colspan: '6', text: 'No users yet.' })));
    }
  }

  // ---------- my account ----------

  function renderDetails(details, force) {
    if (!force && details.version === detailsVersion) return;
    detailsVersion = details.version;
    lastDetails = details;

    document.getElementById('my-name').textContent = details.name;
    const balance = document.getElementById('my-balance');
    balance.textContent = Fmt.money(details.balance);
    balance.className = details.balance < 0 ? 'negative' : 'money';
    document.getElementById('my-loaded').textContent = Fmt.money(details.totalLoaded);
    document.getElementById('my-blocked').textContent = details.blocked ? 'Yes (negative balance: no actions are allowed)' : 'No';
    document.getElementById('my-mm').textContent = Fmt.ids(details.marketMakerEventIds);
    document.getElementById('my-participations').textContent = String(details.participations.length);
    document.getElementById('funds-button').disabled = details.blocked;

    // participations
    const body = document.querySelector('#participations-table tbody');
    body.innerHTML = '';
    for (const p of details.participations) {
      const row = el('tr', { class: 'clickable' + (p.eventId === selectedParticipation ? ' selected' : '') },
        el('td', { text: '#' + p.eventId + '  ' + p.eventName }),
        el('td', { text: p.method }),
        el('td', { text: p.status }),
        el('td', { text: p.marketMaker ? 'Market maker' : 'Trader' }),
        el('td', { text: involvement(p) }),
        el('td', { class: 'num ' + (p.profitLoss < 0 ? 'negative' : ''), text: p.profitLoss === null || p.profitLoss === undefined ? '-' : Fmt.signedMoney(p.profitLoss) }));
      row.addEventListener('click', () => {
        selectedParticipation = p.eventId;
        body.querySelectorAll('tr').forEach((r) => r.classList.toggle('selected', r === row));
        renderParticipationDetails(p);
      });
      body.append(row);
    }
    if (details.participations.length === 0) {
      body.append(el('tr', {}, el('td', { class: 'empty', colspan: '6', text: 'You have not participated in any event yet.' })));
    }
    const selected = details.participations.find((p) => p.eventId === selectedParticipation);
    renderParticipationDetails(selected || null);

    // ledger (newest first)
    const ledger = document.querySelector('#ledger-table tbody');
    ledger.innerHTML = '';
    for (const t of [...details.transactions].reverse()) {
      ledger.append(el('tr', {},
        el('td', { text: String(t.number) }),
        el('td', { text: t.type }),
        el('td', { text: t.description, style: 'white-space: normal' }),
        el('td', { class: 'num ' + (t.amount < 0 ? 'negative' : 'money'), text: Fmt.signedMoney(t.amount) }),
        el('td', { class: 'num ' + (t.balanceAfter < 0 ? 'negative' : ''), text: Fmt.money(t.balanceAfter) })));
    }
    if (details.transactions.length === 0) {
      ledger.append(el('tr', {}, el('td', { class: 'empty', colspan: '5', text: 'No activity yet. Load some money to start.' })));
    }

    drawLineChart(document.getElementById('balance-chart'), [details.balanceHistory || []], { format: (v) => Fmt.money(v) });
  }

  function involvement(p) {
    if (p.method === 'LMSR') {
      return (p.lmsrTrades ? p.lmsrTrades.length : 0) + ' purchase(s)';
    }
    return p.optionNames.map((name, i) => name + '=' + Fmt.shares(p.holdings[i] || 0)).join(', ');
  }

  function renderParticipationDetails(p) {
    const host = document.getElementById('participation-details');
    host.innerHTML = '';
    if (!p) return;
    const grid = el('div', { class: 'info-grid', style: 'margin-top:10px' });
    const info = (k, v) => grid.append(el('span', { class: 'k', text: k }), el('span', { class: 'v', text: v }));
    info('Event:', '#' + p.eventId + '  ' + p.eventName);
    info('Role:', p.marketMaker ? 'Market maker' : 'Trader');
    info('Status:', p.status + (p.winnerName ? '  (winner: ' + p.winnerName + ')' : ''));
    p.optionNames.forEach((name, i) => {
      info(name + ':', Fmt.shares(p.holdings[i] || 0) + ' shares, paid ' + Fmt.money(p.paidPerOption[i] || 0));
    });
    if (p.profitLoss !== null && p.profitLoss !== undefined) {
      info('Profit / loss:', Fmt.signedMoney(p.profitLoss));
    }
    host.append(grid);

    if (p.method === 'LMSR' && p.lmsrTrades && p.lmsrTrades.length) {
      const table = el('table', {}, el('thead', {}, el('tr', {}, el('th', { text: 'Option' }), el('th', { class: 'num', text: 'Shares' }),
        el('th', { class: 'num', text: 'Cost' }), el('th', { class: 'num', text: 'Commission' }))));
      const body = el('tbody');
      for (const t of p.lmsrTrades) {
        body.append(el('tr', {}, el('td', { text: t.optionName }), el('td', { class: 'num', text: Fmt.shares(t.shares) }),
          el('td', { class: 'num', text: Fmt.money(t.cost) }), el('td', { class: 'num', text: Fmt.money(t.commissionPaid) })));
      }
      table.append(body);
      host.append(el('div', { class: 'muted', style: 'margin-top:8px', text: 'Your purchases in this event:' }), el('div', { class: 'table-wrap' }, table));
    }
  }

  // ---------- load funds ----------

  document.getElementById('funds-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const input = document.getElementById('funds-amount');
    const amount = parseFloat(input.value);
    if (!(amount > 0)) {
      toast('Please enter a positive amount to load.', 'error');
      return;
    }
    Api.loadFunds(amount).then((r) => {
      toast(Fmt.money(r.amountLoaded) + ' were added to your account.\nNew balance: ' + Fmt.money(r.newBalance), 'ok');
      input.value = '';
      Poller.refreshSoon();
    }).catch(showError);
  });

  window.addEventListener('resize', () => { if (lastDetails) drawLineChart(document.getElementById('balance-chart'), [lastDetails.balanceHistory || []], { format: (v) => Fmt.money(v) }); });

  // ---------- start ----------

  initHeader('account').then((ok) => {
    if (!ok) return;
    Poller.register({ fetch: () => Api.users(), apply: renderUsers });
    Poller.register({ fetch: () => Api.myDetails(), apply: (d) => renderDetails(d, false) });
    Poller.start();
  });
})();
