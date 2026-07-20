import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/memory_screen.dart';

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.saveError});

  String initialText = 'houdt van kiten';
  String? lastSavedText;
  var saveCallCount = 0;
  Object? saveError;

  @override
  Future<String> getMemoryText() async => initialText;

  @override
  Future<void> saveMemoryText(String text) async {
    saveCallCount++;
    if (saveError != null) throw saveError!;
    lastSavedText = text;
  }
}

void main() {
  testWidgets('toont de huidige geheugen-tekst', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
    await tester.pump();

    expect(find.text('houdt van kiten'), findsOneWidget);
  });

  testWidgets('save-knop slaat de huidige tekst meteen op, zonder te wachten op de debounce', (tester) async {
    final api = _FakeApiClient();

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.enterText(find.byType(TextField), 'nieuw feit');
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();

    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, 'nieuw feit');
    expect(find.text('Opgeslagen'), findsOneWidget);
  });

  testWidgets('save-knop toont een foutmelding als opslaan mislukt', (tester) async {
    final api = _FakeApiClient(saveError: Exception('netwerkfout'));

    await tester.pumpWidget(MaterialApp(home: MemoryScreen(api: api)));
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
