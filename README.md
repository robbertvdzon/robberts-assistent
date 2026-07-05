# robberts-assistent

Repository voor Robberts persoonlijke assistent. De uiteindelijke opzet omvat
meerdere apps en een backend; die worden stap voor stap (per story) opgebouwd.

## Huidige inhoud

- **`wind/`** — PoC-app **"Wind"** (Flutter/Android). Bewijst dat de keten
  **"Hey Google" → Android App Actions → eigen app** werkt met een hands-free
  gevoel: het antwoord wordt uitgesproken (TextToSpeech) én als notificatie
  gepost, zónder zichtbaar scherm. Nog géén backend of echte weerdata.
  Zie [`wind/README.md`](wind/README.md).

## Build & installatie (Wind)

De belangrijkste commando's draaien vanuit `wind/` (Flutter + Android SDK nodig):

```bash
cd wind
flutter pub get
flutter test
flutter build apk --release
```

Bij elke push naar `main` bouwt de workflow
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) een
release-APK en publiceert die als downloadbare GitHub Release, zodat de app
zonder Play Store of lokale build-omgeving te installeren is.

## Documentatie

- `docs/factory/` — repo-context voor de software factory (stack, build/test,
  functionele en technische specificatie, deploy-info, agent-instructies).
- `docs/stories/` — worklogs en handmatige testinstructies per story.

## Buiten scope (latere stories)

Backend, OpenShift, Postgres, echte weerdata, Todo-app, Assistent-app en
Telegram-koppeling volgen in latere stories.
