# robberts_assistent

Flutter-app (APK + web) voor de dagelijkse Morgen-briefing en de chat-assistent van
`robberts-assistent-backend`. Google-login (echt op web/productie; op PR-previews
overgeslagen via `SKIP_GOOGLE_AUTH`, zie `docs/factory/deployment.md`).

## Schermen

- **Morgen** — dagelijkse briefing uit de backend (`GET /api/v1/briefing`): twee losse kaarten
  voor kite-kans (per dagdeel wind + richting) en strandfietskans (per dagdeel een bolletje met
  onderbouwing: wind, regen, getij) voor morgen — sinds SF-1192 gesplitst, was voorheen één
  samengevoegde kaart —, afspraken komende 7 dagen met per afspraak een reminder-status en,
  indien nog geen reminder staat, een één-tap-actie om er één ~1u vooraf aan te maken, een
  AI-weektakensamenvatting en een moestuin-placeholder. Een tik op de dagelijkse 18:00-FCM-push
  opent dit scherm automatisch (`lib/fcm_service.dart`, `FcmService.deepLinkTab`).
- **Assistent** — gesprekkenlijst (titel + laatst bijgewerkt) met een knop voor een nieuw
  gesprek; een gesprek opent het chatscherm en blijft persistent in Firestore, inclusief een
  door de assistent zelf verzonnen titel en verstuurde foto's (camera/galerij). De lijst toont
  eerst de 10 meest recente (niet-gearchiveerde) gesprekken, oudere onder een uitklapbare
  "Ouder"-sectie; swipe-links (`flutter_slidable`) biedt archiveren en verwijderen (met
  bevestiging), een AppBar-toggle toont ook gearchiveerde gesprekken. Chat met de backend's AI
  (Spring AI/OpenAI), met tools voor Robberts notitie, reminders/alarms, agenda, Google Docs en
  windmetingen/-voorspellingen bij IJmuiden (`robberts-assistent-backend/.../assistant/ai/`).
- **Geheugen** (`lib/memory_screen.dart`, via "Meer") — één groot bewerkbaar tekstveld met de
  volledige geheugen-tekst (feiten/voorkeuren over Robbert) die de assistent automatisch
  bijhoudt na elke chat-beurt en als context gebruikt in latere gesprekken; auto-save (zelfde
  patroon als `notities/lib/notes_editor_screen.dart`).
- **Updates** — toont voor alle drie de apps (wind, robberts_assistent, notities)
  de geïnstalleerde vs. laatste GitHub-Release-versie, met een bijwerk-knop per
  app (zie `lib/update_checker.dart`/`lib/updates_screen.dart`).

Bij opstarten checkt de app ook zichzelf (async, niet-blokkerend) en vraagt een
dialoogje om bij te werken als er een nieuwere versie is (`lib/self_update_prompt.dart`).

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
