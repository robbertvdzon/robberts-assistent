import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notities/api_client.dart';
import 'package:notities/notes_editor_screen.dart';

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.saveError, String initialText = ''}) : _text = initialText;

  final String _text;
  final Exception? saveError;
  int saveCalls = 0;
  String? lastSavedText;

  @override
  Future<String> getNotes() async => _text;

  @override
  Future<void> saveNotes(String text) async {
    saveCalls++;
    lastSavedText = text;
    if (saveError != null) throw saveError!;
  }
}

Future<void> _pumpEditor(WidgetTester tester, ApiClient api) async {
  await tester.pumpWidget(
    MaterialApp(home: NotesEditorScreen(api: api, onLoggedOut: () {})),
  );
  await tester.pump();
}

void main() {
  testWidgets('save button saves the text immediately without waiting for debounce', (tester) async {
    final api = _FakeApiClient();
    await _pumpEditor(tester, api);

    await tester.enterText(find.byType(TextField), 'nieuwe tekst');
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();

    expect(api.saveCalls, 1);
    expect(api.lastSavedText, 'nieuwe tekst');
    expect(find.text('Opgeslagen'), findsOneWidget);
  });

  testWidgets('save button shows an error message when saving fails', (tester) async {
    final api = _FakeApiClient(saveError: Exception('boom'));
    await _pumpEditor(tester, api);

    await tester.enterText(find.byType(TextField), 'nieuwe tekst');
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();

    expect(find.textContaining('Opslaan mislukt:'), findsOneWidget);
  });

  testWidgets('save button forces a save even without unsaved changes', (tester) async {
    final api = _FakeApiClient(initialText: 'bestaande tekst');
    await _pumpEditor(tester, api);

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();

    expect(api.saveCalls, 1);
    expect(api.lastSavedText, 'bestaande tekst');
    expect(find.text('Opgeslagen'), findsOneWidget);
  });
}
