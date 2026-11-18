import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/nap_screen.dart';

final _now = DateTime(2026, 11, 18, 14, 0);

class _FakeApiClient extends ApiClient {
  Nap? open;
  ({DateTime? start, DateTime? end, DateTime? alarmAt})? created;
  ({String id, DateTime? start, DateTime? end, bool clearAlarm})? updated;
  String? deleted;

  @override
  Future<NapDay> getNaps(DateTime date) async => NapDay(open: open);

  @override
  Future<Nap> createNap({
    DateTime? start,
    DateTime? end,
    DateTime? alarmAt,
  }) async {
    created = (start: start, end: end, alarmAt: alarmAt);
    return Nap(id: 'n1', start: start ?? _now, end: end, alarmAt: alarmAt);
  }

  @override
  Future<Nap> updateNap(
    String id, {
    DateTime? start,
    DateTime? end,
    bool clearAlarm = false,
  }) async {
    updated = (id: id, start: start, end: end, clearAlarm: clearAlarm);
    return Nap(id: id, start: start ?? _now, end: end);
  }

  @override
  Future<void> deleteNap(String id) async => deleted = id;
}

void main() {
  DateTime? scheduled;
  var cancelled = 0;

  setUp(() {
    scheduled = null;
    cancelled = 0;
  });

  Future<void> pump(WidgetTester tester, _FakeApiClient api, {Nap? nap}) async {
    await tester.pumpWidget(
      MaterialApp(
        home: NapScreen(
          api: api,
          nap: nap,
          now: () => _now,
          scheduleAlarm: (at) async => scheduled = at,
          cancelAlarm: () async => cancelled++,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('dutje starten met een wekker over 30 minuten', (tester) async {
    final api = _FakeApiClient();
    await pump(tester, api);

    await tester.tap(find.text('30 min'));
    await tester.pump();
    await tester.tap(find.text('Start dutje'));
    await tester.pumpAndSettle();

    expect(api.created?.start, _now);
    expect(api.created?.end, isNull);
    expect(api.created?.alarmAt, DateTime(2026, 11, 18, 14, 30));
    expect(scheduled, DateTime(2026, 11, 18, 14, 30));
  });

  testWidgets('dutje starten zonder wekker zet geen wekker', (tester) async {
    final api = _FakeApiClient();
    await pump(tester, api);

    await tester.tap(find.text('Start dutje'));
    await tester.pumpAndSettle();

    expect(api.created?.alarmAt, isNull);
    expect(scheduled, isNull);
  });

  testWidgets('afmelden een kwartier geleden zet het eind en de wekker uit', (
    tester,
  ) async {
    final api = _FakeApiClient()
      ..open = Nap(
        id: 'n1',
        start: DateTime(2026, 11, 18, 13, 10),
        alarmAt: DateTime(2026, 11, 18, 14, 10),
      );
    await pump(tester, api);

    expect(find.text('Dutje sinds 13:10'), findsOneWidget);
    expect(find.text('Wekker om 14:10'), findsOneWidget);
    await tester.tap(find.text('15 min geleden'));
    await tester.pump();
    await tester.tap(find.text('Ik ben wakker'));
    await tester.pumpAndSettle();

    expect(api.updated?.id, 'n1');
    expect(api.updated?.end, DateTime(2026, 11, 18, 13, 45));
    expect(api.updated?.clearAlarm, isTrue);
    expect(cancelled, 1);
  });

  testWidgets('een eind voor het begin wordt geweigerd', (tester) async {
    final api = _FakeApiClient()
      ..open = Nap(id: 'n1', start: DateTime(2026, 11, 18, 13, 50));
    await pump(tester, api);

    await tester.tap(find.text('30 min geleden'));
    await tester.pump();
    await tester.tap(find.text('Ik ben wakker'));
    await tester.pump();

    expect(api.updated, isNull);
    expect(find.textContaining('voor het begin van je dutje'), findsOneWidget);
  });

  testWidgets('een geschat dutje toont de uitleg en is te verwijderen', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await pump(
      tester,
      api,
      nap: Nap(
        id: 'n9',
        start: DateTime(2026, 11, 18, 13, 10),
        end: DateTime(2026, 11, 18, 13, 40),
        endEstimated: true,
      ),
    );

    expect(find.textContaining('Het eind is geschat'), findsOneWidget);
    expect(find.text('Begin 1:10 PM'), findsOneWidget);
    await tester.tap(find.text('Dutje verwijderen'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Verwijderen'));
    await tester.pumpAndSettle();

    expect(api.deleted, 'n9');
    expect(cancelled, 0, reason: 'een afgesloten dutje heeft geen wekker meer');
  });

  test('omschrijving en duur van een dutje', () {
    expect(napDuration(const Duration(minutes: 25)), '25 min');
    expect(napDuration(const Duration(minutes: 70)), '1 u 10');
    expect(napDuration(const Duration(minutes: 120)), '2 u');
    expect(
      describeNap(
        Nap(
          id: 'n',
          start: DateTime(2026, 11, 18, 13, 10),
          end: DateTime(2026, 11, 18, 13, 40),
        ),
      ),
      'Dutje 13:10–13:40 · 30 min',
    );
  });
}
