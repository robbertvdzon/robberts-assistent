import 'dart:async';
import 'dart:ui' show SemanticsAction;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/summary_screen.dart';

final _fixedUpdatedAt = DateTime(2026, 7, 21, 17, 30);

class _FakeApiClient extends ApiClient {
  List<BriefingSection> sections = [];
  DateTime updatedAt = _fixedUpdatedAt;
  final calledActions = <BriefingAction>[];
  int refreshCalls = 0;
  bool refreshShouldThrow = false;
  // Zonder completer lost refreshBriefing meteen op — voor de spinner-test moet de aanroeper zelf
  // kunnen bepalen wanneer de refresh 'klaar' is, zodat de laad-state observeerbaar is.
  Completer<BriefingData>? refreshCompleter;

  @override
  Future<BriefingData> getBriefing() async =>
      BriefingData(sections: sections, updatedAt: updatedAt);

  @override
  Future<BriefingData> refreshBriefing() {
    refreshCalls++;
    if (refreshShouldThrow) return Future.error(Exception('ververs-fout'));
    final completer = refreshCompleter;
    if (completer != null) return completer.future;
    return Future.value(BriefingData(sections: sections, updatedAt: updatedAt));
  }

  @override
  Future<void> runBriefingAction(BriefingAction action) async {
    calledActions.add(action);
  }
}

/// SummaryScreen heeft (net als in de app) geen eigen Scaffold — die zit op HomeScreen-niveau —
/// maar de reload-knop toont een SnackBar bij een fout, wat een omringende Scaffold vereist.
Widget _wrap(ApiClient api) => MaterialApp(
  home: Scaffold(body: SummaryScreen(api: api)),
);

