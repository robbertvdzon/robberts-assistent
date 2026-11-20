import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/jobs_screen.dart';

class _Api extends ApiClient {
  JobOverview jobs = JobOverview(
    description: 'Kotlin bij Heemskerk',
    lastRunNote: '2 nieuwe vacatures gevonden.',
    leads: [
      JobLead(
        id: 'a',
        title: 'AI engineer',
        company: 'Tata Steel',
        location: 'IJmuiden',
        foundAt: DateTime(2026, 11, 20, 8, 30),
        summary: 'Agents bouwen.',
      ),
      JobLead(
        id: 'b',
        title: 'Kotlin dev',
        company: 'Acme',
        location: 'Haarlem',
        foundAt: DateTime(2026, 11, 20, 8, 30),
      ),
    ],
  );
  final calls = <String>[];

  @override
  Future<JobOverview> getJobs() async => jobs;

  @override
  Future<JobOverview> rateJob(String id, String verdict, String reason) async {
    calls.add('$id:$verdict:$reason');
    jobs = JobOverview(
      description: jobs.description,
      lastRunNote: jobs.lastRunNote,
      blockedCompanies: const ['Tata Steel'],
      leads: [
        for (final l in jobs.leads)
          l.id == id
              ? JobLead(
                  id: l.id,
                  title: l.title,
                  company: l.company,
                  foundAt: l.foundAt,
                  verdict: verdict,
                  reason: reason,
                )
              : l,
      ],
    );
    return jobs;
  }

  @override
  Future<JobOverview> runJobSearch() async {
    calls.add('zoeken');
    return JobOverview(
      description: jobs.description,
      running: true,
      leads: jobs.leads,
    );
  }

  @override
  Future<JobOverview> saveJobSettings({
    required String description,
    required String preferences,
    required List<String> blockedCompanies,
  }) async {
    calls.add('instellingen:$description:${blockedCompanies.join(',')}');
    return JobOverview(
      description: description,
      preferences: preferences,
      blockedCompanies: blockedCompanies,
      leads: jobs.leads,
    );
  }
}

Future<void> _pump(WidgetTester tester, _Api api) async {
  tester.view.physicalSize = const Size(1080, 3000);
  tester.view.devicePixelRatio = 2.5;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      home: JobsScreen(api: api, pollInterval: const Duration(hours: 1)),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'niet interessant met reden verplaatst de vacature naar zijn groep',
    (tester) async {
      final api = _Api();
      await _pump(tester, api);

      expect(find.text('Nieuw (2)'), findsOneWidget);
      expect(find.text('Tata Steel · IJmuiden'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('niet-a')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('vacature-reden')),
        'Tata vind ik niet interessant',
      );
      await tester.tap(find.text('Opslaan'));
      await tester.pumpAndSettle();

      expect(api.calls, ['a:NIET:Tata vind ik niet interessant']);
      expect(find.text('Nieuw (1)'), findsOneWidget);
      expect(find.text('AI engineer'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('groep-NIET')));
      await tester.pumpAndSettle();
      expect(
        find.text('Jouw reden: Tata vind ik niet interessant'),
        findsOneWidget,
      );
      // Een afgewezen vacature kan alsnog interessant worden, niet nog eens afgewezen.
      expect(find.byKey(const ValueKey('interessant-a')), findsOneWidget);
      expect(find.byKey(const ValueKey('niet-a')), findsNothing);
    },
  );

  testWidgets('nu zoeken toont dat de ronde loopt', (tester) async {
    final api = _Api();
    await _pump(tester, api);

    await tester.tap(find.byKey(const ValueKey('nu-zoeken')));
    await tester.pump();
    expect(api.calls, ['zoeken']);
    expect(
      find.text('Bezig met zoeken; dit duurt een paar minuten.'),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('nu-zoeken')), findsNothing);
    // De wachtende controle opruimen.
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('zoekvraag en bedrijvenlijst zijn zelf aan te passen', (
    tester,
  ) async {
    final api = _Api();
    await _pump(tester, api);

    await tester.tap(find.byTooltip('Zoekvraag en voorkeuren'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('zoekvraag')),
      'Java in Alkmaar',
    );
    await tester.enterText(
      find.byKey(const ValueKey('bedrijven')),
      'Tata Steel\n\n Acme ',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Opslaan'));
    await tester.pumpAndSettle();

    expect(api.calls, ['instellingen:Java in Alkmaar:Tata Steel,Acme']);
    expect(find.text('Vacatures'), findsOneWidget);
  });
}
