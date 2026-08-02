# SF-1809 - Notities: lettergrootte instelbaar in de app

## Story

Notities: lettergrootte instelbaar in de app

<!-- refined-by-factory -->

## Samenvatting

De lettergrootte van de bewerkbare notitie moet lokaal instelbaar zijn met A− en A+.
De gekozen grootte wordt na een herstart automatisch hersteld.
Dit verandert uitsluitend de weergave; de inhoud en opslag van de notitie blijven ongewijzigd.

## Scope

- Voeg in het notitiescherm twee toegankelijke bedieningen toe voor `A−` en `A+`, met de tooltips `Lettergrootte verkleinen` en `Lettergrootte vergroten`.
- Plaats de bedieningen in de AppBar of opmaakbalk en zorg dat het scherm ook op smalle toestellen geen layout-overflow krijgt.
- Gebruik 16 pt als standaard en bied de vaste reeks 12, 14, 16, 18, 20, 22, 24, 26 en 28 pt.
- Pas een wijziging direct toe op alle tekst in de Quill-editor, inclusief bulletmarkeringen en opgemaakte tekst.
- Bewaar de gekozen waarde lokaal met `shared_preferences` en herstel deze voordat de geladen editor wordt getoond.
- Behandel de lettergrootte uitsluitend als weergavevoorkeur: wijzig geen Quill-documentattributen, markdownconversie, API-contracten of backendcode.
- Laat de bestaande editorfuncties, opmaak, undo/redo, versieherstel, autosave, handmatig opslaan en uitloggen intact.

## Acceptance criteria

- Zonder opgeslagen voorkeur gebruikt de editor 16 pt.
- Iedere druk op A− of A+ verandert de lettergrootte onmiddellijk met 2 pt.
- De grootte kan niet lager dan 12 pt of hoger dan 28 pt worden; op de betreffende grens is de bijbehorende knop uitgeschakeld.
- Normale tekst, vet/cursief/onderstreepte tekst en zowel de tekst als markering van opsommingen worden met dezelfde gekozen basisgrootte weergegeven.
- De keuze wordt lokaal opgeslagen en een nieuw opgebouwd notitiescherm herstelt deze met `SharedPreferences`; de voorkeur wordt niet naar de backend verstuurd.
- Alleen wijzigen van de lettergrootte markeert de notitie niet als gewijzigd en veroorzaakt geen autosave of andere aanroep van `PUT /api/v1/notes`.
- Na wijzigen van de lettergrootte levert handmatig opslaan exact dezelfde markdown op als vóór de wijziging, inclusief bestaande opmaak en opsommingen.
- Widget-tests in `notities/test/` bewijzen de aanpassing van de gebruikte fontgrootte, beide grenzen, de disabled-status van de knoppen en bewaren/herstellen via `SharedPreferences.setMockInitialValues`.
- De bestaande tests in `notities/test/notes_editor_screen_test.dart` en `notities/test/widget_test.dart` blijven slagen.

## Aannames

- De voorkeur geldt voor deze app-installatie en blijft ook na uitloggen behouden.
- Alleen de bewerkbare notitietekst schaalt mee; AppBar, opmaakbalk, statusmeldingen en de alleen-lezen versieweergave behouden hun bestaande grootte.
- Een ontbrekende of ongeldige opgeslagen waarde valt terug op 16 pt; een waarde buiten het bereik wordt binnen 12–28 pt begrensd.

## Eindsamenvatting

### Eindsamenvatting SF-1809

De notities-editor heeft toegankelijke A−/A+-knoppen gekregen voor een lokale lettergrootte van 12 t/m 28 pt in stappen van 2 pt. Standaard is 16 pt. De keuze wordt via SharedPreferences bewaard, vóór het laden toegepast en blijft na uitloggen of herstart behouden. De opmaakbalk is horizontaal scrollbaar voor smalle schermen.

De grootte schaalt gewone en opgemaakte tekst, lijsttekst en bulletmarkeringen. Het Quill-document, markdownformaat, autosavegedrag, API-contract en backend zijn bewust niet gewijzigd. Alleen aanpassen van de weergave veroorzaakt geen save.

Verificatie:

- Gerichte editortests: 21/21 groen.
- Volledige Flutter-tests: 50/50 groen.
- `flutter analyze`: geen issues.
- Release-bundlecompile: geslaagd.
- Grenzen, disabled knoppen, opslag/herstel, ongeldige waarden, smalle layout, save-isolatie en byte-identieke markdown zijn afgedekt.
- Bestaande opmaak-, autosave-, undo/redo- en versieherstelfuncties bleven groen.

Een APK-build kon niet lokaal worden uitgevoerd doordat de ARM64-omgeving geen Android SDK bevat. Browser- en previewtests zijn bewust niet uitgevoerd omdat de notities-app uitsluitend als APK wordt geleverd. Er zijn geen bugs of blockers gevonden.
