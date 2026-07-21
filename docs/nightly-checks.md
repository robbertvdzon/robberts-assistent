# Nightly checks

Generiek framework voor achtergrondchecks die 's nachts/'s ochtends (of vaker) draaien, hun
resultaat bewaren (met historie, niet alleen het laatste resultaat), en zowel in de app
(scherm **Nachtchecks**, bereikbaar via de 'Meer'-tab) als in de dagelijkse samenvatting te
zien zijn. Zie ook [CLAUDE.md](../CLAUDE.md) §4/§5 voor hoe dit in de modulestructuur past.

## Architectuur

- **SPI**: `nightlychecks.NightlyCheck` — elke check is een `@Component` met `id`/`name`/
  `description`/`cronSchedule`/`run(): CheckResult`. Net als `couplings.CouplingProbe` pikt het
  framework (`NightlyCheckScheduler`, via Spring's `List<NightlyCheck>`-injectie) elke
  implementatie automatisch op — een nieuwe check toevoegen betekent alleen een nieuwe module +
  een nieuwe `NightlyCheck`-`@Component`, geen wijziging in het framework zelf.
- **Eigen schema per check**: `NightlyCheckScheduler` gebruikt Spring's `SchedulingConfigurer` +
  `CronTrigger` per check (niet één gezamenlijke `@Scheduled`-methode), zodat de ene check elk uur
  kan draaien en de andere maar één keer per ochtend.
- **Opslag met historie**: `NightlyCheckRepository` (Firestore-collectie `nightly-check-runs`,
  in-memory fallback) bewaart per check de laatste 100 uitvoeringen, niet alleen de nieuwste.
- **API**: `GET /api/v1/nightly-checks` (status + laatste resultaat per check),
  `GET /api/v1/nightly-checks/{id}/history` (historie), `POST /api/v1/nightly-checks/{id}/run`
  (handmatig opnieuw draaien — de "herstart deze check"-knop in de app).
- **App**: scherm **Nachtchecks** (`nightly_checks_screen.dart`), bereikbaar via de 'Meer'-tab
  (`more_screen.dart`) in de bottom-nav, toont de lijst + status, met een "nu draaien"-knop en
  tikken voor de volledige historie.
- **Samenvatting**: `SummaryService` (`GET /api/v1/summary`) neemt de nightly-check-resultaten
  automatisch mee — een nieuwe check verschijnt daar vanzelf, geen wijziging in `SummaryService`
  nodig. Sinds de Morgen-briefing (SF-1163, zie root `CLAUDE.md` §9) is dit endpoint niet meer
  aangesloten op een app-scherm — de "Samenvatting"-tab is de "Morgen"-tab geworden, gevoed door
  de nieuwe `briefing`-module. Sinds SF-1164 heeft de Morgen-briefing een eigen systeem-
  checkrapport-sectie (`briefing.SystemStatusSectionProvider`); die gebruikt bewust een **live**
  check (`OpenShiftClient.clusterHealth()` rechtstreeks) i.p.v. de hier bewaarde
  `NightlyCheckRepository`-historie, omdat die historie t.o.v. het briefing-moment verouderd zou
  kunnen zijn. Zie root `CLAUDE.md` §4 (`briefing`-rij) voor details.

## Eerste check: OpenShift-gezondheid

Module `openshift` — `OpenShiftClient.clusterHealth()`: gedegradeerde ClusterOperators +
of er een platform-update beschikbaar is (`ClusterVersion.status.availableUpdates`). Draait
elke ochtend 07:00 (`OpenShiftHealthNightlyCheck.cronSchedule`). Ook als `@Tool`
(`OpenShiftTools.getOpenShiftHealth`) zodat je het via de chat kunt vragen.

Gebruikt de **in-cluster ServiceAccount-token** van de pod zelf (token + CA-cert die Kubernetes
automatisch op elke pod mount) — geen los secret. Gate op de expliciete vlag
`RA_OPENSHIFT_HEALTH_ENABLED` (i.p.v. keyless-altijd-aan) omdat de benodigde RBAC nog niet in de
cluster bestaat: zonder RBAC zou de koppeling anders continu op 403-fouten stuk lopen.

### TODO — RBAC toepassen (nog niet gedaan, Robbert is er even niet)

Twee stappen, in twee repo's. **Niet aangepast in deze sessie** omdat er niet naar
`robberts-infrastructure` gepusht kon worden — volgende sessie oppakken.

**1. In `robberts-infrastructure`** (cluster-scoped RBAC, zelfde plek/patroon als de bestaande
`claude-agent`-ServiceAccount in `manifests/agent-access/`, zie
[access-and-credentials.md](../../robberts-infrastructure/docs/access-and-credentials.md)):

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: robberts-assistent-openshift-health
rules:
  - apiGroups: ["config.openshift.io"]
    resources: ["clusterversions", "clusteroperators"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: robberts-assistent-openshift-health
subjects:
  - kind: ServiceAccount
    name: robberts-assistent-backend
    namespace: robberts-assistent
roleRef:
  kind: ClusterRole
  name: robberts-assistent-openshift-health
  apiGroup: rbac.authorization.k8s.io
```

**2. In `robberts-assistent`** (dit repo, `deploy/base/`): een nieuwe `ServiceAccount` +
`serviceAccountName: robberts-assistent-backend` op de bestaande backend-`Deployment` (nu draait
die impliciet op de `default`-ServiceAccount van de namespace, die nergens aan gebonden is):

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: robberts-assistent-backend
  namespace: robberts-assistent
```

**3. Daarna**: `RA_OPENSHIFT_HEALTH_ENABLED=true` in `secrets.env` zetten, sealen (zie
`deploy/seal-secrets.sh` — vergeet niet de nieuwe key aan de allowlist in dat script toe te
voegen, zelfde valkuil als bij eerdere koppelingen), en testen:

```
oc auth can-i list clusteroperators --as=system:serviceaccount:robberts-assistent:robberts-assistent-backend
```

**[TEST]** via de assistent: "is de cluster nog gezond?" → een echt antwoord i.p.v. de
stub-tekst (`0.0.0-stub`). Of via het Koppelingen-scherm: "OpenShift" op **echt**.

## Toekomstige checks (ideeën, nog niet gebouwd)

Elke onderstaande check hergebruikt een bestaande koppeling — het is dus vooral een nieuwe
`NightlyCheck`-`@Component` in de bestaande module, geen nieuwe infrastructuur:

- **Moet de tuin water hebben** — `weather.WeatherClient` (regenkans komende dag) +
  eventueel een drempelwaarde/laatste-keer-water-gegeven-state.
- **Kan ik morgen kiten** — `tides.TideClient` (getij) + `weather.WeatherClient` (wind, via
  `WindTools`'s Open-Meteo-bron) gecombineerd tot een simpel ja/nee-oordeel.
- **Doen de zonnepanelen het nog goed** — nog geen koppeling; vereist eerst uitzoeken welke
  monitoring-API/toegang de omvormer/monitoringportal biedt (nieuwe module + eventueel nieuw
  secret, net als Strava/Automower).
- **Agenda-reminder komende week** — `google.CalendarClient` (bestaat al, read-only): kijk een
  week vooruit, en zet voor afspraken zonder eigen reminder er automatisch een via
  `reminders.RemindersService`.

## Uitbreidingsideeën voor de OpenShift-check zelf

Nog **niet** gebouwd (bewust, zie sessie-overleg): geheugengebruik, SSD-gebruik (`/dev/sda`,
240GB) en externe-HDD-gebruik (`/var/mnt/external-hdd`, zie
[architecture.md](../../robberts-infrastructure/docs/architecture.md)). Er is geen
Prometheus/node-exporter/metrics-server op de cluster, dus de Kubernetes-API alleen kan dit niet
geven. Als dit later gewenst is: een lichte node-exporter (of een kleine `df`-sidecar met
hostPath-mount) toevoegen in `robberts-infrastructure` is de kleinste stap — geen volledige
Prometheus-stack nodig voor alleen een paar puntmetingen per ochtend.
