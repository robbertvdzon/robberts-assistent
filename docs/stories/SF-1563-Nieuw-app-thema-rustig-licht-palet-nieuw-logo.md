# SF-1563 - Nieuw app-thema: rustig licht palet, nieuw logo, 4 tabs

## Story

Nieuw app-thema: rustig licht palet, nieuw logo, 4 tabs

<!-- refined-by-factory -->

## Samenvatting

De app van Robbert krijgt een rustiger uiterlijk: een licht, zacht kleurenpalet in plaats van
het huidige paars, kaarten zonder schaduw en met een dun randje, en een nieuw logo.
Kleur alleen zegt straks niets meer: overal waar nu een gekleurd bolletje staat, komt er een
woord bij ("goed", "let op", "kritiek"), zodat de status ook zonder kleur te lezen is.

Onderin gaat de navigatie van zes naar vier knoppen: Vandaag, Assistent, Taken en Meer.
De schermen die daardoor uit de balk verdwijnen (Health check, Zoekopdrachten, Koppelingen,
Nachtchecks, Updates, Geheugen) blijven gewoon bestaan en zijn voortaan via "Meer" te openen.

Er verandert niets aan wat de app kan of aan de backend — puur opmaak en indeling.

## Scope

Alleen de Flutter-app `robberts_assistent/`. Geen backend-wijzigingen, geen wijziging aan
API-contracten, endpoints of gedrag van bestaande schermen.

**1. Kleurthema (centraal in `lib/main.dart`)**

- Vervang `ThemeData(colorSchemeSeed: Colors.deepPurple)` door een expliciet, licht
  `ColorScheme` (light):
  - achtergrond/scaffold `#F6F7F8`, kaart/oppervlak `#FFFFFF`, tekst `#171A1D`,
    secundaire tekst `#6B7480`, lijnen/randen `#E7EAED`, primary/accent `#0F6E6E`.
- Kaartstijl centraal in het thema (`CardTheme`): 16px radius, 1px rand in `#E7EAED`,
  `elevation: 0` (geen slagschaduw), witte vulling.
- Sectiekop-stijl: klein hoofdletterlabel (uppercase, kleine letterhoogte, letterspacing) in
  de secundaire tekstkleur `#6B7480`; toegepast op de sectiekoppen van Vandaag en Health check.
- Verwijder decoratief hardgecodeerd kleurgebruik uit de schermen ten gunste van het thema:
  o.a. `Colors.deepPurple` in `main.dart` (login-icoon), `schedules_screen.dart`,
  `assistant_screen.dart`, `watches_screen.dart`, en `Colors.black54`-secundaire tekst →
  de secundaire tekstkleur uit het thema.
- De drie statuskleuren (`#0CA30C` goed, `#FAB219` let op, `#D03B3B` kritiek) worden
  uitsluitend voor status gebruikt, nooit decoratief.

**2. Statuslabels als pillen (nieuwe gedeelde widget, bv. `lib/status_pill.dart`)**

- Eén herbruikbare pil-widget met vier varianten (achtergrond/tekst):
  goed `#E9F6E9`/`#0A6D0A`, let-op `#FDF3DF`/`#8A5C05`, kritiek `#FBEAEA`/`#A52C2C`,
  neutraal `#EEF1F4`/`#4B545E`. Altijd kleur **én** woord.
- Toepassen op de plekken waar status nu alleen met kleur wordt getoond:
  - `couplings_screen.dart` (groen/oranje/grijs bolletjes en test-ok/-fout),
  - `nightly_checks_screen.dart` (groen/rode bolletjes per check en per run),
  - `watches_screen.dart` (statuskleur per zoekopdracht),
  - `summary_screen.dart`: de 🟢/🟡/🔴-emoji's die de backend in de sectietekst zet
    (kite-/strandfietsregel) worden client-side herkend en als pil met woord gerenderd
    ("goed" / "let op" / "kritiek"); de rest van de regeltekst blijft ongewijzigd.

**3. Logo**

