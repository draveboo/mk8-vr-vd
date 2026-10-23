(function () {
  'use strict';
  var radio = document.querySelector('[data-radio]');
  if (!radio) return;

  var tracks = [
    { title: 'Midnight at the Glass Horizon', src: 'assets/music/midnight-at-the-glass-horizon.mp4' },
    { title: 'Copper Sky Horizon', src: 'assets/music/copper-sky-horizon.mp4' },
    { title: 'Mesa Architect', src: 'assets/music/mesa-architect.mp4' },
    { title: 'Empire of the Sands', src: 'assets/music/empire-of-the-sands.mp4' }
  ];
  var video = radio.querySelector('[data-radio-video]');
  var title = radio.querySelector('[data-radio-title]');
  var toggle = radio.querySelector('[data-radio-toggle]');
  var panel = radio.querySelector('#desert-radio-panel');
  var play = radio.querySelector('[data-radio-play]');
  var progress = radio.querySelector('[data-radio-progress]');
  var index = 0;

  function setTrack(next, shouldPlay) {
    index = (next + tracks.length) % tracks.length;
    var track = tracks[index];
    video.src = track.src;
    video.load();
    title.textContent = track.title;
    play.setAttribute('aria-label', 'Play ' + track.title);
    radio.querySelectorAll('[data-radio-track]').forEach(function (button, buttonIndex) {
      button.classList.toggle('is-active', buttonIndex === index);
      button.setAttribute('aria-current', buttonIndex === index ? 'true' : 'false');
    });
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
  toggle.addEventListener('click', function () {
    var open = radio.getAttribute('data-open') !== 'true';
    radio.setAttribute('data-open', String(open)); panel.hidden = !open; toggle.setAttribute('aria-expanded', String(open));
  });
  play.addEventListener('click', togglePlayback);
  radio.querySelector('[data-radio-prev]').addEventListener('click', function () { setTrack(index - 1, !video.paused); });
  radio.querySelector('[data-radio-next]').addEventListener('click', function () { setTrack(index + 1, !video.paused); });
  radio.querySelectorAll('[data-radio-track]').forEach(function (button) {
    button.addEventListener('click', function () { setTrack(Number(button.dataset.radioTrack), true); });
  });
  video.addEventListener('play', syncPlayback); video.addEventListener('pause', syncPlayback);
  video.addEventListener('timeupdate', function () { if (video.duration) progress.value = String((video.currentTime / video.duration) * 100); });
  video.addEventListener('ended', function () { setTrack(index + 1, true); });
  progress.addEventListener('input', function () { if (video.duration) video.currentTime = video.duration * (Number(progress.value) / 100); });
  setTrack(0, false);
})();
