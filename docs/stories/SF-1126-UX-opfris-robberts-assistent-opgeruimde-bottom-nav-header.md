# SF-1126 - UX-opfris robberts_assistent: opgeruimde bottom-nav, header met logo & nieuwe iconen

## Story

UX-opfris robberts_assistent: opgeruimde bottom-nav, header met logo & nieuwe iconen

<!-- refined-by-factory -->

## Scope

App: `robberts_assistent` (Flutter). UI-opfris in drie onderdelen: bottom-navigatie, header en app-iconen.

**1. Bottom-nav opschonen (`lib/home_screen.dart`)**
- Terugbrengen van de huidige `NavigationBar` met 6 tabs (Samenvatting, Assistent, Herinneringen, Koppelingen, Nachtchecks, Updates) naar 4 tabs: **Samenvatting**, **Assistent**, **Herinneringen**, **Meer**.
- Nieuw scherm `MoreScreen` (nieuw bestand, bv. `lib/more_screen.dart`) met een nette lijst (`ListTile` + icoon) die doornavigeert naar de bestaande `CouplingsScreen`, `NightlyChecksScreen` en `UpdatesScreen` (bv. via `Navigator.push`).
- Bestaande schermen en hun functionaliteit blijven ongewijzigd; alleen de navigatie-structuur verandert.
- `IndexedStack`-gedrag voor de 4 hoofdtabs blijft behouden (state van Samenvatting/Assistent/Herinneringen blijft bewaard bij tab-wissel); de sub-schermen achter 'Meer' hoeven niet in de `IndexedStack` te zitten aangezien het losse navigatie-stappen zijn.

**2. Header**
- `AppBar` in `home_screen.dart` krijgt een klein logo links van de titel "Robbert's assistent", gebaseerd op het (vernieuwde) app-icoon.

**3. Iconen**
- Nieuw, verzorgd assistent-icoon (Nederlandse UI, consistent met de bestaande paarse/deepPurple huisstijl) genereren en `assets/icon/icon.png` vervangen.
- Launcher-icons regenereren via `flutter_launcher_icons` (bestaande config in `pubspec.yaml`, inclusief de reeds aanwezige `web:`-sectie) — dit ververst zowel de Android-launcher-icons als `web/icons/Icon-192/512(-maskable).png` en `web/favicon.png`.
- `web/manifest.json` controleren/aanpassen waar nodig zodat naam en iconen consistent zijn met de nieuwe iconen.

## Acceptance criteria

- `home_screen.dart` toont een `NavigationBar`/bottom-nav met precies 4 items: Samenvatting, Assistent, Herinneringen, Meer.
- Een 'Meer'-tab opent een `MoreScreen` met lijst-items (icoon + label) naar Koppelingen, Nachtchecks en Updates; elk item navigeert naar het bestaande, ongewijzigde scherm.
- De `AppBar` toont een logo (klein icoon-beeld) naast de titeltekst "Robbert's assistent".
- `assets/icon/icon.png` is vervangen door een nieuw, verzorgd icoon; `flutter_launcher_icons` is opnieuw gedraaid zodat Android-launcher-icons én `web/icons/Icon-192/512(-maskable).png` + `web/favicon.png` het nieuwe icoon tonen.
- `web/manifest.json` is gecontroleerd en waar nodig bijgewerkt zodat deze consistent is met het nieuwe icoon/naam.
- Bestaande functionaliteit van Samenvatting, Assistent, Herinneringen, Koppelingen, Nachtchecks en Updates blijft ongewijzigd werken.
- `flutter test` blijft groen, inclusief de bestaande `test/widget_test.dart`.
- Er is een (nieuwe of bijgewerkte) widget-test die verifieert dat de bottom-nav 4 tabs telt en dat de 'Meer'-tab het nieuwe overzichtsscherm opent.
- UI-teksten en code-commentaar zijn in het Nederlands.

## Aannames

