import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/fcm_service.dart';
import 'package:robberts_assistent/more_screen.dart';
import 'package:robberts_assistent/notifications_screen.dart';

AppNotification _notification(
  String id, {
  bool read = false,
  String type = 'meal',
}) => AppNotification(
  id: id,
  title: 'Melding $id',
  body:
      'De volledige melding $id. Je kunt deze invoer wissen als je echt niet hebt ontbeten.',
  createdAt: DateTime(2026, 11, 25, 12),
  data: {'type': type, 'mealId': 'meal-$id'},
  readAt: read ? DateTime(2026, 11, 25, 13) : null,
);

class _Api extends ApiClient {
  final readIds = <String>[];
  final cursors = <String?>[];
  bool fail = false;
  bool empty = false;
  @override
  Future<NotificationPage> notifications({String? before}) async {
    cursors.add(before);
    if (fail) throw Exception('offline');
    if (empty) return const NotificationPage(items: []);
    return before == null
        ? NotificationPage(
            items: [
              _notification('nieuw'),
              _notification('gelezen', read: true),
            ],
            nextCursor: 'gelezen',
          )
        : NotificationPage(items: [_notification('oud', type: 'onbekend')]);
  }

  @override
  Future<AppNotification> markNotificationRead(String id) async {
    readIds.add(id);
    return _notification(id, read: true);
  }
}

Future<void> _pump(WidgetTester tester, _Api api) async {
  await tester.binding.setSurfaceSize(const Size(390, 844));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(MaterialApp(home: NotificationsScreen(api: api)));
  await tester.pumpAndSettle();
}

void main() {
  tearDown(() => FcmService.deepLinkTarget.value = null);

  testWidgets(
    'bericht lezen bewaart alle meldingen, toont volledige tekst en opent dagboek',
    (tester) async {
      final api = _Api();
      await _pump(tester, api);
      expect(find.byIcon(Icons.notifications_active_outlined), findsOneWidget);
      expect(find.text('25-11-2026 om 12:00'), findsNWidgets(2));
      await tester.tap(find.text('Melding nieuw'));
      await tester.pumpAndSettle();
      expect(api.readIds, ['nieuw']);
      expect(find.byType(SelectableText), findsOneWidget);
      expect(find.text(_notification('nieuw').body), findsWidgets);
      await tester.tap(find.text('Openen'));
      await tester.pumpAndSettle();
      expect(FcmService.deepLinkTarget.value, DeepLinkTarget.food);
      expect(find.text('Melding gelezen'), findsOneWidget);
      expect(find.text('Melding nieuw'), findsOneWidget);
      expect(find.byIcon(Icons.notifications_active_outlined), findsNothing);
    },
  );

  testWidgets(
    'oudere meldingen bijladen houdt nieuwe zichtbaar en onbekend type heeft geen openknop',
    (tester) async {
      final api = _Api();
      await _pump(tester, api);
      await tester.tap(find.text('Oudere meldingen laden'));
      await tester.pumpAndSettle();
      expect(api.cursors, [null, 'gelezen']);
      expect(find.text('Melding nieuw'), findsOneWidget);
      expect(find.text('Melding oud'), findsOneWidget);
      expect(find.text('Oudere meldingen laden'), findsNothing);
      await tester.tap(find.text('Melding oud'));
      await tester.pumpAndSettle();
      expect(find.text('Openen'), findsNothing);
      expect(find.text('Sluiten'), findsOneWidget);
    },
  );

  testWidgets('netwerkfout is te herstellen en lege historie legt start uit', (
    tester,
  ) async {
    final api = _Api()..fail = true;
    await _pump(tester, api);
    expect(find.text('Opnieuw proberen'), findsOneWidget);
    api.fail = false;
    api.empty = true;
    await tester.tap(find.text('Opnieuw proberen'));
    await tester.pumpAndSettle();
    expect(
      find.text('Nog geen meldingen. Nieuwe meldingen worden hier bewaard.'),
      findsOneWidget,
    );
  });

  testWidgets('Meer bevat een vaste meldingenbestemming', (tester) async {
    String? path;
    await tester.pumpWidget(
      MaterialApp(
        home: MoreScreen(api: _Api(), onOpen: (value) => path = value),
      ),
    );
    await tester.tap(find.text('Meldingen'));
    expect(path, '/meer/meldingen');
    expect(MoreScreen.paths, contains('meldingen'));
  });

  test('nieuwe en oude lokale notificaties blijven navigeerbaar', () {
    FcmService.handleLocalPayload(
      jsonEncode({'type': 'meal', 'mealId': 'a', 'notificationId': 'b'}),
    );
    expect(FcmService.deepLinkTarget.value, DeepLinkTarget.food);
    FcmService.handleLocalPayload('nap');
    expect(FcmService.deepLinkTarget.value, DeepLinkTarget.nap);
    FcmService.deepLinkTarget.value = null;
    FcmService.handleLocalPayload('{kapot');
    expect(FcmService.deepLinkTarget.value, isNull);
  });
}
