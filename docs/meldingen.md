# Meldingengeschiedenis

Het belletje in de vaste appbalk en **Meer → Meldingen** openen `/meer/meldingen`.
Berichten staan met datum en volledige tekst in een blijvend archief. Ongelezen berichten zijn
vetgedrukt en hebben een ander icoon. Tikken toont de volledige tekst en markeert het bericht
als gelezen; **Openen** gaat naar het bijbehorende scherm als het type bekend is. Lezen en
openen verwijderen geen berichten. De lijst ververst bij terugkomst in de app en via trekken
of de vernieuwknop. Oudere berichten worden per 50 bijgeladen.

## Opslag en API

`push.PushService.sendToAll` bewaart elk bericht één keer vóór de FCM-pogingen, onafhankelijk
van het aantal telefoons. Ook zonder geregistreerde telefoon en bij een afleverfout staat
het bericht in de app. Een opslagfout wordt gelogd en blokkeert de FCM-poging niet.
De historie zegt dat een bericht is aangemaakt; dit is geen afleverbevestiging van de telefoon.
Firestore-collectie `notification-history`: één UUID-document per bericht met `title`, `body`,
`data`, `createdAt` en `readAt` (epoch-millis, `readAt` mag null zijn). Zonder Firebase gebruikt
de backend een in-memory variant. Er is geen automatische verwijdering of bewaartermijn.

Beide endpoints gebruiken de bestaande sessie-autorisatie:

- `GET /api/v1/notifications?limit=50&before=<id>` geeft `items` en `nextCursor`.
  Limit 1–100; nieuwste eerst, met document-id als tweede sorteerveld bij gelijke tijden.
  Cursorpaginering voorkomt overslaan of dubbelen als er tussendoor een bericht bijkomt.
- `POST /api/v1/notifications/{id}/read` geeft het bericht met de eerste leestijd terug.
  Herhaald lezen verandert die tijd niet. Onbekende id's leveren 404 op.

FCM krijgt `notificationId` mee. Een tik op een push markeert het bericht best-effort als
gelezen en navigeert ook als die schrijfactie mislukt. Voorgrondmeldingen bewaren de volledige
data als JSON in hun lokale payload; oude payloads met alleen het type blijven werken.
De gelezenstatus is gedeeld tussen de apparaten van deze persoonlijke assistent.

## Beschikbaarheid en verificatie

De geschiedenis begint na deployment van de backend. Oude pushteksten kunnen niet worden
teruggehaald. Voor de meldingensectie en het markeren vanuit de telefoon is de nieuwe appversie
nodig. Bestaande appversies blijven push ontvangen.

Geautomatiseerde controles dekken autorisatie, paginering, gelijke tijdstippen, behouden van
gelezen berichten, meerdere telefoons, navigatie, oudere lokale payloads, laden/herstellen en
het scherm op 390px breedte. Backend: `mvn test`; app: `flutter test` en `flutter analyze`.
Handmatig op een testtoestel controleren: meerdere pushes in voor- en achtergrond ontvangen,
één openen, historie en gelezenstatus controleren, app herstarten en historie opnieuw openen.
Android-groepering en het verdwijnen van systeemmeldingen zijn hiermee niet op hardware bewezen
opgelost; het archief blijft daar onafhankelijk van.
