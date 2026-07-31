# Robberts assistent

Flutter-app (APK + web) voor de briefings, chat-assistent, reminders en
langdurige zoekopdrachten van `robberts-assistent-backend`. Google-login is
actief op web/productie en wordt op PR-previews overgeslagen via
`SKIP_GOOGLE_AUTH`; zie [`../docs/factory/deployment.md`](../docs/factory/deployment.md).

## Navigatie en vormgeving

De bottom-navigation bevat vier hoofdbestemmingen: **Vandaag**, **Assistent**, **Taken** en
**Meer**. De app opent standaard Assistent. Health check, Zoekopdrachten, Koppelingen,
Nachtchecks, Geheugen en Updates blijven bereikbaar als losse routes vanuit Meer.

De app gebruikt alleen een rustig licht thema: een lichtgrijze achtergrond, witte kaarten met
een dunne rand en zonder schaduw, en teal als accentkleur. Het teal-witte robotbeeldmerk staat in
de app-header, op het loginscherm en in de Android- en webiconen. Status wordt nooit alleen met
kleur aangeduid: Koppelingen, Nachtchecks en Zoekopdrachten gebruiken gedeelde pillen met
**goed**, **let op**, **kritiek** of **neutraal**. De groene, gele en rode backend-emoji's in
kite- en strandfietsregels worden op Vandaag client-side naar dezelfde woordelijke pillen
vertaald; API-responses blijven ongewijzigd.

## Schermen

- **Vandaag** — dagelijkse briefing uit de backend (`GET /api/v1/briefing`): twee losse kaarten
  voor kite-kans (per dagdeel wind + richting) en strandfietskans (per dagdeel een bolletje met
  onderbouwing: wind, regen, getij) voor morgen — sinds SF-1192 gesplitst, was voorheen één
  samengevoegde kaart —, afspraken komende 7 dagen met per afspraak een reminder-status en,
  indien nog geen reminder staat, een één-tap-actie om er één ~1u vooraf aan te maken, een
  AI-weektakensamenvatting en een moestuin-placeholder. Een tik op de dagelijkse 18:00-FCM-push
  sluit eventueel openstaande Meer-routes en opent dit scherm automatisch
  (`lib/fcm_service.dart`, `FcmService.deepLinkTarget`).
- **Health check** — het ruwe systeem-checkrapport uit de onafhankelijke
  Health-check-cache (`GET /api/v1/briefing/health`), met eigen timestamp en
  reload-actie.
- **Assistent** — gesprekkenlijst (titel + laatst bijgewerkt) met een knop voor een nieuw
  gesprek; een gesprek opent het chatscherm en blijft persistent in Firestore, inclusief een
  door de assistent zelf verzonnen titel en verstuurde foto's (camera/galerij). De lijst toont
  eerst de 10 meest recente (niet-gearchiveerde) gesprekken, oudere onder een uitklapbare
  "Ouder"-sectie; swipe-links (`flutter_slidable`) biedt archiveren en verwijderen (met
  bevestiging), een AppBar-toggle toont ook gearchiveerde gesprekken. Chat met de backend's AI
  (Spring AI/OpenAI), met tools voor Robberts notitie, reminders/alarms, agenda, Google Docs en
  windmetingen/-voorspellingen bij IJmuiden (`robberts-assistent-backend/.../assistant/ai/`).
- **Taken** — hoofdnavigatielabel voor het bestaande scherm **Herinneringen**, met overzicht en
  beheer van reminders en alarmen.
