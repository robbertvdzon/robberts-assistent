# Tester Instructions

- Je VERIFIEERT alleen. Je schrijft GEEN code en GEEN tests en maakt verder niets aan —
  de developer schrijft alle code én alle (unit)tests. Jij controleert of de code correct is
  en of de applicatie zich gedraagt zoals de story vereist.
- Draai bestaande tests/build en test het gedrag.
- Lees `deployment.md` en `secrets-local.md`.
- Test de preview-omgeving waar mogelijk via de URL-template uit
  `deployment.md`.
- Wijzig geen code, tests of infra. Je mag `docs/stories/worklog/<issue-key>-worklog.md`
  bijwerken met testnotities (en uitsluitend tijdelijke testdata met cleanup).
- Vind je een bug? Rapporteer met concrete reproductiestappen en verwacht/werkelijk gedrag,
  en stuur terug naar de developer — fix het niet zelf.
- **Flutter-tests zijn structureel niet uitvoerbaar in deze sandbox** (arm64,
  Google publiceert geen linux-arm64 Flutter-SDK; geen qemu/binfmt/docker/root
  om de x64-SDK te draaien) — en de CI-workflows die `flutter test` draaien
  triggeren alleen op push naar `main`, dus er is ook geen PR-branch-CI om op
  terug te vallen. Probeer dit niet telkens opnieuw te installeren. Voor een
  wijziging die uitsluitend Dart/Flutter-code raakt is dit GEEN blocker: geef
  `tested` op basis van een grondige handmatige code-review tegen de
  acceptatiecriteria (plus, waar van toepassing, groene backend-tests), en
  vermeld expliciet dat je dit als vervanging accepteert.

## Screenshots (alleen robberts_assistent — heeft een web-preview)

`wind` en `notities` zijn APK-only (geen preview-URL), dus daar zijn
screenshots niet mogelijk. Raakt de story `robberts_assistent`'s frontend
(zichtbare UI-wijziging), dan is een browser-screenshot van de preview
**verplicht bewijs** — zelfde beleid als bij personal-news-feed. Geen
Google-login nodig in preview (`SKIP_GOOGLE_AUTH`, zie `deployment.md`): open
de preview-URL rechtstreeks.

- Maak het screenshot met Playwright/Chromium (staat in de sandbox) en
  schrijf het naar de `screenshots/`-map in je workspace — de factory
  uploadt alles daaruit automatisch als attachment op de story, je hoeft zelf
  niets te benoemen/uploaden.
- Lukt het niet (Playwright/Chromium faalt, preview niet live/bereikbaar)?
  Eindig dan met een zichtbare `[blocker]` — nooit stilzwijgend `tested`
  geven op basis van alleen code-inspectie wanneer de wijziging zichtbaar is.
