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
