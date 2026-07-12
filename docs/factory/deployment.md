---
default_base_branch: main
branch_prefix: ai/
preview_url_template: "https://robberts-assistent-frontend-robberts-assistent-pr-{pr_num}.apps.sno.lab.vdzon.com"
preview_namespace_template: "robberts-assistent-pr-{pr_num}"
preview_db_secret_recipe: |
  # Geen apart ophaal-commando nodig: RA_DATABASE_URL wordt door Reflector automatisch
  # gemirrord vanuit de robberts-assistent-namespace naar robberts-assistent-pr-*.
  # LET OP: dit is momenteel dezelfde Neon-database als productie (notities worden dus
  # gedeeld tussen preview en prod) — nog geen aparte preview-database-branch zoals bij
  # personal-feed.
---

# Deployment

Er zijn drie Flutter-apps (wind, robberts_assistent, notities) en één backend
(robberts-assistent-backend). Backend + robberts_assistent draaien als web-app op
OpenShift; alle drie de apps zijn ook als Android-APK te installeren.

## Distributie via GitHub Release (APK's)

Per app een eigen workflow (`.github/workflows/build-apk.yml`,
`robberts-assistent-apk.yml`, `notities-apk.yml`) die bij elke relevante push naar
`main` een release-APK bouwt en publiceert als GitHub Release
(`softprops/action-gh-release`):

- **Vaste tag per app** (`wind-latest`, `robberts-assistent-latest`,
  `notities-latest`) — elke build overschrijft dezelfde release, dus de downloadlink
  verandert nooit.
- Asset: `app-release.apk`. `robberts_assistent`/`notities` zijn getekend met een
  vaste release-keystore (`ANDROID_KEYSTORE_BASE64`/`_PASSWORD`/`ANDROID_KEY_ALIAS`
  als repo-secrets) — nodig omdat Google Sign-In aan een vaste SHA-1 gekoppeld is;
  `wind` gebruikt nog de debug-key (geen Google-login nodig).
- Installeren: download `app-release.apk` van de release en sta op het
  Android-toestel "installeren uit onbekende bron" toe.

## OpenShift-deploy (backend + robberts_assistent-web)

GitOps via ArgoCD (`robberts-infrastructure` repo, `manifests/root-app/apps/
robberts-assistent-application.yaml`), gevoed door `deploy/base/` in deze repo.
CI (`.github/workflows/backend-image.yml`, `frontend-image.yml`) bouwt/pusht images
naar `ghcr.io` en bumpt `deploy/base/kustomization.yaml` — ArgoCD synct vanzelf.

- **Productie**: namespace `robberts-assistent`, `https://robberts-assistent.vdzonsoftware.nl`
  (publiek via een gedeelde Cloudflare Tunnel — nieuwe hostnames moeten handmatig in
  Cloudflare Zero Trust geregistreerd worden, niet via GitOps). Google-login vereist
  (`RA_ALLOWED_EMAILS`-allowlist in de sealed secret).
- **Branch-preview**: ArgoCD `ApplicationSet` (`robberts-assistent-previews` in
  `robberts-infrastructure`) spint per open PR een eigen deploy op in namespace
  `robberts-assistent-pr-<nummer>`, met `deploy/overlays/preview` (geen vaste
  Route-host — OpenShift genereert er zelf een onder de cluster-wildcard, vandaar
  het `preview_url_template` hierboven). De backend krijgt daar
  `RA_PREVIEW_SKIP_GOOGLE_AUTH=true` en de frontend wordt gebouwd met
  `SKIP_GOOGLE_AUTH=true` (alleen op PR-builds): **geen Google-login nodig** in
  preview, ook niet in de browser — de frontend slaat het loginscherm dan zelf
  over. `API_BASE_URL` wordt nooit meegegeven (leeg = relatieve `/api/`-paden);
  de frontend-nginx proxy't die same-origin naar `robberts-assistent-backend:80`
  in dezelfde namespace, dus elke preview raakt vanzelf zijn eigen backend.
  Backend krijgt ook `RA_MOCK_AI=true`: de chat-assistent gebruikt daar een
  deterministische mock i.p.v. een echte OpenAI-call, ook al bevat de (via
  Reflector gemirrorde) secret dezelfde `RA_OPENAI_API_KEY` als productie —
  geen kosten/netwerkafhankelijkheid tijdens tester-runs.
- **Notities-app en Wind** hebben geen eigen web-deploy, alleen de APK's hierboven.

## Handmatige verificatie

De App Actions-flow van Wind ("Hey Google, vraag Wind ...") en de notificatie op een
Garmin-horloge zijn alleen op echte hardware te controleren.
