# SF-802 — Handmatige testinstructies (voor de PR)

Deze verificaties kunnen niet in CI draaien en gelden als afgeronde
documentatie, niet als geautomatiseerde test. Neem ze over in de
PR-beschrijving.

## 1. Google App Actions test-tool (Android Studio)

1. Open het project in Android Studio met de **Google Assistant**-plugin
   (App Actions test tool).
2. Installeer de app op een emulator/toestel (`flutter run` of de gebouwde
   APK).
3. Open **Tools → App Actions → App Actions Test Tool** en klik **Create
   preview** (leest `wind/android/app/src/main/res/xml/shortcuts.xml`).
4. Selecteer de capability `custom.actions.intent.GET_WIND_SPEED`, klik **Run**.
   Verwacht: de app spreekt de windsnelheid uit en er verschijnt een notificatie
   met dezelfde tekst; er is geen zichtbaar scherm.
5. Herhaal voor `custom.actions.intent.GET_WIND_FORECAST` (voorspelling).

## 2. Echte "Hey Google, vraag Wind ..."-flow (telefoon)

1. Installeer de release-APK (van de GitHub Release) op een Android-telefoon met
   Google Assistant, ingelogd met het account dat aan de app-preview is
   gekoppeld.
2. Open de app één keer en sta notificaties toe (Android 13+ vraagt
   `POST_NOTIFICATIONS`).
3. Zeg: **"Hey Google, vraag Wind naar de huidige windsnelheid."**
   Verwacht: het antwoord wordt uitgesproken en als notificatie getoond, zonder
   dat de app een scherm opent.
4. Zeg: **"Hey Google, vraag Wind naar de voorspelling."** en controleer
   hetzelfde voor de voorspelling.

## 3. Notificatie op Garmin-horloge (Garmin Connect)

1. Koppel een Garmin-horloge via **Garmin Connect** en zet
   **Smart Notifications** aan (sta app-notificaties van "Wind" toe).
2. Trigger een van de flows hierboven (spraak of App Actions test-tool).
3. Controleer dat de notificatietekst op het Garmin-horloge verschijnt en
   overeenkomt met de uitgesproken/getoonde tekst.
