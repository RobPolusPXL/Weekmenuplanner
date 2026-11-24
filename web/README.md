# Maaltijdplanner – webapp (PWA)

Dezelfde app als de Android-versie, maar in de browser: weekplanning, gerechten, boodschappen per afdeling,
voorraad en statistieken. Gebruikt **dezelfde Firestore-database en documentvormen** als de Android-app,
dus beide apps tonen dezelfde gegevens.

Geen buildstap: statische bestanden, Firebase via de CDN (zoals de reisapp).

## Lokaal bekijken

```bash
cd web
python3 -m http.server 8080
```

- `http://localhost:8080/?demo` – demo met voorbeeldgegevens, zonder login (niets gaat naar Firestore).
- `http://localhost:8080/` – echte app, werkt zodra `js/firebase-config.js` ingevuld is.

Tests van de logica (parser, afdelingen, voorraad, weekstart, statistieken, recept-import):

```bash
node --test web/tests/*.test.mjs
```

## Koppelen aan Firebase

1. Firebase console → Project settings → Your apps → **Add app → Web (</>)**. Kopieer de config naar `js/firebase-config.js`.
2. Authentication → Settings → **Authorized domains**: voeg het adres toe waar de app draait (bv. `robpoluspxl.github.io`). `localhost` staat er standaard in.
3. De Firestore-regels zijn dezelfde als voor de Android-app (`firestore.rules` in de hoofdmap); er hoeft niets bij.

## Opbouw

```
index.html, manifest.webmanifest, sw.js   PWA
css/app.css                               stijl (zelfde palet als de Android-app)
js/main.js                                opstart, tabbladen, tekenen
js/store.js                               toestand en acties (zoals de ViewModel)
js/data/repo.js                           lezen/schrijven, zelfde documenten als MaaltijdRepository
js/data/db-firebase.js / db-memory.js     Firestore of demo in het geheugen
js/logic/                                 parser, planner, afdelingen, voorraad, statistieken, recept
js/views/                                 week, gerechten, boodschappen, voorraad, statistieken, account
tests/                                    node-tests van de logica
```

## Verschillen met de Android-app

- **Recept importeren** lukt alleen bij sites die het toelaten; de meeste receptsites blokkeren dit in de browser (CORS). Dan plak je de ingrediënten zelf.
- **Bijna-op-melding**: in de app (rode kaart en badge), nog geen pushmelding op de telefoon.
