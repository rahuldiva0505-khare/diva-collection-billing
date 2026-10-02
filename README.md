# Diva Collection Billing v31

Offline-first billing app with local storage and optional cloud sync.

## Files
- index.html — complete app
- manifest.json — installable PWA
- SW.js — offline cache

## Cloud sync
The app stores data locally first. For real automatic mobile↔laptop sync, connect a backend such as Supabase/Firebase. This build includes a Sync Settings area where the backend URL/key can be configured later.

Important: do not put a service-role/private server key in browser code. Use a public/anon key only with proper database security rules.
