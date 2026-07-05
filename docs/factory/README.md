# Factory Docs

Deze repository (`robberts-assistent`) bevat momenteel de **Wind** PoC-app in
`wind/`: een Flutter/Android-app die de keten "Hey Google" → Android App
Actions → eigen app bewijst (spraak + notificatie, geen zichtbaar scherm). Nog
geen backend of echte weerdata — die volgen in latere stories.

Belangrijkste onderdelen: `wind/lib/` (Flutter-scherm + gedeelde teksten),
`wind/android/app/src/main/kotlin/nl/vdzon/wind/` (native trampoline-activities,
TTS, notificaties), `wind/android/app/src/main/res/xml/shortcuts.xml` (App
Actions-capabilities) en `.github/workflows/build-apk.yml` (CI: release-APK →
GitHub Release). Agents lezen bij voorkeur eerst `development.md` en
`technical-spec.md`.

## Index

- `development.md`: lokaal bouwen, testen en ontwikkelconventies.
- `functional-spec.md`: functionele afspraken en gebruikersgedrag.
- `technical-spec.md`: technische keuzes, frameworks en codeconventies.
- `deployment.md`: deploy-flow en machine-leesbare factory-config.
- `secrets-local.md`: lokale secrets en waar die vandaan komen.
- `agents/`: rol-specifieke instructies voor factory-agents.
