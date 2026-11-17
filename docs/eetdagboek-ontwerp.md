# Eetdagboek met foto's — ontwerp

Status: **ontwerp, nog niets gebouwd** (17 november 2026). Schermontwerpen staan in de
artifact-versie van dit document: https://claude.ai/artifact/8yhwdq15eHShrTin5RjwVv
(privé, alleen voor Robbert).

Eén foto per eetmoment. De assistent zegt wat hij ziet, Robbert tikt op Klopt of verbetert
het, en de verbetering wordt onthouden. Garmin levert wat hij verbruikt. Daarnaast korte
check-ins voor honger en energie, om op termijn verbanden te vinden tussen eten, slaap,
bewegen en hoe hij zich voelt.

## 1. Doelen en gemaakte keuzes

| Doel | Gevolg voor het ontwerp |
|---|---|
| **Fit voelen, geen honger** (hoofddoel) | Check-ins zijn de maat waar alles op gericht is. Per maaltijd: hoe lang hield die vol (tijd tot eerste "trek", per 100 kcal). |
| **Niet zwaarder worden, liefst 2 kg eraf** (geen hoofddoel) | Geen dagdoel, geen rode cijfers. Wel een weekgemiddelde gegeten tegen verbruikt. |
| **Weten wat hij binnenkrijgt** | Eiwit, vezels en groente naast calorieën op het Vandaag-scherm, met richtwaarde (ca. 30–40 g vezels, 250 g groente per dag). Fruit, koolhydraten, vet in het weekoverzicht. |
| **Meer op vetverbranding** (bijvangst) | Niet meetbaar met deze middelen. Wel ter info: nachtelijke periode zonder eten en minuten op lage intensiteit. Geen streefwaarde. |

Keuzes:

- **Plek:** in de hoofdapp `robberts_assistent/` (tegel op Vandaag, scherm Eten onder Meer,
  snelkoppeling die direct de camera opent).
- **Gewicht:** handmatig invoeren, eens per paar weken.
- **Dranken:** foto, net als eten. Water en thee hoeven niet.
- **Check-ins:** drie meldingen per dag, alleen honger en energie.

## 2. Nauwkeurigheid

Een foto-schatting zit per maaltijd naar verwachting 20–40% naast de werkelijkheid; wat je
niet ziet (olie, boter, saus) telt niet mee. Goed genoeg voor een trend over weken, niet voor
exact tellen per dag. Wat het beter maakt, in volgorde van effect:

1. **Gewogen referenties** (zie §5) — vooral voor het vaste ontbijt en de vaste lunch.
2. **Recept of etiket erbij** (zie §3) — vangt de onzichtbare ingrediënten.
3. **Herhaling** — twintig tot dertig dingen komen steeds terug.
4. **Eigen servies als maatstaf** — eenmalig borden, kommen, glazen en mokken vastleggen met
   de maat. Daarna bestek in beeld; de hand (eenmalig gekalibreerd naast een bankpas) alleen
   buiten de deur of als de app twijfelt. De hand is dus een hulpmiddel, geen verplichting.
5. **Gewicht als ijkpunt** — berekende balans tegen de weegschaal; duurt twee tot drie
   maanden bij een weging eens per paar weken en blijft grof.

Volgorde van vertrouwen per onderdeel: gewogen → etiket → recept → vergeleken met een
referentie → geschat. Bij twee bronnen wint de hoogste.

## 3. UX

### Schermen

1. **Vastleggen** — camera opent direct. Meerdere foto's mogelijk, knop Klaar start de
   analyse. Optioneel inspreken ("met een scheut olijfolie", "de helft opgegeten") of een
   receptlink toevoegen. Onder de camera: "hoe voelde je je net voor het eten?".
2. **Voorstel** — komt als push. Per onderdeel naam, hoeveelheid, kcal en of hij twijfelt.
   Knoppen Klopt en Verbeteren. Geen reactie = telt mee als "niet bevestigd".
3. **Verbeteren** — regel aantikken en portie/product kiezen, grammen invullen met
   weegschaaltje ("gewogen"), of in gewone woorden zeggen wat niet klopt. Onderaan staat
   letterlijk wat hij ervan onthoudt.
4. **Vandaag** — gegeten naast verbruikt (rust + bewegen, stappen, trainingen), eiwit,
   vezels, groente, en de eetmomenten als tijdlijn. Compacte versie als Vandaag-tegel.
