/*
 * Events screen: filters, the event list and the details pane (same structure as the
 * JavaFX client). Data is polled every second; the list and the pane are redrawn only
 * when the server's version changes, and the input controls are persistent DOM nodes
 * that are re-attached on redraw, so typed values and selections are never lost.
 */
'use strict';

(function () {
  const filters = { method: 'All', status: 'All', commission: 'All' };
  const FILTER_OPTIONS = {
    method: ['All', 'LMSR', 'Order Book'],
    status: ['All', 'Not started', 'Active', 'Closed'],
    commission: ['All', 'on-purchase', 'on-close'],
  };

  let allEvents = [];
  let listVersion = -1;
  let selectedId = -1;
  let detailsVersion = -1;

  // persistent inputs (survive redraws)
  const buyOption = el('select', { id: 'buy-option' });
  const buyShares = el('input', { id: 'buy-shares', class: 'short', type: 'number', min: '1', step: '1', placeholder: 'shares' });
  const orderOption = el('select', { id: 'order-option' });
  const orderSide = el('select', { id: 'order-side' }, el('option', { text: 'Buy' }), el('option', { text: 'Sell' }));
  const orderPrice = el('input', { id: 'order-price', class: 'short', type: 'number', min: '0.01', max: '0.99', step: '0.01', placeholder: 'price e.g. 0.60' });
  const orderQuantity = el('input', { id: 'order-quantity', class: 'short', type: 'number', min: '1', step: '1', placeholder: 'quantity' });
  const winnerSelect = el('select', { id: 'winner-select' });

  // ---------- filters ----------

  function buildFilters() {
    for (const [key, options] of Object.entries(FILTER_OPTIONS)) {
      const host = document.querySelector('[data-filter="' + key + '"]');
      options.forEach((option) => {
        const button = el('button', { class: 'toggle' + (option === filters[key] ? ' on' : ''), text: option });
        button.addEventListener('click', () => {
          filters[key] = option;
          host.querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.textContent === option));
          renderList();
        });
        host.append(button);
      });
    }
  }

  function matches(event) {
    return (filters.method === 'All' || event.method === filters.method)
      && (filters.status === 'All' || event.status === filters.status)
      && (filters.commission === 'All' || event.commissionType === filters.commission);
  }

  // ---------- event list ----------

  function renderList() {
    const list = document.getElementById('event-list');
    list.innerHTML = '';
    const visible = allEvents.filter(matches);
    if (visible.length === 0) {
      list.append(el('li', { class: 'empty', text: allEvents.length === 0
        ? 'No events yet. A JavaFX client has to upload an events file first.' : 'No events match the filters.' }));
    }
    for (const event of visible) {
      const li = el('li', { class: event.id === selectedId ? 'selected' : '' },
        el('div', { class: 'title' }, '#' + event.id + '  ' + event.name),
        el('div', { class: 'sub' }, event.method + '  |  ' + event.status + '  |  MM: ' + event.marketMakerName));
      li.addEventListener('click', () => selectEvent(event.id));
      list.append(li);
    }
    if (selectedId >= 0 && !visible.some((e) => e.id === selectedId)) {
      // the selected event is filtered out: clear the details pane
      selectEvent(-1);
    }
  }

  function selectEvent(id) {
    if (id === selectedId) return;
    selectedId = id;
    detailsVersion = -1;
    document.querySelectorAll('#event-list li').forEach((li) => {
      li.classList.toggle('selected', li.textContent.startsWith('#' + id + ' '));
    });
    if (id < 0) {
      document.getElementById('details').innerHTML = '<div class="card empty">Select an event to see its details.</div>';
      return;
    }
    Api.eventDetails(id).then((details) => render(details, true)).catch(showError);
  }

  // ---------- details pane ----------

  function render(details, force) {
    if (details.meta.id !== selectedId) return;
    if (!force && details.version === detailsVersion) return;
    detailsVersion = details.version;
    const meta = details.meta;
    const isMm = meta.marketMakerName === Session.user;
    const host = document.getElementById('details');
    const scrollY = window.scrollY;
    host.innerHTML = '';

    // header card
    const grid = el('div', { class: 'info-grid' });
    const info = (k, v) => grid.append(el('span', { class: 'k', text: k }), typeof v === 'string' ? el('span', { class: 'v', text: v }) : v);
    info('Trading method:', meta.method);
    info('Status:', statusBadge(meta.status));
    info('Commission:', meta.commissionPercentage + '%  (' + meta.commissionType + ')');
    info('Options:', meta.optionNames.join('  /  '));
    info('Market maker:', meta.marketMakerName + (isMm ? '  (you)' : ''));
    info('Event account:', Fmt.money(meta.accountBalance));
    if (meta.status === 'Closed' && details.winnerName) {
      info('Winner:', details.winnerName);
    }
    host.append(el('div', { class: 'card' },
      el('h2', {}, '#' + meta.id + '   ' + meta.name),
      el('p', { class: 'muted', text: meta.description }),
      grid,
      actionBar(meta, isMm)));

    if (meta.method === 'LMSR') {
      renderLmsr(host, details);
    } else {
      renderOrderBook(host, details);
    }

    // participants
    const table = el('table', {}, el('thead', {}, el('tr', {}, el('th', { text: 'User' }),
      ...meta.optionNames.map((o) => el('th', { class: 'num', text: o + ' shares' })),
      ...meta.optionNames.map((o) => el('th', { class: 'num', text: o + ' value' })))));
    const body = el('tbody');
    for (const p of details.participants) {
      body.append(el('tr', {}, el('td', { text: p.userName + (p.userName === Session.user ? ' (you)' : '') }),
        ...p.shares.map((s) => el('td', { class: 'num', text: Fmt.shares(s) })),
        ...p.values.map((v) => el('td', { class: 'num', text: Fmt.money(v) }))));
    }
    table.append(body);
    host.append(el('div', { class: 'card' }, el('h3', { text: 'Participants' }),
      details.participants.length ? el('div', { class: 'table-wrap' }, table) : el('div', { class: 'empty', text: 'Nobody holds shares in this event yet.' })));

    window.scrollTo(0, scrollY);
  }

  function statusBadge(status) {
    const cls = status === 'Active' ? 'active' : status === 'Closed' ? 'closed' : '';
    return el('span', { class: 'badge ' + cls, text: status });
  }

  function actionBar(meta, isMm) {
    const bar = el('div', { class: 'row', style: 'margin-top:12px' });
    const hint = isMm ? null : el('span', { class: 'muted', text: 'Only the market maker (' + meta.marketMakerName + ') can do this.' });
    if (meta.status === 'Not started') {
      const open = el('button', { id: 'open-button', class: 'primary', text: 'Open Event' });
      open.disabled = !isMm;
      open.addEventListener('click', () => doOpen(meta));
      bar.append(open);
      if (hint) bar.append(hint);
    } else if (meta.status === 'Active') {
      syncOptions(winnerSelect, meta.optionNames);
      winnerSelect.disabled = !isMm;
      const close = el('button', { id: 'close-button', class: 'danger', text: 'Close Event' });
      close.disabled = !isMm;
      close.addEventListener('click', () => doClose(meta));
      bar.append(el('label', { text: 'Winning option:' }), winnerSelect, close);
      if (hint) bar.append(hint);
    } else {
      bar.append(el('span', { class: 'muted', text: 'This event is closed. No more trading.' }));
    }
    return bar;
  }

  function syncOptions(select, names) {
    const current = select.value;
    if (select.options.length !== names.length || [...select.options].some((o, i) => o.textContent !== names[i])) {
      select.innerHTML = '';
      names.forEach((name, i) => select.append(el('option', { value: String(i + 1), text: name })));
    }
    if (current && [...select.options].some((o) => o.value === current)) {
      select.value = current;
    }
  }

  // ---------- LMSR ----------

  function renderLmsr(host, details) {
    const meta = details.meta;
    const table = el('table', {}, el('thead', {}, el('tr', {},
      el('th', { text: 'Option' }), el('th', { class: 'num', text: 'Price (probability)' }), el('th', { class: 'num', text: 'Shares bought' }))));
    const body = el('tbody');
    for (const o of details.lmsrOptions) {
      body.append(el('tr', {}, el('td', { text: o.optionName }), el('td', { class: 'num', text: Fmt.percent(o.price) }),
        el('td', { class: 'num', text: Fmt.shares(o.totalShares) })));
    }
    table.append(body);
    const card = el('div', { class: 'card' }, el('h3', { text: 'Options and prices' }), el('div', { class: 'table-wrap' }, table));

    if (meta.status === 'Active') {
      syncOptions(buyOption, meta.optionNames);
      const buy = el('button', { id: 'buy-button', class: 'primary', text: 'Buy' });
      buy.addEventListener('click', () => doBuy(meta));
      buyShares.onkeydown = (e) => { if (e.key === 'Enter') doBuy(meta); };
      card.append(el('div', { class: 'row', style: 'margin-top:10px' }, el('label', { text: 'Buy:' }), buyOption, buyShares, buy));
    }

    // price chart
    const canvas = el('canvas', { class: 'chart' });
    card.append(el('h3', { text: 'Price history', style: 'margin-top:12px' }), canvas);
    host.append(card);
    requestAnimationFrame(() => drawLineChart(canvas, details.priceHistory || [], { min: 0, max: 1, labels: meta.optionNames }));

    // trade history
    const trades = el('table', {}, el('thead', {}, el('tr', {}, el('th', { text: 'User' }), el('th', { text: 'Option' }),
      el('th', { class: 'num', text: 'Shares' }), el('th', { class: 'num', text: 'Cost' }), el('th', { class: 'num', text: 'Commission' }))));
    const tradesBody = el('tbody');
    for (const t of details.lmsrTradeHistory) { // the server already lists the newest trade first
      tradesBody.append(el('tr', {}, el('td', { text: t.userName }), el('td', { text: t.optionName }),
        el('td', { class: 'num', text: Fmt.shares(t.shares) }), el('td', { class: 'num', text: Fmt.money(t.cost) }),
        el('td', { class: 'num', text: Fmt.money(t.commissionPaid) })));
    }
    trades.append(tradesBody);
    host.append(el('div', { class: 'card' }, el('h3', { text: 'Trade history (newest first)' }),
      details.lmsrTradeHistory.length ? el('div', { class: 'table-wrap' }, trades) : el('div', { class: 'empty', text: 'No trades yet.' })));
  }

  // ---------- order book ----------

  function renderOrderBook(host, details) {
    const meta = details.meta;
    if (meta.status === 'Active') {
      syncOptions(orderOption, meta.optionNames);
      const submit = el('button', { id: 'order-button', class: 'primary', text: 'Submit Order' });
      submit.addEventListener('click', () => doOrder(meta));
      orderQuantity.onkeydown = (e) => { if (e.key === 'Enter') doOrder(meta); };
      host.append(el('div', { class: 'card' }, el('h3', { text: 'Submit an order' }),
        el('div', { class: 'row' }, el('label', { text: 'Option:' }), orderOption, el('label', { text: 'Side:' }), orderSide,
          el('label', { text: 'Price:' }), orderPrice, el('label', { text: 'Qty:' }), orderQuantity, submit)));
    }

    const books = el('div', { class: 'split', style: 'grid-template-columns: 1fr 1fr' });
    details.books.forEach((book, i) => {
      const stats = el('div', { class: 'info-grid', style: 'margin-bottom:8px' });
      const stat = (k, v) => stats.append(el('span', { class: 'k', text: k }), el('span', { class: 'v', text: Fmt.price(v) }));
      stat('Last:', book.last); stat('Bid:', book.bid); stat('Ask:', book.ask); stat('Mid:', book.mid); stat('Spread:', book.spread);
      books.append(el('div', {}, el('h3', { text: 'Option ' + (i + 1) + ': ' + book.optionName }), stats,
        el('div', { class: 'row', style: 'align-items:start' },
          ordersTable('Buy orders (bids)', book.buyOrders), ordersTable('Sell orders (asks)', book.sellOrders))));
    });
    host.append(el('div', { class: 'card' }, el('h3', { text: 'Order books' }), books));
  }

  function ordersTable(title, orders) {
    const table = el('table', {}, el('thead', {}, el('tr', {}, el('th', { text: 'User' }), el('th', { class: 'num', text: 'Price' }), el('th', { class: 'num', text: 'Qty' }))));
    const body = el('tbody');
    for (const o of orders) {
      body.append(el('tr', {}, el('td', { text: o.userName }), el('td', { class: 'num', text: Fmt.price(o.price) }), el('td', { class: 'num', text: Fmt.shares(o.quantity) })));
    }
    if (orders.length === 0) {
      body.append(el('tr', {}, el('td', { class: 'muted', text: 'none', colspan: '3' })));
    }
    table.append(body);
    return el('div', { style: 'flex:1; min-width:200px' }, el('div', { class: 'muted', text: title }), el('div', { class: 'table-wrap' }, table));
  }

  // ---------- actions ----------

  function doOpen(meta) {
    Api.openEvent(meta.id).then((r) => {
      toast("Event '" + meta.name + "' is now active.\nYou paid " + Fmt.money(r.amountPaid) + ' (balance: ' + Fmt.money(r.marketMakerBalance) + ').', 'ok');
      Poller.refreshSoon();
    }).catch(showError);
  }

  function doClose(meta) {
    const winner = parseInt(winnerSelect.value || '1', 10);
    const winnerName = meta.optionNames[winner - 1];
    if (!window.confirm("Close '" + meta.name + "' with winning option '" + winnerName + "'?")) return;
    Api.closeEvent(meta.id, winner).then((r) => {
      toast('Winner: ' + r.winnerName + '\nPaid to winners: ' + Fmt.money(r.totalPaidToWinners)
        + '\nCommission collected: ' + Fmt.money(r.commissionCollected)
        + '\nReturned to market maker: ' + Fmt.money(r.leftoverToMarketMaker), 'ok');
      Poller.refreshSoon();
    }).catch(showError);
  }

  function doBuy(meta) {
    const shares = parseInt(buyShares.value, 10);
    if (!Number.isInteger(shares) || shares <= 0) {
      toast('Please enter a whole positive number of shares.', 'error');
      return;
    }
    Api.buyShares(meta.id, buyOption.value || 1, shares).then((r) => {
      let message = "Bought " + Fmt.shares(r.shares) + " shares of '" + r.optionName + "'.\nCost: " + Fmt.money(r.cost)
        + '  |  Commission: ' + Fmt.money(r.commissionPaid) + '\nYour balance: ' + Fmt.money(r.userBalance);
      if (r.message) message += '\n\n' + r.message;
      toast(message, r.userBlocked ? 'error' : 'ok');
      buyShares.value = '';
      Poller.refreshSoon();
    }).catch(showError);
  }

  function doOrder(meta) {
    const price = parseFloat(orderPrice.value);
    const quantity = parseInt(orderQuantity.value, 10);
    if (!(price > 0 && price < 1)) {
      toast('Please enter a price between 0 and 1 (exclusive), e.g. 0.60.', 'error');
      return;
    }
    if (!Number.isInteger(quantity) || quantity <= 0) {
      toast('Please enter a whole positive quantity.', 'error');
      return;
    }
    Api.placeOrder(meta.id, orderOption.value || 1, orderSide.value, price, quantity).then((r) => {
      let message;
      if (r.fills.length === 0) {
        message = 'No immediate match. The order is resting in the book.\n';
      } else {
        message = 'Fills:\n' + r.fills.map((f) => '  * ' + (f.mint ? 'MINT ' : '') + Fmt.shares(f.quantity) + ' @ ' + Fmt.money(f.price)
          + '  ' + f.optionName + '  (buyer: ' + f.buyerName + ', seller: ' + f.sellerName + ')').join('\n') + '\n';
      }
      if (r.restingQuantity > 0) message += 'Resting quantity: ' + Fmt.shares(r.restingQuantity) + '\n';
      message += 'Your balance: ' + Fmt.money(r.userBalance);
      if (r.message) message += '\n\n' + r.message;
      toast(message, r.userBlocked ? 'error' : 'ok');
      orderQuantity.value = '';
      Poller.refreshSoon();
    }).catch(showError);
  }

  // ---------- start ----------

  initHeader('events').then((ok) => {
    if (!ok) return;
    buildFilters();
    Poller.register({
      fetch: () => Api.events(),
      apply: (list) => {
        if (list.version === listVersion) return;
        listVersion = list.version;
        allEvents = list.events;
        renderList();
      },
    });
    Poller.register({
      isActive: () => selectedId >= 0,
      fetch: () => Api.eventDetails(selectedId),
      apply: (details) => render(details, false),
    });
    Poller.start();
  });
})();
