// Service worker: de app opent ook zonder verbinding en toont updates meteen als er verbinding is.
// Code: netwerk eerst, offline uit de cache. Lettertypes en iconen: cache eerst.
// De gegevens zelf cachet Firestore (offline persistence), niet deze service worker.
const V = 'maaltijdplanner-v12';
const CORE = [
  './', 'index.html', 'css/app.css', 'manifest.webmanifest',
  'js/main.js', 'js/ui.js', 'js/store.js', 'js/firebase-config.js',
  'js/data/db-firebase.js', 'js/data/db-memory.js', 'js/data/demo-seed.js', 'js/data/repo.js',
  'js/logic/dates.js', 'js/logic/units.js', 'js/logic/parser.js', 'js/logic/planner.js',
  'js/logic/departments.js', 'js/logic/pantry.js', 'js/logic/stats.js', 'js/logic/recipe.js', 'js/logic/cooking.js',
  'js/views/common.js', 'js/views/week.js', 'js/views/dishes.js', 'js/views/grocery.js',
  'js/views/pantry.js', 'js/views/stats.js', 'js/views/account.js', 'js/views/cook.js', 'js/views/more.js', 'js/theme.js',
  'fonts/Figtree.woff2',
  'icons/icon-192.png', 'icons/icon-512.png', 'icons/favicon-64.png',
];

self.addEventListener('install', e => {
  e.waitUntil((async () => {
    const c = await caches.open(V);
    await Promise.allSettled(CORE.map(u => c.add(new Request(u, { cache: 'reload' }))));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(ks => Promise.all(ks.filter(k => k !== V).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

const isAsset = p => /\/(fonts|icons)\//.test(p);

self.addEventListener('fetch', e => {
  const r = e.request;
  const u = new URL(r.url);
  // Firebase SDK (gstatic) mag gecachet worden zodat de app offline opstart; Firestore-verkeer niet.
  const isSdk = u.hostname === 'www.gstatic.com' && u.pathname.startsWith('/firebasejs/');
  if (r.method !== 'GET' || (u.origin !== location.origin && !isSdk)) return;

  if (isAsset(u.pathname) || isSdk) {
    e.respondWith(caches.open(V).then(async cache => {
      const hit = await cache.match(r);
      if (hit) return hit;
      const res = await fetch(r);
      if (res.ok) cache.put(r, res.clone());
      return res;
    }));
    return;
  }

  e.respondWith((async () => {
    const cache = await caches.open(V);
    try {
      const res = await fetch(r, { cache: 'no-cache' });
      if (res.ok) cache.put(r, res.clone());
      return res;
    } catch {
      return (await cache.match(r, { ignoreSearch: r.mode === 'navigate' }))
        || (r.mode === 'navigate' ? cache.match('index.html') : Response.error());
    }
  })());
});