5. **Wat ik van je weet** — vaste gerechten (met aantal keer gegeten en gewogen),
   vuistregels, servies. Alles leesbaar, wijzigbaar, verwijderbaar.
6. **Check-in** — honger (geen · trek · honger · slap) en energie (1 moe – 5 fit). Twee
   tikken. De app zet er zelf context bij (tijd sinds laatste maaltijd, slaap, training).
7. **Verbanden** — elke bevinding met de getallen erachter en het aantal dagen waarop ze
   steunt; wat te dun is staat als zodanig gemarkeerd.

### Meerdere foto's per eetmoment

Een eetmoment is een mapje. De AI bepaalt zelf wat een foto is.

| Toevoeging | Levert op |
|---|---|
| Bord (en een tweede bij opscheppen) | Wat en hoeveel. Tweede bord telt erbij op. |
| Recept: foto of link | Alle ingrediënten voor het hele gerecht; het bord bepaalt welk deel gegeten is. |
| Verpakking of etiket | Waarden per 100 g en verpakkingsgewicht, afgelezen. |
| Ingrediënten op het aanrecht | Producten, merken en ongeveer hoeveel. |
| Weegschaal met display | Gemeten gewicht, en daarmee een referentie. |

Binnen een uur na een maaltijd een nieuwe foto → "Hoort dit bij je avondeten?" met Ja als
standaard. Na elke toevoeging wordt het hele eetmoment opnieuw doorgerekend. Recepten en
gelezen etiketten worden bewaard en hergebruikt.

### Check-ins

Vier ingangen: bij elke etensfoto, een widget op het startscherm, drie meldingen per dag met
actieknoppen (rond 10:30, 15:30, 20:30, instelbaar), en via chat/praatmodus.

De vaste meldingen zijn nodig: wie alleen logt bij een opvallend gevoel, heeft alleen
uitschieters en geen gewone momenten om mee te vergelijken.

### Overig

- Zonder foto kan ook: "ik heb net een appel en een koffie gehad" in de chat.
- Herkent hij een vast gerecht, dan is het voorstel één regel met één knop.
- Eén avondmelding als er om 20:30 nog geen avondeten staat (instelbaar).
- Weekscherm: gemiddelden, gewichtslijn, aandeel gewogen/vergeleken/geschat, aandeel
  bevestigd. Korte tekst in de zondag-briefing.

## 4. Garmin

| Route | Wat het levert | Oordeel |
|---|---|---|
| **Health Connect** (Garmin Connect-app → Health Connect → onze app) | Stappen, afstand, actieve en totale calorieën, trainingen, hartslag, slaap | **Aanrader.** Officieel, gratis, geen inloggegevens. Alleen Android/APK. |
| **Strava** (bestaat al) | Trainingen met calorieën | Aanvulling en terugval. |
| Onofficiële Garmin Connect-API | Alles | Afrader: sinds maart 2026 kapotte logins en geblokkeerde accounts. |
| Officiële Garmin Health API | Alles | Alleen voor bedrijven. |

De app leest de laatste zeven dagen uit Health Connect en stuurt ze naar de backend: bij het
openen, bij elke etensfoto en een paar keer per dag op de achtergrond.

**Nog te controleren op Robberts telefoon:** welke gegevens Garmin werkelijk doorzet. Body
Battery en stress komen vermoedelijk niet mee. Ontbreekt het dagtotaal aan calorieën, dan
rekent de backend rustverbruik zelf uit (leeftijd, lengte, gewicht) plus actieve calorieën.
Eerste bouwstap voor deze koppeling is daarom een proefscherm.

## 5. Hoe het leren werkt

Er wordt geen model getraind. Het leren zit in wat de AI bij elke nieuwe foto meekrijgt.

### Gewogen referenties

Een referentie is een foto waarvan de gewichten gemeten zijn.

- Robbert weegt wanneer het uitkomt: grammen invullen in het verbeterscherm, of inspreken.
- Foto van het bord op de weegschaal: de AI leest het display af.
- De app vraagt er zelden om (iets wat vijf keer gegeten en nooit gewogen is; hooguit één
  vraag per week).

Analyse in twee stappen: eerst herkennen wat er ligt, dan haalt de backend de gewogen foto's
van die producten/dat gerecht op en krijgt de AI ze ernaast met de gemeten waarden.

| Situatie | Hoe hij tot een getal komt | Label |
|---|---|---|
| Vast ontbijt/lunch, paar keer gewogen | Gemiddelde van de wegingen; foto alleen voor afwijkingen | GEWOGEN |
| Bekend product, andere hoeveelheid | Vergelijken met gewogen foto's, liefst zelfde servies | VERGELEKEN |
| Iets nieuws | Schatten op servies, bestek of hand | GESCHAT |