- De ontwerpvrijheid voor het nieuwe icoon/logo (exacte vorm, kleurgebruik binnen de bestaande deepPurple-huisstijl) ligt bij de developer; alleen "verzorgd en herkenbaar als assistent-icoon" is een harde eis.
- Het logo in de `AppBar` is een kleine, statische afbeelding (geen tap-actie); geplaatst als `leading`-widget of naast de titel, bijvoorbeeld via een `Row` in de `title`-property.
- 'Meer' navigeert via een gewone `Navigator.push` (nieuw scherm, met terug-knop) in plaats van als vijfde item in de `IndexedStack`, omdat de onderliggende schermen (Koppelingen/Nachtchecks/Updates) geen frequente tab-wissel nodig hebben.
- De volgorde van de lijst-items in `MoreScreen` is: Koppelingen, Nachtchecks, Updates (zelfde volgorde als in de huidige bottom-nav).
- Bestaande routes/imports voor `CouplingsScreen`, `NightlyChecksScreen`, `UpdatesScreen` blijven ongewijzigd; alleen de plek van waaruit ze aangeroepen worden verandert.
- Geen wijzigingen nodig aan de backend of aan andere apps (`groentetuin`, `notities`, `wind`); dit is een puur front-end/`robberts_assistent`-scoped UI-verandering.

## Eindsamenvatting

Eindsamenvatting SF-1126:

# SF-1126 — UX-opfris robberts_assistent: opgeruimde bottom-nav, header met logo & nieuwe iconen

## Wat is gebouwd

- **Bottom-navigatie teruggebracht van 6 naar 4 tabs**: Samenvatting, Assistent, Herinneringen, Meer (`lib/home_screen.dart`). `IndexedStack`-gedrag blijft behouden, dus de state van de drie hoofdschermen blijft bewaard bij tab-wissel.
- **Nieuw `MoreScreen`** (`lib/more_screen.dart`): lijst met 3 `ListTile`-items (Koppelingen, Nachtchecks, Updates, in die volgorde) die via `Navigator.push` naar de bestaande, ongewijzigde schermen navigeren.
- **Header-logo**: `AppBar`-titel uitgebreid met een `Row` met daarin het app-icoon (28×28, `Image.asset('assets/icon/icon.png')`) links van de titeltekst "Robbert's assistent".
- **Nieuw app-icoon**: verzorgd, zelf gegenereerd assistent-icoon (deepPurple-gradient, afgeronde hoeken, chatbubbel met typing-indicator-stippen, geel accent) consistent met de bestaande huisstijl. `assets/icon/icon.png` vervangen en `flutter_launcher_icons` opnieuw gedraaid, waardoor zowel de Android-launcher-icons als `web/icons/Icon-192/512(-maskable).png` en `web/favicon.png` zijn ververst.
- **`web/manifest.json`**: de `description` (nog Flutter-scaffold-placeholder) vervangen door een korte Nederlandse omschrijving; naam en iconenlijst waren al consistent.
- **`pubspec.yaml`**: `assets/icon/icon.png` toegevoegd aan `flutter.assets`, nodig omdat het icoon nu ook rechtstreeks in de `AppBar` gebruikt wordt.
- Nieuwe test `test/home_screen_test.dart`: verifieert dat de bottom-nav precies 4 tabs telt, dat 'Meer' de 3 verwachte lijst-items toont, en dat elk item naar het juiste scherm navigeert.

## Belangrijke keuzes

- 'Meer' is een gewone `Navigator.push` (los scherm met terugknop) i.p.v. een vijfde item in de `IndexedStack`, conform de story-aannames.
- Bestaande schermen (`CouplingsScreen`, `NightlyChecksScreen`, `UpdatesScreen`) zijn functioneel ongewijzigd gelaten; alleen de plek van waaruit ze aangeroepen worden is veranderd.
- Backend en de andere apps (`groentetuin`, `notities`, `wind`) zijn niet aangeraakt.

## Getest

- Lokaal (developer, reviewer én tester, onafhankelijk van elkaar): `flutter analyze` (geen issues) en `flutter test` — 11/11 tests groen.
- Live op de preview-omgeving (PR-10), zonder Google-login: met Playwright/Chromium geverifieerd en gescreenshot dat de 4-tabs-navigatie, het header-logo, 'Meer' met de 3 items, en navigatie naar Koppelingen correct werken.
- Geen bugs gevonden.

## Bewust niet gedaan / opgemerkt, geen actie nodig

- De 'Meer'-tab toont een dubbele AppBar. Dit is een bestaand patroon in de app (ook bij Herinneringen), dus geen regressie — expliciet buiten scope gehouden.
