import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notities/api_client.dart';
import 'package:notities/main.dart' show notitiesDarkTheme;
import 'package:notities/note_versions_screen.dart';

/// Levert alleen de versietekst; zelfde stijl als `_FakeApiClient` in
/// `notes_editor_screen_test.dart`.
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this.text);

  final String text;

  @override
  Future<String> getNoteVersion(String documentId, String versionId) async => text;
}

/// Houdt vast welke versie getoond wordt en wat het scherm bij het sluiten
/// teruggeeft aan de editor.
class _DetailHarness {
  _DetailHarness(this.version);

  final NoteVersionSummary version;
  String? restored;
}

/// Pusht het detailscherm als eigen route (zoals de versielijst dat doet, en
/// zodat `Navigator.pop` een echte route heeft om te sluiten) en pumpt tot de
/// tekst geladen is.
Future<_DetailHarness> _pumpDetail(WidgetTester tester, String text, {DateTime? savedAt}) async {
  final harness = _DetailHarness(NoteVersionSummary(id: 'v1', savedAt: savedAt ?? DateTime.now()));
  await tester.pumpWidget(
    MaterialApp(
      theme: notitiesDarkTheme,
      home: Scaffold(
        body: Builder(
          builder: (context) => TextButton(
            onPressed: () async {
              harness.restored = await Navigator.of(context).push<String>(
                MaterialPageRoute(
                  builder: (_) => NoteVersionDetailScreen(
                    api: _FakeApiClient(text),
                    documentId: 'note',
                    version: harness.version,
                  ),
                ),
              );
            },
            child: const Text('open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
  return harness;
}

void main() {
  group('formatVersionMoment', () {
    final now = DateTime(2026, 8, 2, 12, 0);

    test('vandaag toont "vandaag" met de tijd', () {
      expect(formatVersionMoment(DateTime(2026, 8, 2, 11, 30), now: now), 'vandaag 11:30');
    });

    test('gisteren toont "gisteren" met de tijd', () {
      expect(formatVersionMoment(DateTime(2026, 8, 1, 9, 5), now: now), 'gisteren 09:05');
    });

    test('ouder toont dag, datum, maand en tijd in het Nederlands', () {
      // 28 juli 2026 is een dinsdag.
      expect(formatVersionMoment(DateTime(2026, 7, 28, 9, 5), now: now), 'di 28 jul 09:05');
      // 27 juli 2026 is een maandag.
      expect(formatVersionMoment(DateTime(2026, 7, 27, 23, 59), now: now), 'ma 27 jul 23:59');
    });

    test('middernacht en enkele cijfers worden met voorloopnul getoond', () {
      expect(formatVersionMoment(DateTime(2026, 8, 2, 0, 4), now: now), 'vandaag 00:04');
    });
  });

  group('NoteVersionDetailScreen', () {
    testWidgets('toont de versietekst en een label in de rode versiekleur', (WidgetTester tester) async {
      final harness = await _pumpDetail(tester, 'oude inhoud');

      final label = 'Oude versie van ${formatVersionMoment(harness.version.savedAt)}';
      expect(find.text(label), findsOneWidget);
      expect(tester.widget<Text>(find.text(label)).style!.color, noteVersionColor);

      final text = tester.widget<SelectableText>(find.byType(SelectableText));
      expect(text.data, 'oude inhoud');
      expect(text.style!.color, noteVersionColor);

      // Lichte tint, dus goed leesbaar op de zwarte achtergrond.
      expect(noteVersionColor, const Color(0xFFE57373));
    });

    testWidgets('een lege versie toont de placeholdertekst, ook in het rood', (WidgetTester tester) async {
      await _pumpDetail(tester, '');

      final text = tester.widget<SelectableText>(find.byType(SelectableText));
      expect(text.data, '(lege notitie)');
      expect(text.style!.color, noteVersionColor);
    });

    testWidgets('het knopblok met Terugzetten zit in een SafeArea', (WidgetTester tester) async {
      await _pumpDetail(tester, 'oude inhoud');

      final button = find.widgetWithText(FilledButton, 'Terugzetten');
      expect(button, findsOneWidget);
      final safeAreas = find.ancestor(of: button, matching: find.byType(SafeArea));
      expect(safeAreas, findsWidgets);
      // Alleen onderaan: bovenlangs verandert de layout niet.
      expect(tester.widgetList<SafeArea>(safeAreas).any((s) => !s.top && s.bottom), isTrue);
    });

    testWidgets('Terugzetten vraagt bevestiging en geeft daarna de tekst terug', (WidgetTester tester) async {
      final harness = await _pumpDetail(tester, 'oude inhoud');

      await tester.tap(find.widgetWithText(FilledButton, 'Terugzetten'));
      await tester.pumpAndSettle();
      expect(find.text('Versie terugzetten?'), findsOneWidget);
      expect(find.text('Annuleren'), findsOneWidget);

      await tester.tap(find.text('Ja, terugzetten'));
      await tester.pumpAndSettle();
      // De route is gepopt met de tekst; het detailscherm is weg.
      expect(find.widgetWithText(FilledButton, 'Terugzetten'), findsNothing);
      expect(harness.restored, 'oude inhoud');
    });
  });
}
