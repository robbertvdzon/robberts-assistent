# SF-1156 - starten op assistent pagina

## Story

starten op assistent pagina

<!-- refined-by-factory -->

## Scope
De Flutter-app `robberts_assistent` toont na inloggen (`HomeScreen`) standaard de tab "Samenvatting" (dagelijks overzicht). Deze story wijzigt het standaard-starttabblad naar "Assistent", zodat de gebruiker na het openen van de app (en na inloggen) direct in het assistent-gesprekkenscherm terechtkomt in plaats van in het overzicht. De overige tabs (Samenvatting, Herinneringen, Meer) en de navigatie ertussen blijven ongewijzigd; de gebruiker kan nog steeds handmatig naar "Samenvatting" of elke andere tab navigeren.

## Acceptance criteria
- Bij het openen van de app (na succesvolle Google-login) staat de bottom-navigation direct op de tab "Assistent" (niet "Samenvatting").
- Het scherm dat getoond wordt bij het opstarten is `ConversationsScreen` (assistent-gesprekkenlijst), niet `SummaryScreen`.
- De overige drie tabs (Samenvatting, Herinneringen, Meer) blijven functioneel bereikbaar en ongewijzigd; alleen het standaard-geselecteerde tabblad verandert.
- Dit gedrag geldt zowel voor de web-build als de APK (geen platform-specifiek onderscheid).
- Bestaande tests (`flutter test`/`flutter analyze` in `robberts_assistent/`) blijven slagen, inclusief eventuele test die het standaard-startscherm verifieert (deze moet zo nodig worden bijgewerkt naar de nieuwe verwachte starttab).

## Aannames
- "de overzicht" in de storytekst verwijst naar de tab "Samenvatting" (dagelijkse samenvatting) in `HomeScreen`, de tab met index 0.
- "het assistent scherm" verwijst naar de tab "Assistent" (`ConversationsScreen`, gesprekkenlijst), tab-index 1 in de bestaande `NavigationBar` van `home_screen.dart`.
- Dit betreft alleen het standaard-geselecteerde tabblad bij app-start (`_tab` state in `_HomeScreenState`); er wordt geen nieuwe navigatielogica, deep-linking of onthouden-laatste-tab-functionaliteit gevraagd.

## Eindsamenvatting

{"phase":"summarized"}
