# SF-1564 - Statustegels bovenaan de Vandaag-tab (kiten, strandfietsen, afval)

## Story

Statustegels bovenaan de Vandaag-tab (kiten, strandfietsen, afval)

<!-- refined-by-factory -->

## Samenvatting

Bovenaan Vandaag komen maximaal drie compacte tegels voor kiten, strandfietsen en afval.
Elke tegel toont direct de belangrijkste waarde en een herkenbare statuskleur.
Een tik opent de bijbehorende details, zonder dat dezelfde informatie permanent dubbel op het scherm staat.
Als er geen betrouwbare status beschikbaar is, blijft de bestaande sectie gewoon zichtbaar.

## Scope

### Backend

- Breid `BriefingSection` achterwaarts compatibel uit met:
  - `status`: optionele enum met JSON-waarden `GOED`, `LET_OP` en `NIET`;
  - `tileLabel`: optionele korte tekst.
- Beide velden hebben standaardwaarde `null`. Oude gecachete JSON zonder deze velden blijft deserialiseren; bestaande velden, endpoints, sectieteksten en `shortSummary()`-gedrag blijven ongewijzigd.
- `KiteSectionProvider` en `BeachCycleSectionProvider` leiden tegelgegevens af uit hetzelfde `AssessmentResult` waarmee de sectietekst wordt gemaakt, zonder extra calls naar databronnen:
  - kies het gunstigste beschikbare dagdeel (`GOED` boven `LET_OP` boven `NIET`); bij gelijke beoordeling wint het vroegste dagdeel;
  - vertaal `GREEN` naar `GOED`, `YELLOW` naar `LET_OP` en `RED` naar `NIET`;
  - kiten krijgt als `tileLabel` de wind in de vorm `<n> kn <richting>`, bijvoorbeeld `24 kn W`;
  - strandfietsen krijgt als `tileLabel` `goed`, `let op` of `niet`;
  - bij ontbrekende of foutieve voorspellingsdata blijven `status` en `tileLabel` null, zodat de bestaande foutsectie zichtbaar blijft.
- `WasteSectionProvider` gebruikt dezelfde opgehaalde planning voor sectietekst en tegel:
  - een ophaalmoment vandaag of morgen geeft `LET_OP`;
  - een eerstvolgend ophaalmoment over 2 tot en met 6 dagen geeft `GOED`;
  - zonder ophaalmoment binnen zeven dagen wordt dit `GOED` met label `geen`;
  - `tileLabel` bevat een kort herkenbaar baktype van het eerstvolgende moment, zoals `gft`, `pbd`, `papier` of `rest`; meerdere bakken op dezelfde eerstvolgende datum worden kort samengevoegd;
  - bij een fout of exception blijven beide tegelvelden null en blijft de bestaande foutmelding als gewone sectie zichtbaar.
- Alle overige providers laten beide velden null.

### Flutter-app

- Breid het Dart-model en de JSON-parsing achterwaarts compatibel uit met de optionele status en `tileLabel`. Een ontbrekende of onbekende status veroorzaakt geen fout en levert geen tegel op.
- Toon direct onder de bijgewerkt-regel maximaal de eerste drie geldige statussecties, in de bestaande backendvolgorde.
- Gebruik even brede tegels over de beschikbare breedte: één tegel vult de rij, twee delen de rij en drie staan naast elkaar. Lange titels of labels lopen niet buiten de tegel maar worden afgekapt met ellipsis.
- Iedere tegel toont:
  - het sectie-icoon;
  - de sectietitel klein;
  - `tileLabel` prominent;
  - een statusbolletje met exact `#0CA30C` voor `GOED`, `#FAB219` voor `LET_OP` en `#D03B3B` voor `NIET`.
- De tegel heeft toegankelijke semantiek waarin statuswoord, titel en label worden uitgesproken; de betekenis wordt dus niet uitsluitend via kleur overgebracht.
- Herintroduceer de eerdere iconen voor kiten (`Icons.air`) en strandfietsen (`Icons.pedal_bike`), voeg voor afval een passend recycling-icoon toe en gebruik `Icons.info_outline` als generieke fallback.
- Statussecties die als tegel zijn opgenomen, verschijnen niet daarnaast permanent als losse kaart. Een tik op de tegel opent onder de tegelrij één uitklapbaar detailblok met de bestaande volledige sectie-inhoud en brengt dit zo nodig in beeld. Er staat maximaal één tegel-detail tegelijk open.
- Statussecties ná de limiet van drie blijven als gewone sectiekaart zichtbaar, zodat informatie nooit verdwijnt.
- Secties zonder tegelstatus, inclusief foutsecties, blijven exact volgens de bestaande generieke kaartweergave renderen. Bij nul tegels vervallen zowel de tegelrij als de bijbehorende tussenruimte.

