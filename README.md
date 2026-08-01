# robberts-assistent

Monorepo voor Robberts persoonlijke assistent: één Kotlin/Spring Boot-backend en
vier Flutter/Android-apps.

## Huidige inhoud

- **`robberts-assistent-backend/`** — Spring Modulith-backend voor authenticatie,
  assistentgesprekken, reminders, briefings, langdurige websitezoekopdrachten en
  de overige koppelingen. Externe opslag en diensten hebben waar mogelijk een
  stub- of in-memory fallback.
- **`robberts_assistent/`** — hoofdapp als Android-APK en web-app. De vier
  hoofdbestemmingen zijn Vandaag, Assistent, Taken en Meer; onder Meer staan onder
  meer Health check en Zoekopdrachten. Vandaag vat kiten, strandfietsen en afval
  samen in maximaal drie interactieve statustegels. Via Zoekopdrachten kan de
  gebruiker een website periodiek laten beoordelen en optioneel een push ontvangen
  zodra het gezochte is gevonden; een knop bovenin laat alle lopende zoekopdrachten
  meteen controleren. Een start via Google Assistent/Gemini opent meteen een nieuw
  gesprek in praatmodus; elke app-start wordt daarnaast als één regel in de backend
  gelogd (`grep APP_LAUNCH`) zodat de herkenning later scherper gezet kan worden.
- **`groentetuin/`** — moestuin-chat met tekst en foto's, als APK en web-app.
- **`notities/`** — auto-opslaande notitie-app, als APK.
- **`wind/`** — handsfree Wind-app: Android App Actions starten een
  backendvraag, waarna het antwoord wordt uitgesproken en als notificatie wordt
  getoond. Zie [`wind/README.md`](wind/README.md).

## Bouwen en testen

Backend, vanuit `robberts-assistent-backend/`:

```bash
mvn test
mvn -DskipTests package
```

Flutter-app, vanuit de betreffende app-map:

```bash
flutter pub get
flutter test
flutter analyze
flutter build apk --release
```

Zie [`docs/factory/development.md`](docs/factory/development.md) voor de
volledige lokale werkwijze en bekende omgevingsbeperkingen.

## Documentatie

- [`CLAUDE.md`](CLAUDE.md) — volledig functioneel en technisch repo-overzicht.
- `docs/factory/` — repo-context voor de software factory (stack, build/test,
  functionele en technische specificatie, deploy-info, agent-instructies).
- `docs/stories/` — worklogs en handmatige testinstructies per story.
