/* =========================================================================
   VKXR — interaction layer
   Pointer-tracked glass, restrained tilt, ripple, reveals, video lifecycle.
   ========================================================================= */
(function () {
  'use strict';

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  function clamp(v, min, max) { return Math.min(max, Math.max(min, v)); }

  /* --------------------------- Footer year ------------------------------ */
  var year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());

  /* --------------------------- Nav elevation --------------------------- */
  var nav = document.getElementById('nav');
  var ticking = false;
  function onScroll() {
    if (!nav || ticking) return;
    ticking = true;
    window.requestAnimationFrame(function () {
      nav.setAttribute('data-elevated', window.scrollY > 20 ? 'true' : 'false');
      ticking = false;
    });
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* --------------------------- Mobile menu ----------------------------- */
  var toggle = document.getElementById('nav-toggle');
  var menu = document.getElementById('mobile-menu');
  function setMenu(open) {
    if (!toggle || !menu) return;
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    menu.hidden = !open;
  }
  if (toggle && menu) {
    toggle.addEventListener('click', function () { setMenu(menu.hidden); });
    menu.addEventListener('click', function (e) {
      if (e.target.closest('a')) setMenu(false);
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') setMenu(false);
    });
  }

  /* ------------------ Pointer-tracked glass + tilt ---------------------- */
  var glassButtons = document.querySelectorAll('.glass-btn');
  var tiltTargets = document.querySelectorAll('[data-tilt]');

  if (finePointer && !reduceMotion) {
    document.addEventListener('pointermove', function (e) {
      for (var i = 0; i < glassButtons.length; i++) {
        var el = glassButtons[i];
        var r = el.getBoundingClientRect();
        if (r.width === 0 || e.clientX < r.left - 70 || e.clientX > r.right + 70 ||
            e.clientY < r.top - 70 || e.clientY > r.bottom + 70) continue;
        el.style.setProperty('--mx', (e.clientX - r.left) + 'px');
        el.style.setProperty('--my', (e.clientY - r.top) + 'px');
      }
    }, { passive: true });

    for (var t = 0; t < tiltTargets.length; t++) {
      (function (el) {
        var raf = 0, rect = null;
        el.addEventListener('pointerenter', function () {
          rect = el.getBoundingClientRect();
          el.style.willChange = 'transform';
        });
        el.addEventListener('pointermove', function (e) {
          if (raf) return;
          raf = window.requestAnimationFrame(function () {
            if (!rect) rect = el.getBoundingClientRect();
            var cx = clamp((e.clientX - rect.left) / rect.width - 0.5, -0.5, 0.5);
            var cy = clamp((e.clientY - rect.top) / rect.height - 0.5, -0.5, 0.5);
            el.style.transform =
              'perspective(900px) rotateY(' + (cx * 5).toFixed(2) + 'deg) ' +
              'rotateX(' + (-cy * 5).toFixed(2) + 'deg) translateY(-1px)';
            raf = 0;
          });
        }, { passive: true });
        el.addEventListener('pointerleave', function () {
          el.style.transform = '';
          el.style.willChange = '';
          rect = null;
        });
      })(tiltTargets[t]);
    }
  }

  /* ------------------------------- Ripple ------------------------------- */
  document.querySelectorAll('[data-ripple]').forEach(function (el) {
    el.addEventListener('pointerdown', function (e) {
      if (reduceMotion) return;
      var r = el.getBoundingClientRect();
      var size = Math.max(r.width, r.height) * 2.2;
      var span = document.createElement('span');
      span.className = 'ripple';
      span.style.width = span.style.height = size + 'px';
      span.style.left = (e.clientX - r.left) + 'px';
      span.style.top = (e.clientY - r.top) + 'px';
      el.appendChild(span);
      window.setTimeout(function () { span.remove(); }, 750);
    });
  });

  /* ------------------------------ Reveals ------------------------------- */
  var revealEls = document.querySelectorAll('[data-reveal]');
  if (reduceMotion || !('IntersectionObserver' in window)) {
    revealEls.forEach(function (el) { el.classList.add('is-in'); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        var delay = el.getAttribute('data-reveal-delay');
        if (delay) el.style.setProperty('--reveal-delay', delay + 'ms');
        el.classList.add('is-in');
        io.unobserve(el);
      });
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.1 });
    revealEls.forEach(function (el) { io.observe(el); });
  }

  /* --------------------------- Background video ------------------------- */
  var video = document.getElementById('bg-video');
  if (video) {
    var conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
    var slowNetwork = !!(conn && (conn.saveData || /(^|-)2g|(^|-)3g/.test(conn.effectiveType || '')));

    if (slowNetwork || window.matchMedia('(max-width: 700px)').matches) {
      Array.prototype.slice.call(video.querySelectorAll('source')).forEach(function (s) { s.remove(); });
      video.setAttribute('src', 'https://vkxr.vercel.app/assets/video/nebula-720.mp4');
      video.load();
    }

    if (reduceMotion) {
      video.removeAttribute('autoplay');
      video.pause();
    } else {
      var tryPlay = function () {
        var p = video.play();
        if (p && typeof p.catch === 'function') p.catch(function () { /* poster remains */ });
      };
      tryPlay();
      document.addEventListener('visibilitychange', function () {
        if (document.hidden) video.pause();
        else tryPlay();
      });
    }
  }
})();