## Acceptance criteria

1. De briefing-JSON ondersteunt optioneel `status` en `tileLabel`; bestaande responses en caches zonder deze velden blijven werken.
2. Kiten en strandfietsen kiezen deterministisch het gunstigste dagdeel en leveren de afgesproken status en labels zonder een tweede beoordeling of extra broncalls.
3. Afval levert de afgesproken tegelstatus en het korte label voor vandaag, morgen, later in het zevendagenvenster en een leeg venster; bij fouten wordt geen tegel aangeboden.
4. Providers buiten kiten, strandfietsen en afval krijgen geen tegel en hun bestaande output blijft ongewijzigd.
5. Vandaag toont nul, één, twee of drie tegels zonder lege ruimte of layout-overflow; de rij bevat nooit meer dan drie tegels.
6. De tegels gebruiken exact de drie opgegeven statuskleuren en hebben toegankelijke semantiek met een uitgesproken statuswoord en label.
7. Een tik op een tegel opent de juiste bestaande sectie-inhoud. Een andere tegel openen sluit het vorige detail.
8. Een getegelde sectie staat niet tevens permanent als losse kaart onderaan; niet-getegelde en foutsecties blijven wel als kaart zichtbaar.
9. Backendtests dekken de nieuwe velden en selectiegevallen voor `KiteSectionProvider`, `BeachCycleSectionProvider` en `WasteSectionProvider`, inclusief fout- en leeggedrag.
10. Fluttertests dekken JSON zonder/met status, de tegelrij met 0, 1 en 3 tegels, gelijke breedte zonder overflow, de exacte statuskleuren, het tik-/uitklapgedrag en het ontbreken van permanente dubbele kaarten.
11. Bestaande backend- en Fluttertests blijven behouden en worden waar nodig aangepast; `mvn test`, `flutter analyze` en `flutter test` zijn groen binnen de mogelijkheden van de factory-omgeving.
12. De functionele en technische factory-documentatie wordt bijgewerkt met het uitgebreide briefingcontract en het tegelgedrag.

## Aannames

- Een tegel vat bij kiten en strandfietsen de beste mogelijkheid van morgen samen; alle dagdelen blijven via het detailblok beschikbaar.
- Afval is `LET_OP` wanneer vandaag of morgen actie nodig kan zijn en anders `GOED`; een onbetrouwbare afvalplanning wordt niet als rode tegel gepresenteerd maar als de bestaande foutsectie.
- Alleen de eerste drie statussecties worden tegels. Dit houdt toekomstige extra statusproviders bruikbaar zonder informatie te verbergen.
- Er komen geen nieuwe endpoints, cachelagen, databronnen of wijzigingen aan de dagelijkse push.
- De bestaande lichte themastijl blijft leidend; deze story voegt geen dark mode of nieuw algemeen kaartthema toe.

## Eindsamenvatting SF-1564

Gebouwd:

- Het briefingcontract ondersteunt achterwaarts compatibel `status` en `tileLabel`.
- Kiten, strandfietsen en afval bepalen hun tegelgegevens zonder extra broncalls. Afval gebruikt consequent de Amsterdamse kalenderdatum.
- Vandaag toont maximaal drie even brede statustegels met de afgesproken kleuren, iconen, afkapping en toegankelijke bediening.
- Een tegel opent één volledig detail; getegelde secties worden niet dubbel getoond. Onbekende statussen, ontbrekende gegevens en fouten blijven als gewone sectiekaart zichtbaar.
- Functionele en technische factorydocumentatie is bijgewerkt.

Keuzes en herstel:

- Bij gelijke beoordelingen wint het vroegste dagdeel.
- Onbetrouwbare gegevens leveren bewust geen tegel op.
- Reviewbevindingen rond screenreader-activatie en de UTC/Amsterdam-daggrens zijn opgelost en met regressietests afgedekt.

Validatie:

- 341 backendtests en 61 Fluttertests groen.
- Backend-package, Flutter analyze, formatcontrole en webbuild groen.
- Onafhankelijke gerichte tests en preview-E2E waren groen; frontend en briefing-API antwoordden met HTTP 200 en alle drie tegels werkten zonder overflow of dubbele kaarten.

Bewust niet gedaan:

- Geen APK-build, omdat de factoryomgeving geen Android SDK bevat.
- Merge en productie-deploy volgen in de daarvoor bestemde vervolgstappen.