- Nieuw SVG-asset (bv. `assets/icon/logo.svg`) met exact de opgegeven SVG-inhoud
  (teal `#0F6E6E` afgeronde vierkant + witte robotkop).
- Gebruik het logo op 30px in de app-header en op het loginscherm (vervangt
  `Icon(Icons.assistant, color: Colors.deepPurple)`).
- Het app-/launcher-icoon (`assets/icon/icon.png` → `flutter_launcher_icons`, Android + web)
  wordt vervangen door hetzelfde beeldmerk.

**4. Header-opzet**

- App-header: logo (30px) + kleine tekst "Robbert's assistent" op één regel; de bestaande
  uitlog-knop blijft rechts beschikbaar.
- Daaronder, per scherm: de paginatitel groot (bv. "Vandaag"), met daaronder de bestaande
  "Bijgewerkt om HH:mm"-regel en rechts de bestaande reload-knop. Geldt voor de schermen die
  al een `updatedAt` + reload hebben (Vandaag/`summary_screen.dart` en
  Health check/`health_check_screen.dart`); overige schermen tonen alleen de paginatitel.
- De bestaande reload-/pull-to-refresh-logica (`getBriefing`/`refreshBriefing`,
  `getHealthCheck`/`refreshHealthCheck`, spinner-tijdens-laden) blijft functioneel ongewijzigd.

**5. Navigatie (`lib/home_screen.dart`)**

- Van 6 naar 4 tabs: **Vandaag** (`SummaryScreen`, index 0), **Assistent**
  (`ConversationsScreen`), **Taken** (`SchedulesScreen`), **Meer** (`MoreScreen`).
  Assistent blijft het standaard-starttabblad.
- `MoreScreen` krijgt de overige schermen als lijstitems: Health check, Zoekopdrachten,
  Koppelingen, Nachtchecks, Geheugen, Updates — elk als gewone `Navigator.push` naar het
  bestaande, ongewijzigde scherm.
- Push-deeplinks blijven werken: `data.type == 'briefing'` → tab Vandaag (index 0);
  `data.type == 'watch'` → opent het Zoekopdrachten-scherm (dat geen tab meer is) als
  gepushte route, met verse data. `FcmService.deepLinkTab` mag daarvoor van vorm veranderen
  (bv. een doel-enum i.p.v. een kale tab-index); de bestaande `reloadTrigger`-constructie voor
  `WatchesScreen` mag vervallen zolang het scherm bij openen opnieuw laadt.

## Acceptance criteria

1. `lib/main.dart` gebruikt een expliciet `ColorScheme` met exact de opgegeven hexwaarden;
   nergens in `lib/` staat nog `Colors.deepPurple` of een ander paars seed-thema.
2. Kaarten tonen 16px radius, 1px rand `#E7EAED` en geen slagschaduw; sectiekoppen zijn
   kleine hoofdletterlabels in `#6B7480`.
3. Er is één gedeelde statuspil-widget met de vier opgegeven kleurparen, en elke plek die
   status toonde met alleen een kleur toont nu kleur + woord (Koppelingen, Nachtchecks,
   Zoekopdrachten, en de kite-/strandfietsregel op Vandaag). Een widgettest dekt minimaal
   één van deze plekken op de aanwezigheid van het statuswoord.
4. De statuskleuren `#0CA30C`/`#FAB219`/`#D03B3B` komen nergens decoratief voor.
5. Het nieuwe logo-SVG staat in de repo, wordt op 30px in de header en op het loginscherm
   getoond, en het launcher-/web-icoon toont hetzelfde beeldmerk.
6. De header toont logo + "Robbert's assistent" klein, en op Vandaag/Health check daaronder
   de grote paginatitel met "Bijgewerkt om HH:mm" en rechts de reload-knop; verversen werkt
   nog exact als voorheen (eigen cache per tab, spinner, geen dubbele call).
7. De bottom-navigatie heeft precies 4 items met de labels Vandaag, Assistent, Taken, Meer;
   `MoreScreen` bevat werkende items naar Health check, Zoekopdrachten, Koppelingen,
   Nachtchecks, Geheugen en Updates, en elk van die schermen is bereikbaar.
