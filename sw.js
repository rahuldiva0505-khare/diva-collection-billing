const CACHE = 'diva-billing-v135';
const CORE = ['./','./index.html','./manifest.json','./sw.js','./icon-192.png','./icon-512.png','./diva-logo.svg'];
self.addEventListener('message', event => { if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting(); });

const LAKSHMI_AUDIO_URL = 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Aarti_Puja,_Lakshmi_Laxmi.ogg';
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(async cache => {
    await cache.addAll(CORE).catch(()=>{});
    try {
      const response = await fetch(LAKSHMI_AUDIO_URL, {mode:'no-cors', cache:'no-store'});
      if (response && (response.type === 'opaque' || response.ok)) await cache.put('./lakshmi-aarti.ogg', response);
    } catch (e) {}
  }).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  const req = event.request;
  if (new URL(req.url).pathname.endsWith('/lakshmi-aarti.ogg')) {
    event.respondWith(caches.open(CACHE).then(async cache => {
      const saved = await cache.match('./lakshmi-aarti.ogg');
      if (saved) return saved;
      try {
        const response = await fetch(LAKSHMI_AUDIO_URL, {mode:'no-cors'});
        if (response && (response.type === 'opaque' || response.ok)) await cache.put('./lakshmi-aarti.ogg', response.clone());
        return response;
      } catch (e) {
        return new Response('Lakshmi Aarti audio is not cached yet. Connect to the internet once.', {status:503,headers:{'Content-Type':'text/plain'}});
      }
    }));
    return;
  }
  if (req.mode === 'navigate') {
    event.respondWith(fetch(req,{cache:'no-store'}).then(response => {
      if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put('./index.html',copy)).catch(()=>{});}
      return response;
    }).catch(()=>caches.match('./index.html')));
    return;
  }
  event.respondWith(fetch(req).then(response => {
    if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(req,copy)).catch(()=>{});}
    return response;
  }).catch(()=>caches.match(req).then(cached=>cached||caches.match('./index.html'))));
});