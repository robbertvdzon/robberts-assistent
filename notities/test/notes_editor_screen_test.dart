import 'package:flutter/material.dart';
import 'package:flutter_quill/flutter_quill.dart';
import 'package:flutter_quill/quill_delta.dart';
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

/// De Quill-editor heeft zijn eigen localizations-delegate nodig, net als in
/// `main.dart`.
Widget _app(ApiClient api) => MaterialApp(
  localizationsDelegates: FlutterQuillLocalizations.localizationsDelegates,
  supportedLocales: FlutterQuillLocalizations.supportedLocales,
  home: NotesEditorScreen(api: api, onLoggedOut: () {}),
);

/// Pumpt tot de notitie geladen is (geen `pumpAndSettle`: de laadspinner blijft
/// anders frames plannen).
Future<void> _pumpLoaded(WidgetTester tester, ApiClient api) async {
  await tester.pumpWidget(_app(api));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

QuillController _controllerOf(WidgetTester tester) =>
    tester.widget<QuillEditor>(find.byType(QuillEditor)).controller;

/// Selecteert de hele notitietekst (zonder de afsluitende newline van Quill).
void _selectAll(WidgetTester tester) {
  final controller = _controllerOf(tester);
  controller.updateSelection(
    TextSelection(baseOffset: 0, extentOffset: controller.document.length - 1),
    ChangeSource.local,
  );
}

void main() {
  testWidgets('save-knop slaat de huidige tekst meteen op, zonder te wachten op de debounce', (
    WidgetTester tester,
  ) async {
    final api = _FakeApiClient();

    await _pumpLoaded(tester, api);

    _controllerOf(tester).document = Document.fromDelta(Delta()..insert('nieuwe inhoud\n'));
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

    await _pumpLoaded(tester, api);

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();

    expect(api.saveCallCount, 1);
    expect(find.textContaining('Opslaan mislukt'), findsOneWidget);
    // De inhoud blijft gewoon in de editor staan.
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');

    // Voorkomt dat dispose() (best-effort save bij nog-openstaande wijzigingen)
    // opnieuw een onopgevangen fout gooit tijdens de teardown van deze test.
    api.saveError = null;
  });

  testWidgets('de bestaande notitie wordt als opgemaakte tekst geladen', (
    WidgetTester tester,
  ) async {
    final api = _FakeApiClient()..initialText = 'een **vet** woord';

    await _pumpLoaded(tester, api);

    // De markers zijn opmaak geworden, geen zichtbare tekens meer.
    expect(_controllerOf(tester).document.toPlainText(), 'een vet woord\n');
  });

  testWidgets('de opmaakbalk heeft precies de vijf afgesproken knoppen', (
    WidgetTester tester,
  ) async {
    await _pumpLoaded(tester, _FakeApiClient());

    for (final tooltip in ['Vet', 'Cursief', 'Onderstreept', 'Opsomming', 'Opmaak wissen']) {
      expect(find.byTooltip(tooltip), findsOneWidget, reason: 'knop $tooltip ontbreekt');
    }
    // Precies vijf opmaakknoppen; Opslaan/Uitloggen zitten in de AppBar, buiten de balk.
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('opmaakbalk')),
        matching: find.byType(IconButton),
      ),
      findsNWidgets(5),
    );
  });

  testWidgets('selectie + Vet levert **tekst** bij het opslaan; Opmaak wissen haalt het weer weg', (
    WidgetTester tester,
  ) async {
    final api = _FakeApiClient()..initialText = 'notitie';

    await _pumpLoaded(tester, api);

    _selectAll(tester);
    await tester.pump();
    await tester.tap(find.byTooltip('Vet'));
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();
    expect(api.lastSavedText, '**notitie**');

    _selectAll(tester);
    await tester.pump();
    await tester.tap(find.byTooltip('Opmaak wissen'));
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();
    expect(api.lastSavedText, 'notitie');
  });

  testWidgets('Opsomming maakt van de regel een bullet in de opgeslagen tekst', (
    WidgetTester tester,
  ) async {
    final api = _FakeApiClient()..initialText = 'melk';

    await _pumpLoaded(tester, api);

    _selectAll(tester);
    await tester.pump();
    await tester.tap(find.byTooltip('Opsomming'));
    await tester.pump();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();
    expect(api.lastSavedText, '- melk');
  });

  testWidgets('autosave slaat pas na de debounce van 10 seconden op', (WidgetTester tester) async {
    final api = _FakeApiClient();

    await _pumpLoaded(tester, api);

    _controllerOf(tester).document.insert(0, 'extra ');
    await tester.pump();

    await tester.pump(const Duration(seconds: 9));
    expect(api.saveCallCount, 0);

    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, 'extra bestaande notitie');
  });

  testWidgets('een wijziging wordt meteen opgeslagen als de app naar de achtergrond gaat', (
    WidgetTester tester,
  ) async {
    final api = _FakeApiClient();

    await _pumpLoaded(tester, api);

    _controllerOf(tester).document.insert(0, 'extra ');
    await tester.pump();

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    await tester.pump();

    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, 'extra bestaande notitie');
  });
}
