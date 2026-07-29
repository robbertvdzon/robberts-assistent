# Robberts assistent

Flutter-app (APK + web) voor de briefings, chat-assistent, reminders en
langdurige zoekopdrachten van `robberts-assistent-backend`. Google-login is
actief op web/productie en wordt op PR-previews overgeslagen via
`SKIP_GOOGLE_AUTH`; zie [`../docs/factory/deployment.md`](../docs/factory/deployment.md).

## Schermen

- **Upcoming** — dagelijkse briefing uit de backend (`GET /api/v1/briefing`): twee losse kaarten
  voor kite-kans (per dagdeel wind + richting) en strandfietskans (per dagdeel een bolletje met
  onderbouwing: wind, regen, getij) voor morgen — sinds SF-1192 gesplitst, was voorheen één
  samengevoegde kaart —, afspraken komende 7 dagen met per afspraak een reminder-status en,
  indien nog geen reminder staat, een één-tap-actie om er één ~1u vooraf aan te maken, een
  AI-weektakensamenvatting en een moestuin-placeholder. Een tik op de dagelijkse 18:00-FCM-push
  opent dit scherm automatisch (`lib/fcm_service.dart`, `FcmService.deepLinkTab`).
- **Health check** — het ruwe systeem-checkrapport uit de onafhankelijke
  Health-check-cache (`GET /api/v1/briefing/health`), met eigen timestamp en
  reload-actie.
- **Assistent** — gesprekkenlijst (titel + laatst bijgewerkt) met een knop voor een nieuw
  gesprek; een gesprek opent het chatscherm en blijft persistent in Firestore, inclusief een
  door de assistent zelf verzonnen titel en verstuurde foto's (camera/galerij). De lijst toont
  eerst de 10 meest recente (niet-gearchiveerde) gesprekken, oudere onder een uitklapbare
  "Ouder"-sectie; swipe-links (`flutter_slidable`) biedt archiveren en verwijderen (met
  bevestiging), een AppBar-toggle toont ook gearchiveerde gesprekken. Chat met de backend's AI
  (Spring AI/OpenAI), met tools voor Robberts notitie, reminders/alarms, agenda, Google Docs en
  windmetingen/-voorspellingen bij IJmuiden (`robberts-assistent-backend/.../assistant/ai/`).
- **Herinneringen** — overzicht en beheer van reminders en alarmen.
- **Zoekopdrachten** — maakt en verwijdert langdurige websitezoekopdrachten en
  toont per opdracht de meest recente leesbare status. Titel, absolute
  HTTP(S)-URL, zoekinstructie, frequentie (`Dagelijks` of `Kantooruren`) en
  pushvoorkeur worden afzonderlijk ingevoerd. De backend valideert dezelfde
  velden opnieuw. Een tik op een watch-push opent deze tab en herlaadt de lijst;
  ook handmatig openen en de reload-knop halen actuele gegevens op.
- **Meer** — toegang tot Koppelingen, Nachtchecks, Updates en Geheugen.
- **Geheugen** (`lib/memory_screen.dart`, via "Meer") — één groot bewerkbaar tekstveld met de
  volledige geheugen-tekst (feiten/voorkeuren over Robbert) die de assistent automatisch
  bijhoudt na elke chat-beurt en als context gebruikt in latere gesprekken; auto-save (zelfde
  patroon als `notities/lib/notes_editor_screen.dart`).
- **Updates** — toont voor alle drie de apps (wind, robberts_assistent, notities)
  de geïnstalleerde vs. laatste GitHub-Release-versie, met een bijwerk-knop per
  app (zie `lib/update_checker.dart`/`lib/updates_screen.dart`).

Bij opstarten checkt de app ook zichzelf (async, niet-blokkerend) en vraagt een
dialoogje om bij te werken als er een nieuwere versie is (`lib/self_update_prompt.dart`).

De bottom-navigation bevat precies zes bestemmingen in bovenstaande volgorde.
De app opent standaard Assistent (index 2); briefing-pushes openen Upcoming
(index 0) en watch-pushes openen Zoekopdrachten (index 4).

## Zoekopdrachten-API

Alle calls sturen het bestaande Bearer-sessietoken mee.

| Methode | Pad | Gedrag |
|---|---|---|
| `GET` | `/api/v1/watches` | Geeft `{"watches":[...]}` terug; actieve opdrachten eerst, daarna op titel. |
| `POST` | `/api/v1/watches` | Maakt een opdracht met `title`, `url`, `instruction`, `frequency` en `notifyOnFound`. |
| `DELETE` | `/api/v1/watches/{id}` | Verwijdert de opdracht en geeft de resterende lijst terug. |

Een watch-response bevat `id`, de vijf invoervelden, `status`,
`statusDescription`, `lastCheckedAt` en `active`. Mogelijke statussen zijn
`NOG_NIET_GECONTROLEERD`, `NIET_GEVONDEN`, `GEVONDEN` en `ONBEKEND`.
Validatiefouten op `POST` geven HTTP 400 met een Nederlandstalig `message`.

## Build & test

```bash
flutter pub get
flutter test
flutter build apk --release \
  --build-number=<N> \
  --dart-define=API_BASE_URL=https://robberts-assistent.vdzonsoftware.nl \
  --dart-define=GOOGLE_CLIENT_ID=<web-oauth-client-id>
```

CI (`.github/workflows/robberts-assistent-apk.yml`) bouwt en publiceert de
release-APK naar de vaste GitHub-Release-tag `robberts-assistent-latest` bij
elke push naar `main`; `frontend-image.yml` bouwt de web-variant.
