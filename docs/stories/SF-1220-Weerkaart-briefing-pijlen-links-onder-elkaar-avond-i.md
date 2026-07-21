# SF-1220 - Weerkaart-briefing: pijlen links onder elkaar, avond i.p.v. middag, weer + getij onderin

## Story

Weerkaart-briefing: pijlen links onder elkaar, avond i.p.v. middag, weer + getij onderin

<!-- refined-by-factory -->

## Scope

Wijzig de Weerkaart-briefingsectie (backend, module `briefing`: `WeatherMapSectionProvider.kt` + `CoastMapImageBuilder.kt`) op vier punten:

1. **Layout windpijlen**: de twee windpijlen (nu horizontaal verspreid via `spacing = width/(n+1)` in `drawOverlay`) komen boven elkaar aan de linkerkant van het kaartbeeld te staan. Elke pijl behoudt zijn windsnelheidslabel (kn), kleur en het weer-icoontje; de kleur/dagdeel-legenda blijft ongewijzigd functioneel.
2. **Dagdelen**: het tweede dagdeel wordt "Avond" (19:00) i.p.v. "Middag" (14:00), analoog aan de dagdeel-indeling in `KiteSectionProvider`/`SlotAssessmentProvider` (Ochtend 07:00 + Avond 19:00). `WeatherMapSectionProvider`'s `DaySlot`-labels/tijden worden hierop aangepast.
3. **Weersaanduiding onderin**: onderaan het kaartbeeld komt een getekende weersaanduiding voor die dag (zonnig/bewolkt/regen), met dezelfde `java.awt`-vormentekenstijl als de bestaande `drawWeatherIcon` (geen emoji/`Font`-glyphs, want die renderen niet op de server).
4. **Getijtijden onderin**: onderaan het kaartbeeld komen ook de hoog- en laagwatertijden van die dag (IJmuiden) te staan, opgehaald via `tides.TideClient.forecast(...)` (`TideForecast.extremes`, gefilterd op `TideType.HOOGWATER`/`LAAGWATER` voor die dag) en als tekst getekend met `java.awt` (geen emoji).
5. **Tekst opschonen**: de getijtekst verdwijnt uit `BeachCycleSectionProvider.tideText` — de regel toont straks alleen nog de nabijheid ("dichtbij laagwater" / "niet dichtbij laagwater"), zonder het `laagwater om HH:MM`-deel, omdat die tijd nu op de weerkaart staat. De onderliggende rating/beoordelingslogica (`assessBeachCycle`, `SlotAssessment`) blijft ongewijzigd. `KiteSectionProvider` toont al geen getij-tekst en hoeft niet te wijzigen.

Geen frontend-wijziging: `summary_screen.dart` rendert de sectie (`imageUrl` + `text`) al generiek.

## Acceptance criteria

- De twee windpijlen op het weerkaart-PNG staan verticaal gestapeld aan de linkerkant van de kaart (niet meer horizontaal over de breedte verspreid), elk met eigen kleur, windsnelheidslabel (kn) en weer-icoon.
- Het tweede dagdeel in `WeatherMapSectionProvider` heet "Avond" en gebruikt 19:00 als tijdstip (i.p.v. "Middag"/14:00); het eerste dagdeel blijft "Ochtend" 07:00.
- Onderin het kaartbeeld staat een met `java.awt`-vormen getekend weersymbool (zon/wolk/regen) voor die dag, in dezelfde stijl als het bestaande per-dagdeel weer-icoon.
- Onderin het kaartbeeld staan de hoog- en laagwatertijden voor die dag (IJmuiden) als met `java.awt` getekende tekst, opgehaald via `TideClient`.
- `BeachCycleSectionProvider`'s getoonde tekst per dagdeel bevat niet langer een laagwatertijd (`laagwater om HH:MM`); de nabijheids-tekst ("dichtbij laagwater"/"niet dichtbij laagwater") blijft staan. De rating (`assessBeachCycle`/kleur) verandert niet.
- Bestaande unit tests voor `CoastMapImageBuilder`/`drawOverlay`/`weatherCategory` en voor `WeatherMapSectionProvider`/`BeachCycleSectionProvider` zijn aangepast waar nodig en slagen (`mvn test` in `robberts-assistent-backend/`).
- `ModulithArchitectureTest` blijft slagen (geen nieuwe ongeoorloofde module-afhankelijkheden).
- Geen wijziging aan de frontend (`summary_screen.dart`) nodig of aangebracht.

