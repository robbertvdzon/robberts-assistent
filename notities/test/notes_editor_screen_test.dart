import 'package:flutter/material.dart';
import 'package:flutter_quill/flutter_quill.dart';
import 'package:flutter_quill/quill_delta.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:notities/api_client.dart';
import 'package:notities/notes_editor_screen.dart';

class _FakeApiClient extends ApiClient {
  _FakeApiClient({this.saveError});

  String initialText = 'bestaande notitie';
  String? lastSavedText;
  var saveCallCount = 0;
  Object? saveError;

  /// Versie-id => markdown-tekst; de lijst wordt hieruit afgeleid.
  Map<String, String> versionTexts = {};
  DateTime versionSavedAt = DateTime.now();

  @override
  Future<String> getNotes() async => initialText;

  @override
  Future<void> saveNotes(String text) async {
    saveCallCount++;
    if (saveError != null) throw saveError!;
    lastSavedText = text;
  }

  @override
  Future<List<NoteVersionSummary>> listNoteVersions() async =>
      versionTexts.keys.map((id) => NoteVersionSummary(id: id, savedAt: versionSavedAt)).toList(growable: false);

  @override
  Future<String> getNoteVersion(String id) async => versionTexts[id]!;
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
Future<void> _pumpLoaded(
  WidgetTester tester,
  ApiClient api, {
  Map<String, Object> preferences = const {},
  bool resetPreferences = true,
}) async {
  if (resetPreferences) SharedPreferences.setMockInitialValues(preferences);
  await tester.pumpWidget(_app(api));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

QuillController _controllerOf(WidgetTester tester) => tester.widget<QuillEditor>(find.byType(QuillEditor)).controller;

DefaultStyles _stylesOf(WidgetTester tester) =>
    tester.widget<QuillEditor>(find.byType(QuillEditor)).config.customStyles!;

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

  testWidgets('de bestaande notitie wordt als opgemaakte tekst geladen', (WidgetTester tester) async {
    final api = _FakeApiClient()..initialText = 'een **vet** woord';

    await _pumpLoaded(tester, api);

    // De markers zijn opmaak geworden, geen zichtbare tekens meer.
    expect(_controllerOf(tester).document.toPlainText(), 'een vet woord\n');
  });

  testWidgets('de opmaakbalk heeft lettergrootte, undo/redo en de vijf opmaakknoppen', (WidgetTester tester) async {
    await _pumpLoaded(tester, _FakeApiClient());

    for (final tooltip in [
      'Lettergrootte verkleinen',
      'Lettergrootte vergroten',
      'Ongedaan maken',
      'Opnieuw',
      'Vet',
      'Cursief',
      'Onderstreept',
      'Opsomming',
      'Opmaak wissen',
    ]) {
      expect(find.byTooltip(tooltip), findsOneWidget, reason: 'knop $tooltip ontbreekt');
    }
    // Precies negen knoppen; Opslaan/Versies/Uitloggen zitten in de AppBar, buiten de balk.
    expect(
      find.descendant(of: find.byKey(const ValueKey('opmaakbalk')), matching: find.byType(IconButton)),
      findsNWidgets(9),
    );
  });

  testWidgets('lettergrootte schaalt gewone tekst, opmaak, lijsttekst en bulletmarkering', (WidgetTester tester) async {
    final api = _FakeApiClient()..initialText = '- gewoon **vet** *cursief* <u>onderstreept</u>';

    await _pumpLoaded(tester, api);

    final styles = _stylesOf(tester);
    expect(styles.paragraph!.style.fontSize, 16);
    expect(styles.lists!.style.fontSize, 16);
    expect(styles.leading!.style.fontSize, 16);
    expect(tester.widget<Text>(find.text('•')).style!.fontSize, 16);

    await tester.tap(find.byTooltip('Lettergrootte vergroten'));
    await tester.pump();
    final enlargedStyles = _stylesOf(tester);
    expect(enlargedStyles.paragraph!.style.fontSize, 18);
    expect(enlargedStyles.lists!.style.fontSize, 18);
    expect(enlargedStyles.leading!.style.fontSize, 18);
    expect(tester.widget<Text>(find.text('•')).style!.fontSize, 18);
  });

  testWidgets('A− en A+ wijzigen direct in stappen van 2 en zijn uitgeschakeld op de grenzen', (
    WidgetTester tester,
  ) async {
    await _pumpLoaded(tester, _FakeApiClient());

    await tester.tap(find.byTooltip('Lettergrootte verkleinen'));
    await tester.pump();
    expect(_stylesOf(tester).paragraph!.style.fontSize, 14);
    expect(_onPressedOf(tester, 'Lettergrootte verkleinen'), isNotNull);

    await tester.tap(find.byTooltip('Lettergrootte verkleinen'));
    await tester.pump();
    expect(_stylesOf(tester).paragraph!.style.fontSize, 12);
    expect(_onPressedOf(tester, 'Lettergrootte verkleinen'), isNull);

    for (var i = 0; i < 8; i++) {
      await tester.tap(find.byTooltip('Lettergrootte vergroten'));
      await tester.pump();
    }
    expect(_stylesOf(tester).paragraph!.style.fontSize, 28);
    expect(_stylesOf(tester).lists!.style.fontSize, 28);
    expect(_stylesOf(tester).leading!.style.fontSize, 28);
    expect(_onPressedOf(tester, 'Lettergrootte vergroten'), isNull);
  });

  testWidgets('bewaarde grootte wordt hersteld en ontbrekende of ongeldige waarden vallen terug', (
    WidgetTester tester,
  ) async {
    await _pumpLoaded(tester, _FakeApiClient(), preferences: {'notes_editor_font_size': 22});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 22);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, _FakeApiClient(), preferences: {'notes_editor_font_size': 15});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 16);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, _FakeApiClient(), preferences: {'notes_editor_font_size': 100});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 28);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, _FakeApiClient(), preferences: {'notes_editor_font_size': -10});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 12);
  });

  testWidgets('gewijzigde grootte blijft bewaard bij een nieuw opgebouwd notitiescherm', (WidgetTester tester) async {
    await _pumpLoaded(tester, _FakeApiClient());

    await tester.tap(find.byTooltip('Lettergrootte vergroten'));
    await tester.pump();
    expect((await SharedPreferences.getInstance()).getInt('notes_editor_font_size'), 18);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, _FakeApiClient(), resetPreferences: false);
    expect(_stylesOf(tester).paragraph!.style.fontSize, 18);
  });

  testWidgets('lettergrootte wijzigen raakt document, autosave en opgeslagen markdown niet', (
    WidgetTester tester,
  ) async {
    final markdown = '- gewoon\n- **vet** en *cursief* en <u>onderstreept</u>';
    final api = _FakeApiClient()..initialText = markdown;
    await _pumpLoaded(tester, api);
    final deltaBefore = _controllerOf(tester).document.toDelta().toJson();

    await tester.tap(find.byTooltip('Lettergrootte vergroten'));
    await tester.pump();
    expect(_controllerOf(tester).document.toDelta().toJson(), deltaBefore);

    await tester.pump(const Duration(seconds: 11));
    await tester.pump();
    expect(api.saveCallCount, 0);

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();
    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, markdown);
  });

  testWidgets('opmaakbalk geeft op een smal scherm geen layout-overflow', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(280, 600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await _pumpLoaded(tester, _FakeApiClient());

    expect(tester.takeException(), isNull);
    expect(find.byTooltip('Lettergrootte verkleinen'), findsOneWidget);
    expect(find.byTooltip('Lettergrootte vergroten'), findsOneWidget);
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

  testWidgets('Opsomming maakt van de regel een bullet in de opgeslagen tekst', (WidgetTester tester) async {
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

  testWidgets('een wijziging wordt meteen opgeslagen als de app naar de achtergrond gaat', (WidgetTester tester) async {
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

  testWidgets('direct na het laden zijn Ongedaan maken en Opnieuw uitgegrijsd', (WidgetTester tester) async {
    await _pumpLoaded(tester, _FakeApiClient());

    expect(_onPressedOf(tester, 'Ongedaan maken'), isNull);
    expect(_onPressedOf(tester, 'Opnieuw'), isNull);

    // Undo indrukken kan niet, dus de geladen notitie kan er niet door leeglopen.
    await tester.tap(find.byTooltip('Ongedaan maken'), warnIfMissed: false);
    await tester.pump();
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');
  });

  testWidgets('Ongedaan maken draait een wijziging terug en Opnieuw zet hem weer terug', (WidgetTester tester) async {
    final api = _FakeApiClient();
    await _pumpLoaded(tester, api);

    _controllerOf(tester).document.insert(0, 'extra ');
    // Twee frames: de changes-stream levert asynchroon, pas dáárna volgt de
    // setState die de opmaakbalk hertekent.
    await tester.pump();
    await tester.pump();
    expect(_controllerOf(tester).document.toPlainText(), 'extra bestaande notitie\n');
    expect(_onPressedOf(tester, 'Ongedaan maken'), isNotNull);

    await tester.tap(find.byTooltip('Ongedaan maken'));
    await tester.pump();
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');
    expect(_onPressedOf(tester, 'Opnieuw'), isNotNull);

    await tester.tap(find.byTooltip('Opnieuw'));
    await tester.pump();
    expect(_controllerOf(tester).document.toPlainText(), 'extra bestaande notitie\n');
  });

  testWidgets('Versies toont de bewaarde versies en een alleen-lezen weergave', (WidgetTester tester) async {
    final api = _FakeApiClient()
      ..versionTexts = {'v1': 'oude **inhoud**'}
      ..versionSavedAt = DateTime.now();

    await _pumpLoaded(tester, api);

    await tester.tap(find.byTooltip('Versies'));
    await tester.pumpAndSettle();
    expect(find.textContaining('vandaag '), findsOneWidget);

    await tester.tap(find.textContaining('vandaag '));
    await tester.pumpAndSettle();
    // Alleen-lezen: platte markdown als selecteerbare tekst, geen editor.
    expect(find.text('oude **inhoud**'), findsOneWidget);
    expect(find.byType(QuillEditor), findsNothing);
  });

  testWidgets('Terugzetten vraagt bevestiging; annuleren laat de editor ongemoeid', (WidgetTester tester) async {
    final api = _FakeApiClient()..versionTexts = {'v1': 'oude inhoud'};

    await _pumpLoaded(tester, api);
    await tester.tap(find.byTooltip('Versies'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(ListTile).first);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Terugzetten'));
    await tester.pumpAndSettle();
    expect(find.text('Versie terugzetten?'), findsOneWidget);

    await tester.tap(find.text('Annuleren'));
    await tester.pumpAndSettle();
    // Nog steeds op de versie-weergave, editor onaangeroerd.
    expect(find.text('Versie terugzetten?'), findsNothing);
    expect(find.widgetWithText(FilledButton, 'Terugzetten'), findsOneWidget);
  });

  testWidgets('Terugzetten vervangt na bevestiging de editorinhoud en is undo-baar', (WidgetTester tester) async {
    final api = _FakeApiClient()
      ..initialText = 'huidige notitie'
      ..versionTexts = {'v1': 'oude inhoud'};

    await _pumpLoaded(tester, api);
    await tester.tap(find.byTooltip('Versies'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(ListTile).first);
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Terugzetten'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ja, terugzetten'));
    await tester.pumpAndSettle();

    // Terug in de editor, met de oude tekst erin.
    expect(find.byType(QuillEditor), findsOneWidget);
    expect(_controllerOf(tester).document.toPlainText(), 'oude inhoud\n');

    // Het terugzetten is een gewone documentwijziging: undo draait het terug ...
    expect(_onPressedOf(tester, 'Ongedaan maken'), isNotNull);
    await tester.tap(find.byTooltip('Ongedaan maken'));
    await tester.pump();
    expect(_controllerOf(tester).document.toPlainText(), 'huidige notitie\n');

    // ... en de normale debounce-autosave slaat het op.
    await tester.tap(find.byTooltip('Opnieuw'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 11));
    await tester.pump();
    expect(api.lastSavedText, 'oude inhoud');
  });

  testWidgets('opmaak van een teruggezette versie blijft behouden', (WidgetTester tester) async {
    final api = _FakeApiClient()
      ..initialText = 'plat'
      ..versionTexts = {'v1': '- melk\n- **eieren**'};

    await _pumpLoaded(tester, api);
    await tester.tap(find.byTooltip('Versies'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(ListTile).first);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Terugzetten'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ja, terugzetten'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Opslaan'));
    await tester.pump();
    await tester.pump();
    expect(api.lastSavedText, '- melk\n- **eieren**');
  });
}

/// `find.byTooltip` levert de Tooltip-widget, niet de knop zelf.
VoidCallback? _onPressedOf(WidgetTester tester, String tooltip) =>
    tester.widget<IconButton>(find.ancestor(of: find.byTooltip(tooltip), matching: find.byType(IconButton))).onPressed;
