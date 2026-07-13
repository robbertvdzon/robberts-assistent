# notities

Flutter-app (APK-only) met één auto-opslaande notitie, gekoppeld aan
`robberts-assistent-backend`. Google-login vereist.

## Gedrag

- Slaat automatisch op: 10 seconden na de laatste toetsaanslag (debounce), en
  meteen bij het naar de achtergrond gaan of afsluiten van de app.
- Checkt bij opstarten (async, niet-blokkerend) of er een nieuwere versie op
  GitHub staat en vraagt een dialoogje om bij te werken zo ja
  (`lib/self_update_prompt.dart`/`lib/update_checker.dart`).

## Build & test

```bash
flutter pub get
flutter test
flutter build apk --release \
  --build-number=<N> \
  --dart-define=API_BASE_URL=https://robberts-assistent.vdzonsoftware.nl \
  --dart-define=GOOGLE_CLIENT_ID=<web-oauth-client-id>
```

CI (`.github/workflows/notities-apk.yml`) bouwt en publiceert de release-APK
naar de vaste GitHub-Release-tag `notities-latest` bij elke push naar `main`.
