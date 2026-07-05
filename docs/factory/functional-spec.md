# Functional Spec

## Doel

De **Wind**-app is een proof-of-concept die aantoont dat de keten
"Hey Google" → Android App Actions → eigen app werkt met een hands-free gevoel
(spraak + notificatie, geen zichtbaar scherm), vóórdat er een backend of echte
weerdata wordt gebouwd.

## Gebruikersflows

1. **Hands-free (hoofd-PoC).** De gebruiker zegt via Google Assistant iets als
   "Hey Google, vraag Wind naar de huidige windsnelheid" of "... naar de
   voorspelling". De bijbehorende trampoline-activity start onzichtbaar, spreekt
   het antwoord uit én post een notificatie met exact dezelfde tekst, en sluit
   zichzelf direct af. De notificatie kan doorkomen op een gekoppeld
   Garmin-horloge (smart-notifications).
2. **Handmatig (test zonder spraak).** De gebruiker opent de app en ziet een
   scherm met dezelfde windsnelheid- en voorspellingswaarden.

## Antwoorden

- **Huidige windsnelheid**: hardcoded waarde met eenheid.
- **Voorspelling**: hardcoded voorspellingstekst.
- De uitgesproken tekst, de notificatietekst en de schermtekst zijn identiek.

## Acceptatiecriteria (terugkerend)

- Buildbare Flutter/Android-app; `flutter build apk --release` slaagt.
- Minimaal 2 App Actions-capabilities, gekoppeld aan trampoline-activities.
- Elke trampoline-activity spreekt uit via TextToSpeech, post een gelijke
  notificatie en toont geen zichtbaar scherm.
- CI publiceert bij push naar `main` een installeerbare release-APK als GitHub
  Release.

## Buiten scope (latere stories)

Backend, OpenShift, Postgres, echte weerdata, Todo-app, Assistent-app,
Telegram-koppeling.