### Overige context per analyse

1. Profiel en servies-maten.
2. Het document met vuistregels.
3. Lijst vaste gerechten (meest gegeten bovenaan, met gebruikelijk tijdstip).
4. De laatste verbeterde maaltijden rond hetzelfde tijdstip, met voorstel én correctie.

Na elke bevestiging of correctie werkt een tweede, stil falende AI-aanroep het geheugen bij
(zelfde patroon als `memoryChatClient`).

Calorieën schat de AI in de eerste versie zelf. Later eventueel de NEVO-tabel (RIVM) als
opzoektabel voor vaste waarden per 100 g.

## 6. Verbanden vinden

Verwachting: één persoon, één dag per dag, en slaap, sport, eten en werkdruk bewegen
tegelijk. Reken op zes weken voor de eerste patronen; lees ze als aanwijzing.

1. **De backend rekent, de AI niet.** Per check-in vaste kenmerken: uren sinds laatste
   maaltijd, grootte en samenstelling daarvan, slaapduur afgelopen nacht, training vandaag
   en gisteren, stappen, energiebalans gisteren, tijdstip. Daarover groepsvergelijkingen in
   code, met aantallen.
2. **De AI verwoordt.** Wekelijks de uitgerekende tabellen → tekst in de zondag-briefing.
3. **Proefjes.** Twee weken iets anders doen en vergelijken.

Vaste maat: **hoe lang een maaltijd vol houdt** (tijd tot eerste check-in met trek of
honger, per 100 kcal), uitgesplitst naar eiwit, vezels en groente.

## 7. Technisch ontwerp

### Verloop van een foto

```mermaid
sequenceDiagram
    participant App as Flutter-app
    participant BE as Backend (nutrition)
    participant FS as Firestore + Storage
    participant AI as Agent Runtime (vision)
    App->>BE: POST /api/v1/meals (foto's, notitie)
    BE->>FS: foto's naar eten/, maaltijd status ANALYSEREN
    BE-->>App: 202 + maaltijd-id
    BE->>FS: profiel, vuistregels, gerechten, referenties
    BE->>AI: foto's + context, vraagt JSON terug
    AI-->>BE: items, grammen, kcal, zekerheid
    BE->>FS: status VOORSTEL
    BE-->>App: FCM-push type=meal
    App->>BE: PUT /api/v1/meals/{id} (klopt of verbeterd)
    BE->>FS: status BEVESTIGD of VERBETERD
    BE->>AI: wat valt hiervan te leren?
    BE->>FS: geheugen bijgewerkt
```

Zelfde asynchrone patroon als de chat sinds commit `4861233`.

### Backendmodules

| Module | Inhoud |
|---|---|
| `nutrition` (nieuw) | `Meal` + `MealRepository` (Firestore/in-memory), `MealPhotoStorage` (map `eten/`), `MealAnalyzer` met tool-loze `nutritionChatClient` (vision, JSON-uitvoer), `FoodMemoryRepository`, recepten, producten, referenties, `NutritionController`, `NutritionSectionProvider` (Vandaag-tegel). |
| `activity` (nieuw) | `ActivityDay` + repository, `POST /api/v1/activity/days` (batch upsert per datum), `EnergyBalanceService` (combineert met Strava, rekent rustverbruik uit als dat ontbreekt), `HealthConnectCouplingProbe` (leeftijd laatste sync). |
| `wellbeing` (nieuw) | `CheckIn` + repository, `POST /api/v1/checkins`, `CheckInPromptScheduler` (FCM met actieknoppen), `FeatureBuilder`, `InsightService` (berekeningen in code), tool-loze `insightChatClient`. |
| `assistant` (uitbreiding) | `NutritionTools` (`logMealFromText`, `getDayNutrition`, `getWeekNutrition`), `WellbeingTools` (`logCheckIn`, `getInsights`). |

### Firestore

