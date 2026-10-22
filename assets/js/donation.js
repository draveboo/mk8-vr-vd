(function () {
  var form = document.getElementById('donation-form');
  var cardContainer = document.getElementById('card-container');
  var status = document.getElementById('donation-status');
  if (!form || !window.Square) return;
  var card;
  var appId = 'sq0idp-y85qNXRclUc_3OHxffOA8Q';
  var locationId = 'LSTWPE3W0HSMP';
  Square.payments(appId, locationId).card().then(function (value) {
    card = value;
    card.attach('#card-container');
  }).catch(function () { status.textContent = 'Card checkout is temporarily unavailable.'; });
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!card) return;
    status.textContent = 'Securely preparing checkout…';
    var token = await card.tokenize();
    if (token.status !== 'OK') { status.textContent = 'Please check your card details and try again.'; return; }
    var cents = Math.round(Number(form.amount.value) * 100);
    var response = await fetch('/api/create-payment', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourceId: token.token, amount: cents }) });
    var result = await response.json();
    status.textContent = response.ok ? 'Thank you — your support helps keep VKXR moving.' : (result.error || 'Payment failed.');
  });
})();