## Aannames

- De weersaanduiding en getijtijden onderin de kaart representeren de hele dag (morgen), niet per dagdeel — één weer-icoon en één set hoog-/laagwatertijden onderaan, naast de twee per-dagdeel windpijlen.
- Voor de weersaanduiding onderin wordt het weertype van het dagdeel "Ochtend" (of een vergelijkbare representatieve keuze, bv. de eerstvolgende beschikbare voorspelling) gebruikt als er geen aparte "hele dag"-databron is; exacte keuze is aan de developer, mits consistent met de bestaande `weatherCodeDescription`/`drawWeatherIcon`-logica.
- Alle hoog-/laagwatermomenten van die dag (IJmuiden) worden getoond, niet alleen het eerstvolgende laagwatermoment — analoog aan wat `TideClient.forecast` teruggeeft over de periode van die dag.
- De opslagsleutel (`STORAGE_KEY = "morgen"`) en het endpoint `GET /api/v1/briefing/weather-map/{slot}` blijven ongewijzigd; alleen de inhoud van het gegenereerde PNG verandert.

<!-- test-feedback:start -->
## Test-feedback
Testronde afgerond. Bevindingen:

**Backend-tests groen**: `mvn test` in `robberts-assistent-backend/` → 240 tests, 0 failures, 0 errors, BUILD SUCCESS (incl. `ModulithArchitectureTest`). Gerichte hertest van de story-relevante tests ook groen.

**Preview-verificatie** (pr-20, frontend-proxy `/api/v1/briefing`): `POST /refresh` bevestigt "Ochtend"/"Avond"-labels (AC2) en de opgeschoonde strandfiets-tekst zonder `laagwater om HH:MM` (AC5).

**Bug gevonden**: het gegenereerde weerkaart-PNG (`screenshots/SF-1220-weerkaart.png`, 512×512px) toont de windpijlen correct verticaal gestapeld links (AC1 OK), maar het kader met weer-icoon + getijtijden onderin is breder dan het canvas — de getijtekst is aan zowel de linker- als rechterkant afgesneden en onleesbaar. Oorzaak: `drawDaySummary()` in `CoastMapImageBuilder.kt` berekent `boxWidth` puur op basis van tekstbreedte zonder begrenzing op de kaartbreedte, waardoor `boxX` negatief wordt zodra er meerdere getijmomenten zijn. Dit schendt AC4 ("hoog-/laagwatertijden ... als getekende tekst" — impliciet leesbaar).

Details en repro-stappen staan in `docs/stories/worklog/SF-1220-worklog.md`. Terug naar de developer om het kader/tekst binnen de kaartbreedte te laten passen.

{"agent_tips_update":[{"category":"tester","key":"robberts-assistent-briefing-refresh-endpoint-accessible-in-preview","content":"In preview robberts-assistent-pr-<n> is POST /api/v1/briefing/refresh en GET /api/v1/briefing/weather-map/{slot} bereikbaar zonder expliciete auth-header via de frontend-proxy-route (RA_PREVIEW_SKIP_GOOGLE_AUTH) - handig om na een codewijziging direct het live-gegenereerde PNG te downloaden en te inspecteren i.p.v. alleen unit-tests te vertrouwen bij canvas/tekenwerk."},{"category":"tester","key":"coastmapimagebuilder-boxwidth-not-bounded-sf1220","content":"CoastMapImageBuilder.drawDaySummary() (SF-1220/1221) berekent boxWidth voor het onderste getij-/weer-kader puur uit metrics.stringWidth(tideText) zonder rekening te houden met de 512px kaartbreedte - bij 3+ getijmomenten wordt de tekst aan beide kanten van het kaartbeeld afgesneden. Bestaande unit tests checken alleen pixelkleuren op vaste posities, niet of tekst binnen canvasgrenzen blijft - dit soort overflow-bugs valt alleen op met een echte gerenderde PNG (preview/curl), niet met mvn test alleen."}]}
{"phase":"test-rejected"}
<!-- test-feedback:end -->