| Collectie | Velden |
|---|---|
| `meals` | `eatenAt`, `attachments[]` (soort BORD/RECEPT/ETIKET/INGREDIËNTEN/WEEGSCHAAL, fotopad of link, wat de AI eruit las, tijdstip), `recipeId`, `note`, `status` (ANALYSEREN/VOORSTEL/BEVESTIGD/VERBETERD/MISLUKT), `items[]` (naam, gram, kcal, eiwit, koolhydraten, vet, vezels, groente-gram, fruit-gram, zekerheid, `basis` GEWOGEN/VERGELEKEN/GESCHAT), `aiProposal` (oorspronkelijk voorstel, blijft bewaard), `dishId`, `sizeReference` |
| `food-dishes` | naam, omschrijving, vaste items, kcal, voorbeeldfoto, `timesEaten`, `lastEatenAt` |
| `recipes` | naam, bron, ingrediënten met hoeveelheden, porties, totaal en per portie, `timesEaten` |
| `food-products` | naam, merk, waarden per 100 g, verpakkingsgewicht |
| `food-references` | per gewogen onderdeel: product, gram, `mealId`, fotopad, servies, datum |
| `food-memory` | één tekstdocument met vuistregels (zelfde opzet als `assistant-memory`) |
| `food-profile` | servies met maten, handbreedte, lengte, geboortejaar |
| `activity-days/{datum}` | stappen, actieve kcal, totale kcal, trainingen[] (met minuten lage intensiteit), slaap, rusthartslag, gewicht (handmatig), bron, `syncedAt` |
| `checkins` | `at`, `hunger` (0–3), `energy` (1–5), `note`, `source` (FOTO/WIDGET/MELDING/CHAT), `mealId`, `features` |
| `insights` | per bevinding: omschrijving, beide groepen met gemiddelde en aantal, sterkte, `computedAt`; lopende proefjes |

Het bewaarde `aiProposal` maakt meetbaar of hij beter wordt: aandeel maaltijden dat zonder
wijziging bevestigd wordt, per week.

### App

- Schermen in `robberts_assistent/`; camera en multipart-upload bestaan al.
- Foto verkleinen tot ca. 1600 px voor upload.
- Receptlink delen vanuit de browser (Android-deelmenu); backend haalt de pagina op met
  dezelfde techniek als `watches` (HTML → platte tekst, begrensd).
- Android-snelkoppeling "Foto van eten" (`quick_actions`).
- Health Connect via het Flutter-pakket `health`; achtergrond-sync met `workmanager`. In de
  web-versie staat dit uit.
- Push `type=meal` opent het voorstelscherm.

### Bestaande afspraken

- Alle AI via de Agent Runtime; onder `RA_MOCK_AI` een vast voorstel uit `MockChatModel`.
- In-memory fallback voor elke repository; preview schrijft niet naar de echte Firestore.
- Modulegrenzen via `ModulithArchitectureTest`.
- Etensfoto's gaan naar de AI-leverancier, net als moestuin- en chatfoto's.
  Gezondheidscijfers blijven in de eigen Firestore.

## 8. Bouwvolgorde

1. **Foto, voorstel, bevestigen.** Module `nutrition`, schermen 1–3, daglijst. Meerdere
   foto's per eetmoment (incl. etiket en receptfoto), invoerveld voor gewogen grammen en
   vuistregels als tekstgeheugen zitten er meteen in.
2. **Check-ins.** Module `wellbeing` zonder analyse: scherm 6, vraag onder de camera,
   widget, drie meldingen. Vroeg, omdat de data weken moet oplopen.
3. **Garmin-proef en koppeling.** Proefscherm Health Connect, dan module `activity` met
   stappen, trainingen en slaap, en het Vandaag-scherm.
4. **Beter leren.** Gewogen referenties met analyse in twee stappen, bewaarde recepten en
   producten, receptlinks, vaste gerechten, servies-kalibratie, scherm 5.
5. **Overzicht en gemak.** Weekscherm, Vandaag-tegel, chat-tools, avondmelding,
   gewichts-ijking.
6. **Verbanden.** Kenmerken, groepsvergelijkingen, scherm 7, weektekst, proefjes. Zinvol
   na een week of zes aan check-ins.

## Bronnen

- [Android Central: wat Garmin wel en niet deelt met Health Connect](https://www.androidcentral.com/wearables/garmin/heres-everything-garmin-will-and-wont-share-with-google-health-connect)
- [Google Health & Fitness-nieuwsbrief, augustus 2025](https://developer.android.com/health-and-fitness/community/newsletters/2025/08)
- [python-garminconnect, issue 332](https://github.com/cyberjunky/python-garminconnect/issues/332)
- [Garmin-forum: account geblokkeerd na API-login](https://forums.garmin.com/developer/fit-sdk/f/discussion/435087/persistent-429-on-api-login-account-blocked-for-48-hours)
