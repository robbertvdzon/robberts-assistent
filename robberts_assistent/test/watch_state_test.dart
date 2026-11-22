import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/summary_screen.dart';
import 'package:robberts_assistent/watches_screen.dart';

Watch _watch(
  String status, {
  DateTime? searchingSince,
  DateTime? foundAt,
  DateTime? acknowledgedAt,
  DateTime? failingSince,
  String note = '',
}) => Watch(
  id: status,
  title: 'Aaltjes',
  url: 'https://example.com',
  instruction: 'meld als op voorraad',
  notifyOnFound: true,
  status: status,
  statusDescription: 'Het product staat nog op uitverkocht.',
  lastCheckedAt: DateTime(2026, 11, 22, 21, 4),
  active: status != 'GEVONDEN',
  searchingSince: searchingSince,
  foundAt: foundAt,
  acknowledgedAt: acknowledgedAt,
  failingSince: failingSince,
  note: note,
);

class _Api extends ApiClient {
  List<Watch> watches = [];
  final acknowledged = <String>[];
  BriefingData? briefing;

  @override
  Future<List<Watch>> listWatches() async => watches;

  @override
  Future<List<Watch>> acknowledgeWatch(String id) async {
    acknowledged.add(id);
    watches = [
      for (final w in watches)
        w.id == id
            ? _watch(
                w.status,
                foundAt: w.foundAt,
                acknowledgedAt: DateTime(2026, 11, 22, 22),
              )
            : w,
    ];
    return watches;
  }

  @override
  Future<BriefingData> getBriefing() async => briefing!;
}

void main() {
  final now = DateTime(2026, 11, 22, 21, 30);

  test('duur in woorden', () {
    expect(watchDuration(DateTime(2026, 11, 22, 8), now), 'vandaag');
    expect(watchDuration(DateTime(2026, 11, 21, 23), now), '1 dag');
    expect(watchDuration(DateTime(2026, 11, 12), now), '10 dagen');
    expect(watchDuration(DateTime(2026, 10, 22), now), '4 weken');
    expect(watchDuration(DateTime(2026, 8, 20), now), '3 maanden');
  });

  test('stand van een zoekopdracht in woorden', () {
    final searching = watchState(
      _watch('NIET_GEVONDEN', searchingSince: DateTime(2026, 8, 20)),
      now,
    );
    expect(searching.headline, 'Nog niet gevonden');
    expect(searching.detail, contains('Zoekt al 3 maanden (sinds 20 aug).'));
    expect(searching.detail, contains('Laatst gecontroleerd: 22 nov 21:04.'));

    final found = watchState(
      _watch('GEVONDEN', foundAt: DateTime(2026, 11, 21, 10)),
      now,
    );
    expect(found.headline, 'Gevonden op 21 nov');
    expect(found.icon, Icons.check_circle);
    expect(found.detail, contains('elke ochtend een herinnering'));

    final seen = watchState(
      _watch(
        'GEVONDEN',
        foundAt: DateTime(2026, 11, 21, 10),
        acknowledgedAt: DateTime(2026, 11, 22),
      ),
      now,
    );
    expect(seen.headline, 'Gevonden op 21 nov en gezien');

    final failing = watchState(
      _watch('ONBEKEND', failingSince: DateTime(2026, 11, 19)),
      now,
    );
    expect(failing.headline, 'Controleren lukt niet');
    expect(failing.detail, contains('Mislukt al 3 dagen (sinds 19 nov).'));

    expect(
      watchState(_watch('NOG_NIET_GECONTROLEERD'), now).headline,
      'Nog niet gecontroleerd',
    );
  });

  testWidgets(
    'een vondst toont een groen vinkje en verdwijnt uit de aandacht na Gezien',
    (tester) async {
      final api = _Api()
        ..watches = [
          _watch('GEVONDEN', foundAt: DateTime(2026, 11, 21, 10)),
          _watch(
            'NIET_GEVONDEN',
            searchingSince: DateTime(2026, 11, 20),
            note: 'Het product lijkt uit het assortiment.',
          ),
        ];
      await tester.pumpWidget(MaterialApp(home: WatchesScreen(api: api)));
      await tester.pumpAndSettle();

      expect(find.text('Gevonden op 21 nov'), findsOneWidget);
      expect(find.text('Nog niet gevonden'), findsOneWidget);
      expect(
        find.text('Het product lijkt uit het assortiment.'),
        findsOneWidget,
      );
      // Geen 'let op' meer bij een zoekopdracht die gewoon nog loopt.
      expect(find.text('let op'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('gezien-GEVONDEN')));
      await tester.pumpAndSettle();

      expect(api.acknowledged, ['GEVONDEN']);
      expect(find.text('Gevonden op 21 nov en gezien'), findsOneWidget);
      expect(find.byKey(const ValueKey('gezien-GEVONDEN')), findsNothing);
    },
  );

  testWidgets('Vandaag toont onder de tegels waarom iets let op of niet is', (
    tester,
  ) async {
    final api = _Api()
      ..briefing = BriefingData(
        updatedAt: DateTime(2026, 11, 22, 21),
        sections: const [
          BriefingSection(
            key: 'kite',
            title: 'Kiten',
            text: 'Dag: 6 kn',
            items: [],
            status: BriefingStatus.niet,
            tileLabel: '6 kn ONO',
            statusReason:
                'De wind is niet aanlandig (ONO) en te weinig wind (6 kn, kiten kan vanaf 15)',
          ),
          BriefingSection(
            key: 'beach',
            title: 'Strandfietsen',
            text: 'Dag: goed',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: 'goed',
          ),
        ],
      );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: SummaryScreen(api: api)),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Kiten: De wind is niet aanlandig (ONO) en te weinig wind (6 kn, kiten kan vanaf 15)',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('status-reason-beach')), findsNothing);
  });
}
