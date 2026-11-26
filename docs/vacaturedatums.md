# Datums bij vacatures

Elke vacaturekaart toont **Gevonden op** met lokale datum en tijd, en **Geplaatst op** met
de publicatiedatum van de bron. Zonder bekende plaatsingsdatum staat er **Publicatiedatum
onbekend**. De vinddatum is het moment waarop deze assistent de vacature voor het eerst opsloeg;
het is niet de datum waarop de vacature online kwam.

`JobLead.publishedOn` en `FoundJob.publishedOn` zijn optionele kalenderdatums (`LocalDate`).
Firestore bewaart `publishedOn` als ISO-datumtekst, naast het bestaande `foundAt` in epoch-millis.
Documenten zonder het nieuwe veld blijven leesbaar. De API geeft `publishedOn` terug als
`YYYY-MM-DD` of null. Een oordeel aanpassen behoudt beide datums.

De zoekronde vraagt de AI de oorspronkelijke plaatsingsdatum op de vacaturepagina te controleren,
bijvoorbeeld `datePosted`. Geen datum verzinnen of vinddatum, wijzigingsdatum, sluitingsdatum of
copyrightjaar overnemen. Ontbrekende, ongeldige en toekomstige datums worden onbekend.

Per zoekronde worden daarnaast maximaal tien bestaande, niet afgewezen vacatures zonder datum
gecontroleerd. Een teruggevonden datum wordt alleen bij dezelfde genormaliseerde URL aangevuld.
Daarbij blijven id, vinddatum, oordeel en reden behouden; dit telt niet als nieuwe vacature en
veroorzaakt geen push. Een al bekende publicatiedatum wordt niet overschreven door een latere
herplaatsingsdatum. Aanvullen gebeurt bij volgende normale zoekrondes of via **Nu zoeken**.
