import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notities/api_client.dart';
import 'package:notities/notes_editor_screen.dart';

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.saveError});

  String initialText = 'bestaande notitie';
  String? lastSavedText;
  var saveCallCount = 0;
  Object? saveError;

  @override
  Future<String> getNotes() async => initialText;

  @override
  Future<void> saveNotes(String text) async {
    saveCallCount++;
    if (saveError != null) throw saveError!;
    lastSavedText = text;
  }
}

void main() {
  testWidgets('save-knop slaat de huidige tekst meteen op, zonder te wachten op de debounce', (
    WidgetTester tester,
  ) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(
      MaterialApp(
        home: NotesEditorScreen(api: api, onLoggedOut: () {}),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.enterText(find.byType(TextField), 'nieuwe inhoud');
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();

    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, 'nieuwe inhoud');
    expect(find.text('Opgeslagen'), findsOneWidget);
  });

  testWidgets('save-knop toont een foutmelding als opslaan mislukt', (WidgetTester tester) async {
    final api = _FakeApiClient(saveError: Exception('netwerkfout'));

    await tester.pumpWidget(
      MaterialApp(
        home: NotesEditorScreen(api: api, onLoggedOut: () {}),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();

    expect(api.saveCallCount, 1);
    expect(find.textContaining('Opslaan mislukt'), findsOneWidget);

    // Voorkomt dat dispose() (best-effort save bij nog-openstaande wijzigingen)
    // opnieuw een onopgevangen fout gooit tijdens de teardown van deze test.
    api.saveError = null;
  });
}
