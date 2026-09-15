/* 课表 PWA Service Worker：应用外壳缓存 + 课表离线缓存 */
const CACHE_VERSION = 'kcb-v1';
const APP_SHELL = [
  '/',
  '/static/manifest.json',
  '/static/icon-192.png',
  '/static/icon-512.png',
  '/static/icon-maskable-512.png',
  '/static/apple-touch-icon.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE_VERSION)
      .then((c) => c.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  /* API：网络优先，失败回退缓存（离线也能看课表） */
  if (url.pathname.startsWith('/api/')) {
    e.respondWith(
      fetch(req).then((resp) => {
        if (resp.ok) {
          const clone = resp.clone();
          caches.open(CACHE_VERSION).then((c) => c.put(req, clone));
        }
        return resp;
      }).catch(() =>
        caches.match(req).then((cached) =>
          cached || new Response(
            JSON.stringify({ ok: false, msg: '离线状态，且没有缓存数据' }),
            { headers: { 'Content-Type': 'application/json' } }
          )
        )
      )
    );
    return;
  }

  /* 页面导航：网络优先，失败回退首页缓存 */
  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req).then((resp) => {
        const clone = resp.clone();
        caches.open(CACHE_VERSION).then((c) => c.put('/', clone));
        return resp;
      }).catch(() => caches.match('/'))
    );
    return;
  }

  /* 静态资源：缓存优先 */
  e.respondWith(
    caches.match(req).then((cached) =>
      cached || fetch(req).then((resp) => {
        if (resp.ok) {
          const clone = resp.clone();
          caches.open(CACHE_VERSION).then((c) => c.put(req, clone));
        }
        return resp;
      })
    )
  );
});