- **Zoekopdrachten** — maakt, bewerkt en verwijdert langdurige websitezoekopdrachten en
  toont per opdracht de meest recente leesbare status. Titel, absolute
  HTTP(S)-URL, zoekinstructie, frequentie (`Dagelijks` of `Kantooruren`) en
  pushvoorkeur worden afzonderlijk ingevoerd. De backend valideert dezelfde
  velden opnieuw. Een tik op een watch-push opent dit scherm als verse route en herlaadt de lijst;
  ook handmatig openen en de reload-knop halen actuele gegevens op. Naast de
  reload-knop staat een "nu draaien"-knop (`Icons.play_circle_outline`, tooltip
  "Alle zoekopdrachten nu controleren") die alle actieve opdrachten meteen laat
  controleren zonder op het schema te wachten; tijdens de run toont die knop een
  voortgangsindicatie en zijn de run-, reload- en toevoegknop uitgeschakeld (dus
  geen tweede run), terwijl de bestaande lijst zichtbaar blijft. Na afloop toont
  de lijst de teruggekomen statussen; faalt de call, dan verschijnt een
  `SnackBar` met "Nu controleren mislukt: …" en blijft de lijst staan.
- **Meer** — toegang tot Health check, Zoekopdrachten, Koppelingen, Nachtchecks, Geheugen en
  Updates.
- **Geheugen** (`lib/memory_screen.dart`, via "Meer") — één groot bewerkbaar tekstveld met de
  volledige geheugen-tekst (feiten/voorkeuren over Robbert) die de assistent automatisch
  bijhoudt na elke chat-beurt en als context gebruikt in latere gesprekken; auto-save (zelfde
  patroon als `notities/lib/notes_editor_screen.dart`).
- **Updates** — toont voor alle drie de apps (wind, robberts_assistent, notities)
  de geïnstalleerde vs. laatste GitHub-Release-versie, met een bijwerk-knop per
  app (zie `lib/update_checker.dart`/`lib/updates_screen.dart`).

Bij opstarten checkt de app ook zichzelf (async, niet-blokkerend) en vraagt een
dialoogje om bij te werken als er een nieuwere versie is (`lib/self_update_prompt.dart`).

Briefing-pushes openen Vandaag (index 0); watch-pushes openen rechtstreeks een nieuwe
Zoekopdrachten-route. De deeplink geeft daarom een doel door en geen tab-index.

## Zoekopdrachten-API

Alle calls sturen het bestaande Bearer-sessietoken mee.

| Methode | Pad | Gedrag |
|---|---|---|
| `GET` | `/api/v1/watches` | Geeft `{"watches":[...]}` terug; actieve opdrachten eerst, daarna op titel. |
| `POST` | `/api/v1/watches` | Maakt een opdracht met `title`, `url`, `instruction`, `frequency` en `notifyOnFound`. |
| `PUT` | `/api/v1/watches/{id}` | Wijzigt dezelfde vijf velden en activeert de opdracht voor een nieuwe controle. |
| `DELETE` | `/api/v1/watches/{id}` | Verwijdert de opdracht en geeft de resterende lijst terug. |
| `POST` | `/api/v1/watches/run-now` | Controleert synchroon alle opdrachten met `active == true` (ongeacht frequentie en `lastCheckedAt`) en geeft daarna de bijgewerkte lijst in dezelfde vorm als `GET` terug. |

Een watch-response bevat `id`, de vijf invoervelden, `status`,
`statusDescription`, `lastCheckedAt` en `active`. Mogelijke statussen zijn
`NOG_NIET_GECONTROLEERD`, `NIET_GEVONDEN`, `GEVONDEN` en `ONBEKEND`.
Na wijzigen zijn status en omschrijving weer `NOG_NIET_GECONTROLEERD`/`Nog niet gecontroleerd.`,
is `lastCheckedAt` leeg en is de opdracht actief. Validatiefouten op `POST` en `PUT` geven HTTP 400
met een Nederlandstalig `message`; wijzigen van een onbekende id geeft HTTP 404.

## Build & test

```bash
flutter pub get
flutter test
flutter build apk --release \
  --build-number=<N> \
  --dart-define=API_BASE_URL=https://robberts-assistent.vdzonsoftware.nl \
  --dart-define=GOOGLE_CLIENT_ID=<web-oauth-client-id>
```

CI (`.github/workflows/robberts-assistent-apk.yml`) bouwt en publiceert de
release-APK naar de vaste GitHub-Release-tag `robberts-assistent-latest` bij
elke push naar `main`; `frontend-image.yml` bouwt de web-variant.
