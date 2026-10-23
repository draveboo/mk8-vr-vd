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
  play.addEventListener('click', togglePlayback);
  radio.querySelector('[data-radio-prev]').addEventListener('click', function () { setTrack(index - 1, !video.paused); });
  radio.querySelector('[data-radio-next]').addEventListener('click', function () { setTrack(index + 1, !video.paused); });
  video.addEventListener('play', syncPlayback); video.addEventListener('pause', syncPlayback);
  video.addEventListener('ended', function () { setTrack(index + 1, true); });
  setTrack(0, false);
})();