void main() {
  test('briefing-JSON zonder of met onbekende status blijft compatibel', () {
    final oldSection = BriefingSection.fromJson({
      'key': 'agenda',
      'title': 'Agenda',
      'text': 'Geen afspraken.',
    });
    final unknownSection = BriefingSection.fromJson({
      'key': 'later',
      'title': 'Later',
      'text': 'Details',
      'status': 'ONBEKEND',
      'tileLabel': 'waarde',
    });

    expect(oldSection.status, isNull);
    expect(oldSection.tileLabel, isNull);
    expect(unknownSection.status, isNull);
  });

  test('briefing-JSON leest alle afgesproken statussen', () {
    BriefingSection parse(String status) => BriefingSection.fromJson({
      'key': 'status',
      'title': 'Status',
      'text': 'Details',
      'status': status,
      'tileLabel': 'waarde',
    });

    expect(parse('GOED').status, BriefingStatus.goed);
    expect(parse('LET_OP').status, BriefingStatus.letOp);
    expect(parse('NIET').status, BriefingStatus.niet);
  });

  testWidgets('toont de titel en tekst van elke briefingsectie', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'kite',
          title: 'Kiten',
          text: 'Morgen: 🟢 24kn',
          items: [],
        ),
        BriefingSection(
          key: 'beach',
          title: 'Strandfietsen',
          text: 'Morgen: 🟢 (10 kn, droog, laagwater om 08:00)',
          items: [],
        ),
        BriefingSection(
          key: 'moestuin',
          title: 'Moestuin',
          text: 'Alles goed.',
          items: [],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('KITEN'), findsOneWidget);
    expect(find.text('STRANDFIETSEN'), findsOneWidget);
    expect(find.text('MOESTUIN'), findsOneWidget);
    expect(find.text('Morgen:'), findsNWidgets(2));
    expect(find.text('goed'), findsNWidgets(2));
    expect(find.text('24kn'), findsOneWidget);
    expect(find.text('(10 kn, droog, laagwater om 08:00)'), findsOneWidget);
    expect(find.textContaining('🟢'), findsNothing);
    expect(find.text('Alles goed.'), findsOneWidget);
    expect(find.byKey(const ValueKey('status-tile-row')), findsNothing);
  });

  testWidgets(
    'één geldige statustegel vult de rij en heeft toegankelijke semantiek',
    (tester) async {
      final api = _FakeApiClient()
        ..sections = const [
          BriefingSection(
            key: 'kite',
            title: 'Kiten',
            text: 'Volledige kite-details.',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: '24 kn W',
          ),
        ];

      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      final rowWidth = tester
          .getSize(find.byKey(const ValueKey('status-tile-row')))
          .width;
      final tileWidth = tester
          .getSize(find.byKey(const ValueKey('status-tile-kite')))
          .width;
      expect(tileWidth, closeTo(rowWidth, 0.01));
      final semanticsFinder = find.semantics.byLabel('goed, Kiten, 24 kn W');
      expect(semanticsFinder, findsOne);
      final semanticsNode = semanticsFinder.evaluate().single;
      expect(
        semanticsNode.getSemanticsData().hasAction(SemanticsAction.tap),
        isTrue,
      );
      expect(find.byIcon(Icons.air), findsOneWidget);
      expect(find.text('Volledige kite-details.'), findsNothing);
      expect(find.text('KITEN'), findsNothing);

      tester.semantics.tap(semanticsFinder);
      await tester.pumpAndSettle();

      expect(find.text('Volledige kite-details.'), findsOneWidget);
      expect(find.text('KITEN'), findsOneWidget);
    },
  );

  testWidgets(
    'drie tegels zijn even breed, kappen lange tekst af en gebruiken exacte kleuren',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(420, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = _FakeApiClient()
        ..sections = const [
          BriefingSection(
            key: 'kite',
            title: 'Een buitengewoon lange titel voor kiten',
            text: 'Kite-details',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: 'Een buitengewoon lang groen tegellabel',
          ),
          BriefingSection(
            key: 'beach',
            title: 'Strandfietsen met een erg lange titel',
            text: 'Strandfiets-details',
            items: [],
            status: BriefingStatus.letOp,
            tileLabel: 'let op met een buitengewoon lange toelichting',
          ),
          BriefingSection(
            key: 'waste',
            title: 'Afval met een erg lange titel',
            text: 'Afvaldetails',
            items: [],
            status: BriefingStatus.niet,
            tileLabel: 'gft + pbd + papier + rest',
          ),
        ];

      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      final widths = ['kite', 'beach', 'waste']
          .map(
            (key) =>
                tester.getSize(find.byKey(ValueKey('status-tile-$key'))).width,
          )
          .toList();
      expect(widths[0], closeTo(widths[1], 0.01));
      expect(widths[1], closeTo(widths[2], 0.01));
      expect(tester.takeException(), isNull);
      expect(_dotColor(tester, 'kite'), const Color(0xFF0CA30C));
      expect(_dotColor(tester, 'beach'), const Color(0xFFFAB219));
      expect(_dotColor(tester, 'waste'), const Color(0xFFD03B3B));
      expect(find.byIcon(Icons.air), findsOneWidget);
      expect(find.byIcon(Icons.pedal_bike), findsOneWidget);
      expect(find.byIcon(Icons.recycling), findsOneWidget);
    },
  );

  testWidgets(
    'alleen de eerste drie statussen zijn tegels en een vierde blijft kaart',
    (tester) async {
      final api = _FakeApiClient()
        ..sections = const [
          BriefingSection(
            key: 'een',
            title: 'Een',
            text: 'Detail een',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: '1',
          ),
          BriefingSection(
            key: 'twee',
            title: 'Twee',
            text: 'Detail twee',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: '2',
          ),
          BriefingSection(
            key: 'drie',
            title: 'Drie',
            text: 'Detail drie',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: '3',
          ),
          BriefingSection(
            key: 'vier',
            title: 'Vier',
            text: 'Detail vier',
            items: [],
            status: BriefingStatus.goed,
            tileLabel: '4',
          ),
        ];

      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      expect(find.byKey(const ValueKey('status-tile-een')), findsOneWidget);
      expect(find.byKey(const ValueKey('status-tile-twee')), findsOneWidget);
      expect(find.byKey(const ValueKey('status-tile-drie')), findsOneWidget);
      expect(find.byKey(const ValueKey('status-tile-vier')), findsNothing);
      expect(find.text('VIER'), findsOneWidget);
      expect(find.text('Detail vier'), findsOneWidget);
    },
  );

  testWidgets('tik opent één detail tegelijk zonder permanente dubbele kaart', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'kite',
          title: 'Kiten',
          text: 'Eerste detail.',
          items: [],
          status: BriefingStatus.goed,
          tileLabel: '24 kn W',
        ),
        BriefingSection(
          key: 'beach',
          title: 'Strandfietsen',
          text: 'Tweede detail.',
          items: [],
          status: BriefingStatus.letOp,
          tileLabel: 'let op',
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Eerste detail.'), findsNothing);
    expect(find.text('Tweede detail.'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('status-tile-kite')));
    await tester.pumpAndSettle();
    expect(find.text('Eerste detail.'), findsOneWidget);
    expect(find.text('Tweede detail.'), findsNothing);
    expect(find.text('KITEN'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('status-tile-beach')));
    await tester.pumpAndSettle();
    expect(find.text('Eerste detail.'), findsNothing);
    expect(find.text('Tweede detail.'), findsOneWidget);
    expect(find.text('KITEN'), findsNothing);
    expect(find.text('STRANDFIETSEN'), findsOneWidget);
  });

  testWidgets('toont de updatedAt-tijdstip van de (gecachete) briefing', (
    tester,
  ) async {
    final api = _FakeApiClient()..updatedAt = DateTime(2026, 7, 21, 9, 5);

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Bijgewerkt om 09:05'), findsOneWidget);
  });

  testWidgets(
    'reload-knop roept refreshBriefing aan en toont tijdens het laden een spinner',
    (tester) async {
      final completer = Completer<BriefingData>();
      final api = _FakeApiClient()..refreshCompleter = completer;

      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      expect(find.byIcon(Icons.refresh), findsOneWidget);

      await tester.tap(find.byIcon(Icons.refresh));
      await tester.pump();

      // Tijdens het laden: geen indrukbare refresh-knop meer, wel een spinner.
      expect(find.byIcon(Icons.refresh), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsWidgets);
      expect(api.refreshCalls, 1);

      completer.complete(
        BriefingData(sections: api.sections, updatedAt: api.updatedAt),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.refresh), findsOneWidget);
    },
  );

  testWidgets('reload-knop toont een foutmelding als refreshen mislukt', (
    tester,
  ) async {
    final api = _FakeApiClient()..refreshShouldThrow = true;

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    await tester.tap(find.byIcon(Icons.refresh));
    await tester.pumpAndSettle();

    expect(find.textContaining('Verversen mislukt'), findsOneWidget);
    expect(find.byIcon(Icons.refresh), findsOneWidget);
  });

  testWidgets(
    'een item met imageUrl rendert een afbeelding i.p.v. platte tekst',
    (tester) async {
      final api = _FakeApiClient()
        ..sections = const [
          BriefingSection(
            key: 'weather-map',
            title: 'Weerkaart',
            text: '',
            items: [
              BriefingItem(
                text: 'Ochtend: 24 kn (ZW), regen',
                imageUrl: '/api/v1/briefing/weather-map/ochtend',
              ),
            ],
          ),
        ];

      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      expect(find.text('WEERKAART'), findsOneWidget);
      expect(find.text('Ochtend: 24 kn (ZW), regen'), findsOneWidget);
      final image = tester.widget<Image>(find.byType(Image));
      final provider = image.image as NetworkImage;
      expect(
        provider.url,
        endsWith(
          '/api/v1/briefing/weather-map/ochtend?v=${_fixedUpdatedAt.millisecondsSinceEpoch ~/ 1000}',
        ),
      );
    },
  );

  testWidgets(
    'een andere updatedAt na refresh geeft een andere cache-bust-query-param',
    (tester) async {
      final refreshedAt = _fixedUpdatedAt.add(const Duration(minutes: 5));
      final api = _FakeApiClient()
        ..sections = const [
          BriefingSection(
            key: 'weather-map',
            title: 'Weerkaart',
            text: '',
            items: [
              BriefingItem(
                text: 'Ochtend: 24 kn (ZW), regen',
                imageUrl: '/api/v1/briefing/weather-map/morgen',
              ),
            ],
          ),
        ]
        ..refreshCompleter = null;
      api.updatedAt = _fixedUpdatedAt;

      await tester.pumpWidget(_wrap(api));
      await tester.pump();

      final beforeUrl =
          (tester.widget<Image>(find.byType(Image)).image as NetworkImage).url;

      api.updatedAt = refreshedAt;
      await tester.tap(find.byIcon(Icons.refresh));
      await tester.pumpAndSettle();

      final afterUrl =
          (tester.widget<Image>(find.byType(Image)).image as NetworkImage).url;

      expect(beforeUrl, isNot(equals(afterUrl)));
      expect(
        afterUrl,
        endsWith('?v=${refreshedAt.millisecondsSinceEpoch ~/ 1000}'),
      );
    },
  );

  testWidgets('afspraak zonder reminder toont een werkende actieknop', (
    tester,
  ) async {
    final action = const BriefingAction(
      label: 'Reminder 1 uur van tevoren aanmaken',
      endpoint: '/api/v1/briefing/agenda-reminder',
      payload: {'summary': 'Standup', 'startAt': '2026-07-22T08:00:00Z'},
    );
    final api = _FakeApiClient()
      ..sections = [
        BriefingSection(
          key: 'agenda',
          title: 'Agenda (7 dagen)',
          text: 'Standup (geen reminder)',
          items: [
            BriefingItem(
              text: 'Ma 22 jul 08:00 — Standup (⚠️ nog geen reminder)',
              action: action,
            ),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Reminder 1 uur van tevoren aanmaken'), findsOneWidget);

    await tester.tap(find.text('Reminder 1 uur van tevoren aanmaken'));
    await tester.pump();

    expect(api.calledActions, [action]);
  });

  testWidgets('afspraak mét reminder toont geen actieknop', (tester) async {
    final api = _FakeApiClient()
      ..sections = [
        const BriefingSection(
          key: 'agenda',
          title: 'Agenda (7 dagen)',
          text: 'Tandarts (reminder staat)',
          items: [
            BriefingItem(text: 'Ma 22 jul 08:00 — Tandarts (✅ reminder staat)'),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.byType(TextButton), findsNothing);
  });

  testWidgets('meerregelige kite-tekst wordt per regel apart weergegeven', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'kite',
          title: 'Kiten',
          text: 'Morgen: 🟢 24kn NW\nOvermorgen: 🟡 12kn Z',
          items: [],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('Morgen:'), findsOneWidget);
    expect(find.text('Overmorgen:'), findsOneWidget);
    expect(find.text('goed'), findsOneWidget);
    expect(find.text('let op'), findsOneWidget);
    expect(find.text('24kn NW'), findsOneWidget);
    expect(find.text('12kn Z'), findsOneWidget);
    expect(
      find.text('Morgen: 🟢 24kn NW\nOvermorgen: 🟡 12kn Z'),
      findsNothing,
    );
  });

  testWidgets('de systeemstatus-sectie wordt niet getoond op de Upcoming-tab', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..sections = const [
        BriefingSection(
          key: 'kite',
          title: 'Kiten',
          text: 'Morgen: 🟢 24kn',
          items: [],
        ),
        BriefingSection(
          key: 'system-status',
          title: 'Systeemstatus',
          text: 'Alles is in orde.',
          items: [
            BriefingItem(
              text: 'huidig vermogen=100 W.',
              heading: 'Zonnepanelen',
            ),
          ],
        ),
      ];

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.text('KITEN'), findsOneWidget);
    expect(find.text('SYSTEEMSTATUS'), findsNothing);
    expect(find.text('huidig vermogen=100 W.'), findsNothing);
  });

  testWidgets('toont een foutmelding als het ophalen van de briefing faalt', (
    tester,
  ) async {
    final api = _FailingApiClient();

    await tester.pumpWidget(_wrap(api));
    await tester.pump();

    expect(find.textContaining('kapot'), findsOneWidget);
  });
}

Color? _dotColor(WidgetTester tester, String key) {
  final container = tester.widget<Container>(
    find.byKey(ValueKey('status-dot-$key')),
  );
  return (container.decoration as BoxDecoration).color;
}

class _FailingApiClient extends ApiClient {
  @override
  Future<BriefingData> getBriefing() async => throw Exception('kapot');
}