## Eindsamenvatting

## Eindsamenvatting SF-1220 — Weerkaart-briefing: pijlen links onder elkaar, avond i.p.v. middag, weer + getij onderin

**Wat is gebouwd**

De weerkaart-sectie van de Morgen-briefing (`briefing.WeatherMapSectionProvider` + `CoastMapImageBuilder`) is op vier punten aangepast:

1. **Windpijlen**: staan nu verticaal gestapeld aan de linkerkant van het kaartbeeld (was: horizontaal verspreid over de breedte). Kleur, windsnelheidslabel (kn) en weer-icoon per dagdeel blijven behouden.
2. **Dagdelen**: tweede dagdeel is nu "Avond" (19:00) i.p.v. "Middag" (14:00); "Ochtend" (07:00) blijft ongewijzigd.
3. **Weersymbool onderin**: onderaan de kaart staat nu een met `java.awt`-vormen getekend dag-weersymbool (zon/wolk/regen), zelfde tekenstijl als de bestaande per-dagdeel iconen — geen emoji/font-glyphs (die renderen niet op de server).
4. **Getijtijden onderin**: hoog- en laagwatertijden voor die dag (IJmuiden, via `TideClient`) staan als getekende tekst onderin de kaart. Een getij-fout laat de sectie niet crashen (stil-falend).
5. **Tekst opgeschoond**: `BeachCycleSectionProvider` toont per dagdeel alleen nog de nabijheids-tekst ("dichtbij/niet dichtbij laagwater") zonder de exacte `laagwater om HH:MM`-tijd, omdat die nu op de kaart staat. De onderliggende beoordelingslogica (`assessBeachCycle`) is ongewijzigd.

**Gevonden en opgelost tijdens het testen**

Bij de eerste testronde bleek het onderste weer-/getij-kader op het gegenereerde PNG breder dan het canvas: bij 3+ getijmomenten liep de tekst aan beide kanten buiten beeld en was onleesbaar. Oorzaak: `boxWidth` werd puur uit de tekstbreedte berekend zonder begrenzing op de kaartbreedte. Fix: de tekst wordt nu greedy over meerdere regels verdeeld (`wrapTideLines`) en `boxWidth` is begrensd op de beschikbare kaartbreedte. Een gerichte hertest (visuele inspectie van het live-gegenereerde PNG in de preview-omgeving) bevestigde dat het kader nu volledig binnen het canvas past en alle tijden leesbaar zijn.

**Getest**

- `mvn test` in `robberts-assistent-backend/`: 241 tests groen, incl. `ModulithArchitectureTest`.
- Gerichte unit tests voor `CoastMapImageBuilder`, `WeatherMapSectionProvider` en `BeachCycleSectionProvider`, incl. een nieuwe test die specifiek de overflow-bug-repro (4 getijmomenten) afdekt.
- Live verificatie in preview-omgeving (`robberts-assistent-pr-20`): briefing-refresh, weerkaart-PNG gedownload en visueel gecontroleerd, strandfiets-tekst gecontroleerd op afwezigheid van de oude tijdsvermelding.
- Code review: geen blokkerende bevindingen; alle call sites van `CoastMapImageBuilder.build(...)` consistent bijgewerkt.

**Bewust niet gedaan**

- Geen frontend-wijziging — `summary_screen.dart` rendert de sectie al generiek via `imageUrl`/`text`, dus geen aanpassing nodig of gedaan.
- `SlotAssessment.nearestLowTideAt` blijft bewust ongebruikt-maar-aanwezig, conform de oorspronkelijke opdracht.
- De opslagsleutel (`"morgen"`) en het endpoint `GET /api/v1/briefing/weather-map/{slot}` zijn ongewijzigd gelaten — alleen de inhoud van het PNG is veranderd.
