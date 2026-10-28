/**
 * Sovereign Offline ServiceWorker (The Immortality Invariant)
 * Caches all WebXR assets locally so the construct runs 100% offline with zero server dependence.
 */
const CACHE_NAME = 'galaxy-sovereign-v1';
const CORE_ASSETS = [
  './',
  './index.html',
  './assets/js/sovereign-actions.js',
  './assets/js/social-share.js',
  './assets/js/stereo-demo.js',
  './assets/js/app.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[SW] Pre-caching sovereign construct assets');
      return cache.addAll(CORE_ASSETS).catch((err) => console.warn('[SW] Cache add warning:', err));
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  // Never intercept API routes (Square payments, vault syncs)
  if (event.request.url.includes('/api/')) {
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (!response || response.status !== 200 || response.type !== 'basic') {
          return response;
        }
        const clone = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        return response;
      }).catch(() => {
        // Offline fallback to root
        return caches.match('./index.html');
      });
    })
  );
});
