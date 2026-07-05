---
default_base_branch: main
branch_prefix: ai/
preview_url_template: "https://example-pr-{pr_num}.example.com"
preview_namespace_template: "example-pr-{pr_num}"
preview_db_secret_recipe: |
  echo "Vul hier optioneel het commando in om de preview database secret op te halen."
---

# Deployment

De Wind-app is een PoC zonder backend of server-omgeving; er is (nog) geen
preview-deploy of hosting. "Deployen" betekent hier het beschikbaar stellen van
een installeerbare Android-APK.

## Distributie via GitHub Release

De workflow `.github/workflows/build-apk.yml` bouwt bij elke push naar `main`
(en handmatig via `workflow_dispatch`) een release-APK en publiceert die als
GitHub Release (`softprops/action-gh-release`):

- Release-tag/-naam: `wind-build-<run_number>`.
- Asset: `app-release.apk` (debug-signing volstaat voor de PoC).
- Installeren: download `app-release.apk` van de Release en sta op het
  Android-toestel "installeren uit onbekende bron" toe. Geen Play Store of
  lokale build-omgeving nodig.

## Handmatige verificatie

De App Actions-flow ("Hey Google, vraag Wind ...") en de notificatie op een
Garmin-horloge zijn alleen op echte hardware te controleren. De stappen staan in
`docs/stories/SF-802-manual-tests.md` en in de PR-beschrijving.

## Latere stories

Backend, OpenShift, Postgres en preview-omgevingen komen in latere stories; de
`preview_*`-velden in de frontmatter hierboven zijn nog placeholders.
