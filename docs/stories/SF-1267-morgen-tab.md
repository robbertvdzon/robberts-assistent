# SF-1267 - morgen tab

## Story

morgen tab

<!-- refined-by-factory -->

## Scope

De huidige "Morgen"-tab in `robberts_assistent` wordt gesplitst in twee tabs op de bottom-navigatie:

1. **"Upcoming"** (vervangt de huidige "Morgen"-tab, zelfde plek/icoon in de navigatie): toont exact dezelfde inhoud als de huidige Morgen-briefing (weerkaart, kite/strandfiets, agenda, AI-weektakensamenvatting, moestuin-placeholder), **behalve** de systeemstatus-sectie. Reload-knop, "Bijgewerkt om ..."-regel en pull-to-refresh blijven ongewijzigd werken op dezelfde `GET /api/v1/briefing` / `POST /api/v1/briefing/refresh`-endpoints.
2. **"Health check"** (nieuwe tab): toont uitsluitend de systeemstatus, met per onderdeel (zonnepanelen, backups, OpenShift, robotmaaier, Software Factory) een duidelijke kop, en daaronder de laatste ruwe status van dat onderdeel in tabel-/bullet-vorm — dus de eigen (Nederlandstalige) statusregels die de backend per check al berekent (bv. "huidig vermogen=... W, gisteren opgewekt=... kWh"), niet de huidige AI-samengevatte alinea. Alle tekst op deze pagina moet selecteerbaar zijn (bv. `SelectableText`) zodat Robbert statusregels kan kopiëren.

Backend: `briefing.SystemStatusSectionProvider` levert naast (of in plaats van) de bestaande AI-tekst ook de ruwe per-check gegevens gestructureerd aan de frontend (per check: naam/kop + de bestaande ruwe statusregel(s), zoals nu al intern berekend in `buildSolarText()`/`buildOpenShiftText()`/`buildAutomowerText()`/`buildSoftwareFactoryText()`/de backups-placeholder). De bestaande AI-"aandacht nodig"-beoordeling (`parseAiReply`/`shortSummary()`), gebruikt voor de dagelijkse 18:00-pushtekst, blijft functioneel ongewijzigd — dit is puur een uitbreiding van wat aan de frontend wordt blootgelegd voor de nieuwe Health check-pagina, geen wijziging aan het pushgedrag.

De 4-tabs bottom-navigatie (Morgen/Assistent/Herinneringen/Meer) wordt een 5-tabs navigatie (Upcoming/Health check/Assistent/Herinneringen/Meer); overige tabs blijven ongewijzigd.

## Acceptance criteria

- De bottom-navigatie in `robberts_assistent` toont een tab **"Upcoming"** op de plek waar nu "Morgen" staat; deze tab toont alle briefingsecties van `GET /api/v1/briefing` behalve de systeemstatus-sectie (`key == "system-status"`).
- Er is een nieuwe tab **"Health check"** in de bottom-navigatie die alleen de systeemstatus toont.
- Op de Health check-pagina heeft elk onderdeel (zonnepanelen, backups, OpenShift, robotmaaier, Software Factory) een eigen, duidelijk zichtbare kop.
- Onder elke kop staat de laatste ruwe status van dat onderdeel, gepresenteerd in tabel- of bullet-vorm, in dezelfde vorm/inhoud als de backend die al berekent (geen AI-parafrasering van deze gegevens).
- Alle tekst op de Health check-pagina is selecteerbaar/kopieerbaar door de gebruiker (bv. via `SelectableText`).
- De bestaande dagelijkse 18:00-pushtekst (gebaseerd op `SystemStatusSectionProvider.shortSummary()`) en de AI-"aandacht nodig"-beoordeling blijven functioneel ongewijzigd.
- De deep-link vanuit de bestaande 18:00-briefingpush (`FcmService.deepLinkTab`) opent nog steeds de "Upcoming"-tab (het niet-systeemstatus-deel van de briefing), niet de Health check-tab.
- Bestaande tests (`SystemStatusSectionProviderTest`, `summary_screen_test.dart` en overige briefing-tests) zijn aangepast/aangevuld zodat ze slagen met de nieuwe structuur.

## Aannames

- "Upcoming" is de letterlijke, Engelstalige tabnaam zoals in de omschrijving gevraagd (in afwijking van de verder Nederlandstalige UI-conventie uit `CLAUDE.md`); "Health check" idem, zoals letterlijk genoemd in de story ("heath check" is aangenomen als typefout voor "health check").
- De AI-gebaseerde "aandacht nodig"-classificatie (voor de 18:00-push) blijft bestaan en wordt niet vervangen door een rule-based check op de ruwe data; alleen de weergave op de nieuwe Health check-pagina toont voortaan de ruwe data i.p.v. de AI-samenvatting.
- "Tabel en bullit vorm" betekent: per onderdeel de bestaande ruwe key/value-statusregels leesbaar gepresenteerd (rijen/bullets), geen nieuwe visualisatie zoals grafieken; de developer kiest de concrete Flutter-weergave (Table-widget, DataTable, of eenvoudige bullet-`Column`) passend bij het bestaande app-design.
- De Health check-tab hergebruikt dezelfde databron (`GET /api/v1/briefing`, evt. `POST /api/v1/briefing/refresh`) als de Upcoming-tab, gefilterd op de systeemstatus-sectie — er komt geen apart nieuw backend-endpoint voor uitsluitend de Health check-pagina.
- Selecteerbare tekst geldt voor de Health check-pagina; overige tabs (Upcoming, Assistent, etc.) blijven ongewijzigd (niet-selecteerbare `Text`-widgets), tenzij de developer besluit dit consistent door te voeren.

## Eindsamenvatting

{"agent_tips_update":[]}
{"phase":"summarized"}
