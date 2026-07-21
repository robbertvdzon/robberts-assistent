# SF-1221 - Worklog

Story-context bij pickup: vervolg op SF-1220 (zelfde scope-omschrijving, parent-story
`SF-1220`). De tester vond in SF-1220 één bug (zie `docs/stories/worklog/SF-1220-worklog.md`,
sectie "Testronde"): het onderste weer-/getij-kader in `CoastMapImageBuilder.drawDaySummary()`
werd breder dan de kaart, waardoor de getijtekst bij 3+ getijmomenten aan beide kanten werd
afgesneden. De rest van de scope (verticaal gestapelde pijlen links, Ochtend/Avond 07:00-19:00,
`BeachCycleSectionProvider.tideText()` zonder `laagwater om HH:MM`) was al correct geïmplementeerd
en getest in SF-1220 — deze subtaak richt zich op het oplossen van de gevonden bug.

Stappenplan:
[x]: read issue and target docs (incl. SF-1220-worklog voor de bug-context)
[x]: fix drawDaySummary()-kader-overflow in CoastMapImageBuilder.kt
[x]: add/update unit test die de fix afdekt
[x]: run mvn test (incl. ModulithArchitectureTest)
[x]: update story-log with results

Done / rationale:
- `CoastMapImageBuilder.drawDaySummary()`: `boxWidth` werd voorheen puur uit de tekstbreedte
  berekend zonder rekening te houden met de kaartbreedte, waardoor `boxX` negatief kon worden
  zodra er 3+ getijmomenten waren. Fix: de tekst wordt nu greedy over meerdere regels verdeeld
  (nieuwe `wrapTideLines()`) zodra hij niet binnen de beschikbare breedte
  (`width - 2 * margin - icoonbreedte - padding`) past, en `boxWidth` wordt begrensd op
  `maxBoxWidth` (`width - 2 * margin`). `boxHeight` past zich aan het aantal regels aan; het
  weer-icoon en de tekstblok blijven verticaal gecentreerd in het kader.
- Geen wijziging nodig aan `WeatherMapSectionProvider.kt` of `BeachCycleSectionProvider.kt` — die
  waren in SF-1220 al conform de acceptatiecriteria (Ochtend/Avond 07:00-19:00,
  `tomorrowTideExtremes()` met stil-falende `TideClient`-fout, en de opgeschoonde
  strandfiets-tekst zonder `laagwater om HH:MM`).
- Nieuwe test in `CoastMapImageBuilderTest`: rendert het kaartbeeld met vier getijmomenten (2
  hoogwater + 2 laagwater, zoals in de bug-repro) en controleert via pixel-scans dat de
  kader-kleur (`Color(255,255,255,220)`) niet op de linker- of rechterrand van het canvas
  voorkomt, maar wel ergens onderin het midden — dit zou zonder de fix falen (het kader liep dan
  tot buiten beide randen).
- `mvn test` in `robberts-assistent-backend/`: 241 tests, 0 failures, 0 errors, BUILD SUCCESS
  (incl. `ModulithArchitectureTest`).
- Geen frontend-wijziging (`summary_screen.dart` rendert de sectie al generiek via `imageUrl`).

---

## Reviewronde (reviewer, SF-1221)

- Volledige diff t.o.v. `main` bekeken (niet alleen deze subtaak): `BeachCycleSectionProvider.kt`,
  `CoastMapImageBuilder.kt`, `WeatherMapSectionProvider.kt` + bijbehorende tests.
- Bug uit de test-feedback (kader/tekst buiten het canvas bij 3+ getijmomenten) is correct
  opgelost: `boxWidth` is nu begrensd op `width - 2*margin`, tekst wordt greedy over regels
  verdeeld (`wrapTideLines`) i.p.v. het kader oneindig te verbreden. Nieuwe test dekt exact de
  bug-repro (4 getijmomenten) via pixel-scans op de canvasranden.
- Overige AC's (verticaal gestapelde pijlen links, Ochtend 07:00/Avond 19:00,
  `BeachCycleSectionProvider.tideText()` zonder `laagwater om HH:MM`, stil-falende
  `TideClient`-fout) waren al in SF-1220 correct geïmplementeerd en blijven ongewijzigd/correct.
  `SlotAssessment.nearestLowTideAt` blijft bewust ongebruikt-maar-aanwezig, conform opdracht.
- Alle call sites van `CoastMapImageBuilder.build(...)` (incl. `StubCoastMapImageBuilder`) zijn
  consistent bijgewerkt naar de nieuwe signatuur; geen restanten van de oude 1-parameter-vorm.
- Zelf gericht `mvn -o test` gedraaid op `CoastMapImageBuilderTest`, `WeatherMapSectionProviderTest`,
  `BeachCycleSectionProviderTest` en `ModulithArchitectureTest` (via volledige testrun, surefire
  filtert niet doorgezet): 241 tests, 0 failures, 0 errors, BUILD SUCCESS — bevestigt het
  ontwikkelaarsbewijs.
- Geen frontend-wijziging aangetroffen/nodig, conform scope.
- Geen blokkerende bevindingen.

{"phase":"reviewed"}
