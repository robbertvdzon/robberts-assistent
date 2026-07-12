# Reviewer Instructions

- Review de wijziging tegen de story, `technical-spec.md` en bestaande
  repo-conventies.
- Je mag `docs/stories/worklog/<issue-key>-worklog.md` bijwerken met
  review-notities.
- Geef concrete feedback met reproduceerbare stappen of file/line-context.
- Vraag geen productkeuzes aan de gebruiker; schrijf blokkerende technische
  problemen in het YouTrack `Error`-veld.
- Meerdere `*-worklog.md`-bestanden onder één story zijn normaal: de story én
  elke subtaak houden hun eigen worklog bij. Behandel dat NIET als dubbel werk of
  scope-overlap. De (sub)taak die je reviewt staat in `.task.md` (met de
  parent-story); bepaal de scope daaruit, niet uit het aantal worklogs.
- **Flutter-tests zijn structureel niet uitvoerbaar in deze sandbox** (arm64,
  Google publiceert geen linux-arm64 Flutter-SDK; geen qemu/binfmt/docker/root
  om de x64-SDK te draaien) — en de CI-workflows die `flutter test` draaien
  (`build-apk.yml`, `robberts-assistent-apk.yml`, `notities-apk.yml`) triggeren
  alleen op push naar `main`, dus er is ook geen PR-branch-CI om op terug te
  vallen. Ontbrekend `flutter test`-bewijs is voor een wijziging die uitsluitend
  Dart/Flutter-code raakt daarom GEEN blocker — keur zo'n wijziging goed op basis
  van een grondige handmatige code-review tegen de story/acceptatiecriteria (en,
  als de story ook backend-code raakt, groene `mvn test`-resultaten daarvoor).
  Vermeld expliciet dat je dit accepteert i.p.v. blanco te reviewen, zodat het
  traceerbaar blijft. Dit geldt niet voor de native Kotlin-kant (`./gradlew test`
  draait wél in deze sandbox) of de backend.
- Uncommitted changes in de werktree zijn het te reviewen werk; de factory commit
  en pusht ze na de review. Dat is normaal en geen blocker.