8. Op een schermbreedte van 390px breekt geen enkel navigatielabel over meerdere regels
   (verifieerbaar via een widgettest met een 390px-brede testomgeving of via handmatige
   controle die in de worklog is vastgelegd).
9. Een tik op de briefing-push opent Vandaag; een tik op een watch-push opent het
   Zoekopdrachten-scherm. Beide zijn gedekt door de (aangepaste) tests in
   `test/home_screen_test.dart`.
10. `flutter analyze` en `flutter test` in `robberts_assistent/` zijn groen; bestaande tests
    die op oude labels/indices/teksten leunen zijn meegemigreerd, niet verwijderd.
11. Geen enkel bestand buiten `robberts_assistent/` (behalve de story-worklog) is gewijzigd.

## Aannames

- Alleen light mode; er wordt geen dark theme toegevoegd of onderhouden.
- Het renderen van het SVG-logo gebeurt met de dependency `flutter_svg`. Lukt het toevoegen
  van die dependency niet (bv. geen netwerk bij `flutter pub get`), dan is een equivalente
  Dart-weergave van hetzelfde beeldmerk (bv. `CustomPaint`) acceptabel, mits het SVG-bestand
  als bron in de repo blijft staan.
- Het launcher-icoon vereist een PNG-bron voor `flutter_launcher_icons`; die PNG wordt uit de
  SVG gegenereerd. Is er geen rasterisatie-tooling beschikbaar in de omgeving, dan mag de
  developer de PNG programmatisch opbouwen (het beeldmerk bestaat uit eenvoudige vormen) —
  zoals eerder bij SF-1247 met het alarmgeluid — en legt hij de gekozen route in de worklog vast.
- "Taken" is puur een hernoeming van de bestaande tab "Herinneringen" (`SchedulesScreen`);
  het scherm zelf en zijn eigen titel blijven ongewijzigd.
- "Geheugen" stond niet in de opsomming van de story, maar blijft in "Meer" staan zodat het
  scherm niet onbereikbaar wordt.
- De backend blijft 🟢/🟡/🔴 in de sectietekst zetten; de vertaling naar pil + woord gebeurt
  volledig client-side, zodat deze story frontend-only blijft. De woordkeuze is
  "goed" / "let op" / "kritiek".
- De AppBar-uitlogknop blijft bestaan (niet genoemd in de story, wel noodzakelijke functie).
- Geen wijziging aan de andere apps (`groentetuin`, `notities`, `wind`) of aan de
  native Android-lagen behalve het launcher-icoon.

## Eindsamenvatting

De Flutter-app heeft een nieuw rustig licht thema gekregen met randloze schaduwen, uniforme kaarten, sectiekoppen en toegankelijke statuspillen met kleur én tekst. Het nieuwe teal robotlogo is toegepast in de header, login en Android-/webiconen.

De navigatie bestaat nu uit Vandaag, Assistent, Taken en Meer, waarbij Assistent de starttab blijft. Health check, Zoekopdrachten, Koppelingen, Nachtchecks, Geheugen en Updates zijn bereikbaar via Meer. Briefing- en watch-pushes openen het juiste scherm; daarbij is ook de situatie opgelost waarin nog een Meer-route openstaat.

De bestaande refresh-, cache- en schermfunctionaliteit is behouden. Briefingemoji’s worden uitsluitend in de client vertaald naar statuspillen; backend en API-contracten zijn niet gewijzigd. Ook andere apps en dark mode vielen bewust buiten scope.

Verificatie: `flutter analyze` was groen, alle 55 Flutter-tests slaagden, inclusief tests voor statuswoorden, navigatie op 390px en push-deeplinks. Ook de web-releasebuild, formattering, launcher-iconengeneratie en scope-/diffcontroles waren groen. De story-brede test is goedgekeurd. Alleen een lokale release-APK kon niet worden gebouwd doordat de container geen Android SDK of `ANDROID_HOME` bevatte.
