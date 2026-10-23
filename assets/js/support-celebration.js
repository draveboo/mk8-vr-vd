(function () {
  'use strict';
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var busy = false;
  var audioContext;
  var style = document.createElement('style');
  style.textContent = '@keyframes support-confetti-fall{to{transform:translate3d(var(--drift),105vh,0) rotate(var(--spin));opacity:0}}.support-confetti{position:fixed;z-index:9999;top:-14px;left:var(--left);width:var(--size);height:calc(var(--size)*1.45);border-radius:2px;background:var(--color);pointer-events:none;box-shadow:0 0 9px color-mix(in srgb,var(--color),transparent 45%);animation:support-confetti-fall var(--duration) cubic-bezier(.18,.62,.32,1) forwards}';
  document.head.appendChild(style);

  function getAudio() {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return null;
    if (!audioContext) audioContext = new Ctx();
    if (audioContext.state === 'suspended') audioContext.resume();
    return audioContext;
  }
  function tone(ctx, frequency, endFrequency, start, duration, type, volume) {
    var osc = ctx.createOscillator(), gain = ctx.createGain();
    osc.type = type; osc.frequency.setValueAtTime(frequency, start); osc.frequency.exponentialRampToValueAtTime(Math.max(1, endFrequency), start + duration);
    gain.gain.setValueAtTime(.0001, start); gain.gain.exponentialRampToValueAtTime(volume, start + .008); gain.gain.exponentialRampToValueAtTime(.0001, start + duration);
    osc.connect(gain); gain.connect(ctx.destination); osc.start(start); osc.stop(start + duration + .02);
  }
  function noise(ctx, start, duration, volume, frequency) {
    var length = Math.ceil(ctx.sampleRate * duration), buffer = ctx.createBuffer(1, length, ctx.sampleRate), data = buffer.getChannelData(0);
    for (var i = 0; i < length; i++) data[i] = Math.random() * 2 - 1;
    var source = ctx.createBufferSource(), filter = ctx.createBiquadFilter(), gain = ctx.createGain();
    source.buffer = buffer; filter.type = 'bandpass'; filter.frequency.value = frequency; filter.Q.value = .8;
    gain.gain.setValueAtTime(.0001, start); gain.gain.linearRampToValueAtTime(volume, start + .006); gain.gain.exponentialRampToValueAtTime(.0001, start + duration);
    source.connect(filter); filter.connect(gain); gain.connect(ctx.destination); source.start(start);
  }
  function celebrate(premium) {
    var ctx = getAudio();
    if (ctx) {
      var now = ctx.currentTime;
      tone(ctx, 720, 430, now, .09, 'sine', .12); // soft button click
      noise(ctx, now + .08, premium ? .48 : .3, premium ? .065 : .045, 850); // airy whoosh
      tone(ctx, 440, 790, now + .1, premium ? .82 : .48, 'triangle', premium ? .105 : .075); // rising whoo
      if (premium) {
        tone(ctx, 554, 988, now + .13, .76, 'sine', .055);
        tone(ctx, 660, 1180, now + .17, .66, 'triangle', .038);
        tone(ctx, 150, 72, now + .04, .28, 'sine', .12);
      }
      var claps = premium ? 38 : 18;
      for (var i = 0; i < claps; i++) noise(ctx, now + .28 + Math.random() * (premium ? 1.05 : .62), .045 + Math.random() * .045, (premium ? .045 : .035) + Math.random() * .025, 1150 + Math.random() * 900); // applause-like claps
      tone(ctx, 1040, 1280, now + .2, .22, 'sine', .035);
    }
    if (reduced) return;
    var colors = ['#ffe78c', '#ffd044', '#fff8da', '#ff8d63', '#9ee7ff'];
    var count = premium ? 76 : 44;
    for (var i = 0; i < count; i++) {
      var bit = document.createElement('i');
      bit.className = 'support-confetti';
      bit.style.setProperty('--left', Math.random() * 100 + 'vw');
      bit.style.setProperty('--size', (5 + Math.random() * 6) + 'px');
      bit.style.setProperty('--color', colors[i % colors.length]);
      bit.style.setProperty('--drift', (Math.random() * 170 - 85) + 'px');
      bit.style.setProperty('--spin', (Math.random() * 900 - 450) + 'deg');
      bit.style.setProperty('--duration', (1.05 + Math.random() * .7) + 's');
      document.body.appendChild(bit);
      window.setTimeout(function (node) { node.remove(); }.bind(null, bit), 1900);
    }
  }
  document.addEventListener('click', function (event) {
    var link = event.target.closest('a[href*="/api/donate"]');
    if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || busy) return;
    event.preventDefault(); busy = true;
    var destination = link.href;
    var premium = new URL(destination).searchParams.get('amount') === '25';
    celebrate(premium);
    window.setTimeout(function () { window.location.assign(destination); }, reduced ? 180 : (premium ? 1550 : 1050));
  });
})();
