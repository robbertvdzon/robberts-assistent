import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:notities/api_client.dart';
import 'package:notities/main.dart' show notitiesDarkTheme;
import 'package:notities/note_documents_screen.dart';

import 'fake_api_client.dart';

FakeApiClient _api({int documents = 3}) => FakeApiClient()
  ..documents = [
    for (var i = 0; i < documents; i++) NoteDocument(id: 'doc$i', title: 'document $i', order: i),
  ];

Future<void> _pumpScreen(WidgetTester tester, FakeApiClient api) async {
  await tester.pumpWidget(
    MaterialApp(theme: notitiesDarkTheme, home: NoteDocumentsScreen(api: api)),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

List<String> _titles(FakeApiClient api) => api.documents.map((document) => document.title).toList();

void main() {
  testWidgets('toont de documenten op volgorde', (WidgetTester tester) async {
    await _pumpScreen(tester, _api());

    expect(find.text('document 0'), findsOneWidget);
    expect(find.text('document 1'), findsOneWidget);
    expect(find.text('document 2'), findsOneWidget);
  });

  testWidgets('toevoegen maakt een document met de ingevoerde titel', (WidgetTester tester) async {
    final api = _api(documents: 1);
    await _pumpScreen(tester, api);

    await tester.tap(find.byTooltip('Document toevoegen'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'recepten');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(_titles(api), ['document 0', 'recepten']);
    expect(find.text('recepten'), findsOneWidget);
  });

  testWidgets('toevoegen met een lege titel doet niets', (WidgetTester tester) async {
    final api = _api(documents: 1);
    await _pumpScreen(tester, api);

    await tester.tap(find.byTooltip('Document toevoegen'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), '   ');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(_titles(api), ['document 0']);
  });

  testWidgets('een fout van de backend (bv. dubbele titel) komt als melding terug', (WidgetTester tester) async {
    final api = _api(documents: 1)..writeError = const ApiException(409, 'Er bestaat al een document met die titel');
    await _pumpScreen(tester, api);

    await tester.tap(find.byTooltip('Document toevoegen'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'document 0');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(find.text('Er bestaat al een document met die titel'), findsOneWidget);
    expect(_titles(api), ['document 0']);
  });

  testWidgets('hernoemen wijzigt de titel van dat document', (WidgetTester tester) async {
    final api = _api();
    await _pumpScreen(tester, api);

    await tester.tap(find.byTooltip('Hernoemen').at(1));
    await tester.pumpAndSettle();
    // Het dialoog start met de huidige titel.
    expect(find.widgetWithText(TextField, 'document 1'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'boodschappen');
    await tester.tap(find.text('Opslaan'));
    await tester.pumpAndSettle();

    expect(_titles(api), ['document 0', 'boodschappen', 'document 2']);
    expect(find.text('boodschappen'), findsOneWidget);
  });

  testWidgets('verwijderen vraagt eerst bevestiging', (WidgetTester tester) async {
    final api = _api();
    await _pumpScreen(tester, api);

    await tester.tap(find.byTooltip('Verwijderen').first);
    await tester.pumpAndSettle();
    expect(find.text('Document verwijderen?'), findsOneWidget);

    await tester.tap(find.text('Annuleren'));
    await tester.pumpAndSettle();
    expect(_titles(api), ['document 0', 'document 1', 'document 2']);

    await tester.tap(find.byTooltip('Verwijderen').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ja, verwijderen'));
    await tester.pumpAndSettle();

    expect(_titles(api), ['document 1', 'document 2']);
    expect(find.text('document 0'), findsNothing);
  });

  testWidgets('bij precies één document is verwijderen niet beschikbaar', (WidgetTester tester) async {
    await _pumpScreen(tester, _api(documents: 1));

    final button = tester.widget<IconButton>(
      find.ancestor(of: find.byTooltip('Verwijderen'), matching: find.byType(IconButton)),
    );
    expect(button.onPressed, isNull);
    // Hernoemen kan wel gewoon.
    expect(
      tester
          .widget<IconButton>(find.ancestor(of: find.byTooltip('Hernoemen'), matching: find.byType(IconButton)))
          .onPressed,
      isNotNull,
    );
  });

  testWidgets('slepen bewaart de nieuwe volgorde met dichte posities', (WidgetTester tester) async {
    final api = _api();
    await _pumpScreen(tester, api);

    // Sleep het bovenste document onder de andere twee.
    final handle = find.byIcon(Icons.drag_handle).first;
    final gesture = await tester.startGesture(tester.getCenter(handle));
    await tester.pump(const Duration(milliseconds: 600));
    // In stapjes: één grote sprong verwerkt ReorderableListView als één plek.
    for (var i = 0; i < 8; i++) {
      await gesture.moveBy(const Offset(0, 20));
      await tester.pump(const Duration(milliseconds: 20));
    }
    await gesture.up();
    await tester.pumpAndSettle();

    expect(_titles(api), ['document 1', 'document 2', 'document 0']);
    expect(api.documents.map((document) => document.order), [0, 1, 2]);
  });
}
