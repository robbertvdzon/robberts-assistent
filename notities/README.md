# notities

Flutter-app (APK-only) met één auto-opslaande notitie, gekoppeld aan
`robberts-assistent-backend`. Google-login vereist.

## Gedrag

- Achtergrondkleur van de app (login-scherm en notitie-editor) is geel
  (`scaffoldBackgroundColor: Colors.yellow` in `lib/main.dart`, bovenop het
  bestaande Material 3-thema met `colorSchemeSeed: Colors.amber`). AppBar,
  kaarten en knoppen behouden hun eigen thema-kleur voor voldoende contrast.
- Slaat automatisch op: 10 seconden na de laatste toetsaanslag (debounce), en
  meteen bij het naar de achtergrond gaan of afsluiten van de app.
- Heeft daarnaast een "Opslaan"-knop in de `AppBar` van de editor
  (naast de statustekst en de uitlog-knop) om direct handmatig op te slaan,
  zonder op de debounce te wachten. De knop annuleert een eventueel lopende
  debounce-timer, is tijdens het opslaan kort uitgeschakeld (laadindicator)
  om dubbele requests te voorkomen, en toont dezelfde statusindicator
  ("Opgeslagen" / "Opslaan mislukt: ...") als de auto-save.
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
