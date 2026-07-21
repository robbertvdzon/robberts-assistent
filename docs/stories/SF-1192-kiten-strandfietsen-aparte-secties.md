# SF-1192 - Morgen-briefing: kiten en strandfietsen als aparte secties

## Story

Morgen-briefing: kiten en strandfietsen als aparte secties

Splits de huidige gecombineerde 'Kiten / strandfietsen'-briefingsectie op in twee aparte kaarten op de Morgen-tab.

Achtergrond: nu maakt KiteSectionProvider (robberts-assistent-backend/.../briefing/KiteSectionProvider.kt) één BriefingSection (key='kite', title='Kiten / strandfietsen') die per dagdeel beide activiteiten op één regel propt ('Ochtend: kiten 🔴 12 kn (NW), strandfietsen 🟢'). Dat is rommelig.

Gewenst:
1. Twee losse BriefingSections; Kiten eerst, Strandfietsen daaronder. Beide bovenaan de briefing (boven Agenda) blijven staan. Regel via 'order': kiten laagste order, strandfietsen net erna, dan pas de rest.
2. Kiten-kaart: per dagdeel (Ochtend/Avond op werkdag ma-do, anders Dag) regel '<label>: <emoji> <wind> kn (richting)'.
3. Strandfietsen-kaart: per dagdeel bolletje MET onderbouwing — toon wind (kn + richting), regen (mm of droog/nat) en getij (laagwater-nabijheid/tijd), zodat het groen/geel/rood-oordeel navolgbaar is.

Implementatie: hergebruik bestaande beoordelingslogica (assessKite/assessBeachCycle) en dataproviders (WindForecastClient, WeatherClient, TideClient, CalendarClient). Splits in twee @Component BriefingSectionProviders (bv. gedeelde helper voor de slot-assessment). shortSummary() voor de 18:00-push behouden voor kiten (strandfietsen mag null). Frontend rendert secties generiek (summary_screen.dart) — geen app-wijziging nodig. Werk de bestaande tests bij (KiteSectionProviderTest) en voeg een test voor de strandfietsen-sectie toe.

## Eindsamenvatting

De eindsamenvatting voor SF-1192 is opgesteld en bevat: wat gebouwd is (splitsing kiten/strandfietsen in twee losse briefingsecties), de belangrijkste keuzes (gedeelde `SlotAssessmentProvider` om dubbele netwerkcalls te voorkomen, onafhankelijke foutafhandeling, generieke frontend-rendering), wat getest is (volledige backend- en frontend-testsuites plus reviewer-verificatie), en wat bewust niet is aangepakt.
