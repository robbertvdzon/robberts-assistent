import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notities/api_client.dart';
import 'package:notities/notes_editor_screen.dart';

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.initialText = 'bestaande notitie', this.failSave = false});

  final String initialText;
  final bool failSave;
  final List<String> savedTexts = [];

  @override
  Future<String> getNotes() async => initialText;

  @override
  Future<void> saveNotes(String text) async {
    if (failSave) {
      throw Exception('kon niet opslaan');
    }
    savedTexts.add(text);
  }
}

Future<void> _pumpAndLoad(WidgetTester tester, ApiClient api) async {
  await tester.pumpWidget(
    MaterialApp(home: NotesEditorScreen(api: api, onLoggedOut: () {})),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

void main() {
  testWidgets('save-knop is zichtbaar in de app-bar naast de uitlog-knop', (tester) async {
    final api = _FakeApiClient();
    await _pumpAndLoad(tester, api);

    expect(find.byTooltip('Opslaan'), findsOneWidget);
    expect(find.byTooltip('Uitloggen'), findsOneWidget);
  });

  testWidgets('indrukken van de save-knop slaat direct op zonder debounce af te wachten', (
    tester,
  ) async {
    final api = _FakeApiClient();
    await _pumpAndLoad(tester, api);

    await tester.enterText(find.byType(TextField), 'nieuwe tekst');
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();

    expect(api.savedTexts, ['nieuwe tekst']);
    expect(find.text('Opgeslagen'), findsOneWidget);
  });

  testWidgets('save-knop toont foutmelding wanneer opslaan mislukt', (tester) async {
    final api = _FakeApiClient(failSave: true);
    await _pumpAndLoad(tester, api);

    await tester.enterText(find.byType(TextField), 'weer iets nieuws');
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();

    expect(find.textContaining('Opslaan mislukt:'), findsOneWidget);
  });

  testWidgets('save-knop werkt ook zonder niet-opgeslagen wijzigingen', (tester) async {
    final api = _FakeApiClient();
    await _pumpAndLoad(tester, api);

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();

    expect(api.savedTexts, [api.initialText]);
    expect(find.text('Opgeslagen'), findsOneWidget);
  });
}
