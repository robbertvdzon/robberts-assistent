# Privacy bij de persoonlijke bankkoppeling

Deze pagina beschrijft de bankkoppeling die wordt ontwikkeld voor Robberts Assistent.
Datum: 21 november 2026.

## Doel en beheer

Robbert van der Zon gebruikt deze toepassing voor zijn eigen huishoudelijke
administratie. De bankkoppeling is bedoeld om rekeninggegevens, saldi en transacties
van door hem gekoppelde betaalrekeningen te bekijken en maandelijkse uitgaven te
rubriceren. Contact over gegevensbescherming: robbert@vdzon.com.

## Toegang tot rekeninggegevens

Enable Banking verzorgt de verbinding met de bank. De rekeninghouder geeft bij de
bank toestemming voor het lezen van rekeninginformatie. Bankwachtwoorden en pincodes
worden bij de bank ingevoerd. Robberts Assistent ontvangt deze gegevens niet.
De toepassing gebruikt de rekeninginformatiedienst. De bankkoppeling biedt geen
mogelijkheid om betalingen te initiëren.

## Gegevens en opslag

De koppeling kan rekeningidentificatie, rekeninghouder, saldo, bedragen, datums,
tegenpartijen en betalingsomschrijvingen ontvangen. De toepassing bewaart deze
gegevens en de eigen rubriceringen om uitgaven over maanden te vergelijken.
Gegevens kunnen daarbij door de backend en de bestaande Google Cloud/Firestore-opslag
van Robberts Assistent worden verwerkt. Ze worden niet in de publieke broncode
opgeslagen. Enable Banking en de bank verwerken de verbinding volgens hun eigen
privacyverklaringen.

De gegevens zijn bestemd voor de rekeninghouder en door hem uitdrukkelijk
gemachtigde gebruikers. De toepassing verkoopt geen bankgegevens en gebruikt ze niet
voor advertenties. AI-functies binnen Robberts Assistent kunnen informatie die de
gebruiker voor een analyse of gesprek beschikbaar maakt via de geconfigureerde
AI-diensten verwerken. De backend bewaart banktoegangssleutels zelf en verstrekt
deze niet aan AI-diensten.

## Bewaren, intrekken en verwijderen

Transacties blijven beschikbaar voor de persoonlijke financiële historie totdat
Robbert ze verwijdert of om verwijdering verzoekt. Toestemming voor toekomstige
banktoegang kan bij de bank of via Enable Banking worden ingetrokken. Intrekken
verwijdert eerder opgehaalde gegevens niet automatisch. Vragen over inzage,
correctie of verwijdering kunnen naar het bovenstaande contactadres.

De toepassing is in ontwikkeling. Nieuwe verwerkingen worden in deze pagina
beschreven voordat ze in gebruik worden genomen.
