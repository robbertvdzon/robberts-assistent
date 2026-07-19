# robberts_assistent

Flutter-app (APK + web) voor de dagelijkse samenvatting en de chat-assistent van
`robberts-assistent-backend`. Google-login (echt op web/productie; op PR-previews
overgeslagen via `SKIP_GOOGLE_AUTH`, zie `docs/factory/deployment.md`).

## Schermen

- **Samenvatting** — dagelijks overzicht uit de backend.
- **Assistent** — gesprekkenlijst (titel + laatst bijgewerkt) met een knop voor een nieuw
  gesprek; een gesprek opent het chatscherm en blijft persistent in Firestore, inclusief een
  door de assistent zelf verzonnen titel en verstuurde foto's (camera/galerij). Chat met de
  backend's AI (Spring AI/OpenAI), met tools voor Robberts notitie, reminders/alarms, agenda,
  Google Docs en windmetingen/-voorspellingen bij IJmuiden
  (`robberts-assistent-backend/.../assistant/ai/`).
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
