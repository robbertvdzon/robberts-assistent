# Factory Docs

Context voor factory-agents die aan `robberts-assistent` werken. **Lees eerst de root
[`CLAUDE.md`](../../CLAUDE.md)** — dat geeft het volledige overzicht (wat de app is, de
backend-modules, de koppelingen, de apps en de deploy). Deze map verdiept per onderwerp.

De repo bevat één Kotlin/Spring-Boot-backend (`robberts-assistent-backend/`) en vier
Flutter/Android-apps (`robberts_assistent/`, `groentetuin/`, `notities/`, `wind/`). De
backend is een Spring Modulith met skills (notes, reminders, gardenchat/moestuin-chat,
google-agenda/docs, wind via de chat-assistent, summary), aangesproken door de apps en door
een OpenAI-agent met `@Tool`-functies. Elke externe koppeling heeft een stub/in-memory
fallback, dus alles bouwt en test groen zónder secrets.

## Index

- `development.md`: lokaal bouwen, testen en ontwikkelconventies.
- `functional-spec.md`: functionele afspraken en gebruikersgedrag per skill/app.
- `technical-spec.md`: architectuur (Modulith-modules, ports, koppelingen), stack, conventies.
- `deployment.md`: deploy-flow (GitOps/ArgoCD/Cloudflare/Sealed Secrets) en machine-leesbare config.
- `secrets-local.md`: lokale secrets en waar die vandaan komen.
- `agents/`: rol-specifieke instructies voor factory-agents.

Aanvullend buiten deze map: `docs/foundation-couplings.md` (ontwerp + implementatieplan van
de koppelingen), `docs/setup-guide-details.md` (console-setup), `docs/robbert-todo.md` (status
+ handmatige stappen), `docs/stories/` (per-story worklogs).
