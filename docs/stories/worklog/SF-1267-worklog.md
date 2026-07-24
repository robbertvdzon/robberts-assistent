# SF-1267 - Worklog

Story-context bij eerste pickup:
Upcoming/Health check tabs implementeren

Backend: voeg optioneel BriefingItem.heading-veld toe; herstructureer SystemStatusSectionProvider.assess() zodat de 5 per-check ruwe statusregels (zonnepanelen, backups, OpenShift, automower, Software Factory) als gestructureerde items (heading + ruwe tekst) in BriefingSection.items terechtkomen, zonder de bestaande AI-samenvatting/parseAiReply()/shortSummary()/18:00-push-logica te wijzigen; breid SystemStatusSectionProviderTest.kt uit met asserts op de nieuwe items. Frontend: voeg heading toe aan het Dart BriefingItem-model; splits de bottom-navigatie in home_screen.dart van 4 naar 5 tabs (Upcoming op de plek van Morgen, nieuwe Health check ernaast, overige tabs ongewijzigd); pas summary_screen.dart aan zodat de Upcoming-tab alle secties behalve system-status toont; bouw een nieuw health_check_screen.dart dat alleen de system-status-sectie toont met per onderdeel een duidelijke kop en de ruwe statusregels in tabel-/bullet-vorm, met volledig selecteerbare tekst (SelectableText); zorg dat de bestaande FCM-briefing-deep-link naar de Upcoming-tab blijft wijzen. Werk summary_screen_test.dart en home_screen_test.dart bij en voeg tests toe voor het nieuwe Health check-scherm.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-1268 — Upcoming/Health check tabs implementeren

Backend (`robberts-assistent-backend`):
- `briefing.BriefingItem` kreeg een optioneel `heading`-veld (nullable, default `null` —
  bestaande secties zonder heading blijven ongewijzigd).
- `SystemStatusSectionProvider.assess()` is herstructureerd: de vijf per-check ruwe
  statusregels (zonnepanelen, backups, OpenShift, robotmaaier, Software Factory) worden nu
  eerst als `CheckData(heading, content)` opgebouwd (`buildChecks()` +
  `solarCheckData()`/`openShiftCheckData()`/`automowerCheckData()`/`softwareFactoryCheckData()`),
  gebruikt voor zowel de ruwe AI-inputtekst (ongewijzigd formaat: `"<heading>: <content>"` per
  regel) als de nieuwe `BriefingSection.items` (`BriefingItem(text = content, heading = heading)`)
  — dus geen wijziging aan `callAi()`/`parseAiReply()`/`shortSummary()`/de 18:00-push-logica, en
  de AI-raw-inputtekst is byte-voor-byte gelijk aan voorheen. `items` blijft gevuld ook als de
  AI-call faalt (fallback-tak van `assess()`), zodat de Health check-pagina onafhankelijk van een
  AI-fout altijd de ruwe data toont.
- `SystemStatusSectionProviderTest.kt` uitgebreid met asserts op de nieuwe `items` (headings,
  ruwe tekst, gevuld blijven bij een falende AI-call, maaier-foutcode in het Robotmaaier-item).

Frontend (`robberts_assistent`):
- `BriefingItem` (Dart, `api_client.dart`) kreeg het `heading`-veld + `fromJson`-parsing.
- `home_screen.dart`: bottom-navigatie van 4 naar 5 tabs — "Upcoming" op de plek van "Morgen"
  (zelfde index 0, zelfde `SummaryScreen`, dus de bestaande FCM-briefing-deep-link
  (`FcmService.deepLinkTab`/`_morgenTabIndex = 0`) blijft ongewijzigd naar deze tab wijzen),
  nieuwe "Health check"-tab op index 1 (nieuw `HealthCheckScreen`), overige tabs (Assistent,
  Herinneringen, Meer) ongewijzigd maar één index opgeschoven; standaard-tab (`_tab`) blijft
  "Assistent", nu index 2 i.p.v. 1.
- `summary_screen.dart`: de sectielijst wordt gefilterd op `section.key != 'system-status'`,
  zodat de Upcoming-tab alle briefingsecties toont behalve systeemstatus.
- Nieuw `lib/health_check_screen.dart`: haalt dezelfde `GET /api/v1/briefing` op (geen nieuw
  backend-endpoint), zoekt de `system-status`-sectie op `key`, en toont per item een kaart met
  de `heading` als kop en de ruwe `text` (gesplitst op `\n`) als bullets — alles via
  `SelectableText` zodat de tekst kopieerbaar is. Toont een neutrale melding als er (nog) geen
  systeemstatus-sectie is, en de bestaande foutafhandelingsstijl van `SummaryScreen` bij een
  mislukte `getBriefing()`.
- Tests bijgewerkt/toegevoegd: `home_screen_test.dart` (5 tabs, tabnamen, gewijzigde
  default-tab-index, Health check niet gebouwd op de starttoestand),
  `summary_screen_test.dart` (systeemstatus-sectie niet zichtbaar op Upcoming), nieuw
  `health_check_screen_test.dart` (koppen + ruwe statusregels + `SelectableText`, lege-staat,
  foutmelding).

Getest:
- `mvn test` (backend, vanuit `robberts-assistent-backend/`): 267 tests, 0 failures/errors.
- `flutter analyze` (vanuit `robberts_assistent/`): geen issues.
- `flutter test` (vanuit `robberts_assistent/`): 33 tests, alles groen.

Niet gedaan / aangepast:
- Geen wijziging aan `docs/` (functionele/technische spec, `CLAUDE.md`) — dat is voor de
  documentation-subtaak (SF-1271).
- Geen nieuw backend-endpoint voor de Health check-pagina; hergebruikt bewust
  `GET /api/v1/briefing` zoals in de story-aannames vastgelegd.
