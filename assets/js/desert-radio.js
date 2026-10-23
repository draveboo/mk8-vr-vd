(function () {
  'use strict';
  var radio = document.querySelector('[data-radio]');
  if (!radio) return;

  var tracks = [
    { title: 'Midnight at the Glass Horizon', src: 'assets/music/midnight-at-the-glass-horizon.mp4' },
    { title: 'Copper Sky Horizon', src: 'assets/music/copper-sky-horizon.mp4' },
    { title: 'Mesa Architect', src: 'assets/music/mesa-architect.mp4' },
    { title: 'Empire of the Sands', src: 'assets/music/empire-of-the-sands.mp4' },
    { title: 'Midnight in the Canyon', src: 'assets/music/midnight-in-the-canyon.mp4' }
  ];
  var video = radio.querySelector('[data-radio-video]');
  var expand = radio.querySelector('[data-radio-expand]');
  var play = radio.querySelector('[data-radio-play]');
  var index = 0;

  function setTrack(next, shouldPlay) {
    index = (next + tracks.length) % tracks.length;
    var track = tracks[index];
    video.src = track.src;
    video.load();
    video.setAttribute('aria-label', track.title + ' visual');
    play.setAttribute('aria-label', 'Play ' + track.title);
    if (shouldPlay) {
      video.addEventListener('canplay', function onReady() {
        video.removeEventListener('canplay', onReady);
        var promise = video.play();
        if (promise) promise.catch(function () { /* Playback still requires a user gesture in some browsers. */ });
      });
    }
  }
  function togglePlayback() {
    if (video.paused) {
      var promise = video.play();
      if (promise) promise.catch(function () { /* Keep the player usable if a browser blocks playback. */ });
    } else video.pause();
  }
  function syncPlayback() {
    var playing = !video.paused;
    play.textContent = playing ? '❚❚' : '▶';
    play.setAttribute('aria-label', (playing ? 'Pause ' : 'Play ') + tracks[index].title);
    radio.classList.toggle('is-playing', playing);
  }
  expand.addEventListener('click', function () {
    var open = !radio.classList.contains('is-open');
    radio.classList.toggle('is-open', open);
    expand.setAttribute('aria-expanded', String(open));
    if (open) {
      radio.classList.add('is-bloop');
      window.setTimeout(function () { radio.classList.remove('is-bloop'); }, 260);
    }
  });
  play.addEventListener('click', togglePlayback);
  radio.querySelector('[data-radio-prev]').addEventListener('click', function () { setTrack(index - 1, !video.paused); });
  radio.querySelector('[data-radio-next]').addEventListener('click', function () { setTrack(index + 1, !video.paused); });
  video.addEventListener('play', syncPlayback); video.addEventListener('pause', syncPlayback);
  video.addEventListener('ended', function () { setTrack(index + 1, true); });
  setTrack(0, false);

  /* A user gesture is required for audible media on modern browsers. The small
     headphone control provides that consent, plays the 4K end-card once, then
     passes the soundscape to the default Desert Radio track. */
  var soundButton = document.querySelector('[data-audio-unlock]');
  var atmosphere = document.getElementById('site-atmosphere');
  if (soundButton && atmosphere) {
    soundButton.addEventListener('click', function () {
      soundButton.disabled = true;
      soundButton.setAttribute('aria-label', 'Desert Systems intro playing');
      atmosphere.loop = false;
      atmosphere.muted = false;
      atmosphere.volume = 0.42;
      var startIntro = function () {
        atmosphere.currentTime = 0;
        var intro = atmosphere.play();
        if (intro) intro.catch(function () { soundButton.disabled = false; });
      };
      if (atmosphere.dataset.hdSrc && !/desert-systems-endcard-4k\.mp4$/.test(atmosphere.currentSrc || atmosphere.src)) {
        atmosphere.addEventListener('canplay', startIntro, { once: true });
        atmosphere.src = atmosphere.dataset.hdSrc;
        atmosphere.load();
      } else startIntro();
      atmosphere.addEventListener('ended', function startDefaultTrack() {
        atmosphere.muted = true;
        atmosphere.loop = true;
        atmosphere.play().catch(function () { /* visual loop remains optional */ });
        setTrack(0, true);
        soundButton.classList.add('is-complete');
        soundButton.setAttribute('aria-label', 'Music enabled');
      }, { once: true });
    }, { once: true });
  }
})();
