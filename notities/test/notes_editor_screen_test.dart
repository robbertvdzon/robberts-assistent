import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_quill/flutter_quill.dart';
import 'package:flutter_quill/quill_delta.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:notities/api_client.dart';
import 'package:notities/main.dart' show notitiesDarkTheme, notitiesEditorBackground;
import 'package:notities/note_documents_screen.dart';
import 'package:notities/notes_editor_screen.dart';

import 'fake_api_client.dart';

/// De Quill-editor heeft zijn eigen localizations-delegate nodig, net als in
/// `main.dart`.
Widget _app(ApiClient api, {ThemeData? theme}) => MaterialApp(
  theme: theme,
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
  ThemeData? theme,
}) async {
  if (resetPreferences) SharedPreferences.setMockInitialValues(preferences);
  await tester.pumpWidget(_app(api, theme: theme));
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
    final api = FakeApiClient();

    await _pumpLoaded(tester, api);

    _controllerOf(tester).document = Document.fromDelta(Delta()..insert('nieuwe inhoud\n'));
    await tester.pump();

    await _tapMenu(tester, 'Opslaan');

    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, 'nieuwe inhoud');
    // De statustekst is vervallen; na een geslaagde save staat er geen
    // dirty-indicator meer in de AppBar.
    expect(find.text('Opgeslagen'), findsNothing);
    expect(find.byKey(const ValueKey('opslagindicator')), findsNothing);
  });

  testWidgets('save-knop toont een foutmelding als opslaan mislukt', (WidgetTester tester) async {
    final api = FakeApiClient(saveError: Exception('netwerkfout'));

    await _pumpLoaded(tester, api);

    await _tapMenu(tester, 'Opslaan');

    expect(api.saveCallCount, 1);
    expect(find.textContaining('Opslaan mislukt'), findsOneWidget);
    // De inhoud blijft gewoon in de editor staan.
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');

    // Voorkomt dat dispose() (best-effort save bij nog-openstaande wijzigingen)
    // opnieuw een onopgevangen fout gooit tijdens de teardown van deze test.
    api.saveError = null;
    await _dismissSnackBar(tester);
  });

  testWidgets('de bestaande notitie wordt als opgemaakte tekst geladen', (WidgetTester tester) async {
    final api = FakeApiClient()..initialText = 'een **vet** woord';

    await _pumpLoaded(tester, api);

    // De markers zijn opmaak geworden, geen zichtbare tekens meer.
    expect(_controllerOf(tester).document.toPlainText(), 'een vet woord\n');
  });

  testWidgets('de opmaakbalk heeft lettergrootte, undo/redo en de vijf opmaakknoppen', (WidgetTester tester) async {
    await _pumpLoaded(tester, FakeApiClient());

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
    final api = FakeApiClient()..initialText = '- gewoon **vet** *cursief* <u>onderstreept</u>';

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

  testWidgets('editortekst krijgt de themakleur, niet Flutters rode monospace error-fallback', (
    WidgetTester tester,
  ) async {
    // Zoals de app het doet: het echte donkere thema eromheen.
    await _pumpLoaded(tester, FakeApiClient()..initialText = '- melk', theme: notitiesDarkTheme);

    final expected = notitiesDarkTheme.colorScheme.onSurface;
    // Op zwart levert het notities-thema feitelijk witte letters.
    expect(expected, Colors.white);

    final styles = _stylesOf(tester);
    for (final style in [styles.paragraph!.style, styles.lists!.style, styles.leading!.style]) {
      expect(style.color, expected);
      expect(style.color, isNot(const Color(0xD0FF0000)));
      expect(style.fontFamily, isNot('monospace'));
      expect(style.fontSize, 16);
    }
    // Ook de daadwerkelijk getekende bulletmarkering volgt die stijl.
    expect(tester.widget<Text>(find.text('•')).style!.color, expected);
  });

  testWidgets('de daadwerkelijk gerenderde editortekst is wit, niet rood/monospace', (WidgetTester tester) async {
    final api = FakeApiClient()..initialText = '- melk\ngewone regel met **vet**';

    await _pumpLoaded(tester, api, theme: notitiesDarkTheme);

    // Wat er op het scherm terechtkomt, niet alleen wat we meegeven: de
    // error-fallback zat vóór deze story ín de gerenderde tekststijl.
    final rendered = <TextStyle>[];
    void walk(RenderObject object) {
      if (object is RenderParagraph && object.text.style != null) rendered.add(object.text.style!);
      object.visitChildren(walk);
    }

    walk(tester.renderObject(find.byType(QuillEditor)));

    expect(rendered, isNotEmpty);
    for (final style in rendered) {
      expect(style.color, Colors.white);
      expect(style.color, isNot(const Color(0xD0FF0000)));
      expect(style.fontFamily, isNot('monospace'));
      expect(style.fontSize, 16);
    }
  });

  testWidgets('het editorvlak heeft een donkergrijze, niet-zwarte achtergrond tot onderaan het scherm', (
    WidgetTester tester,
  ) async {
    // Leeg document: juist dan moet het hele vlak onder de opmaakbalk gekleurd
    // zijn, niet alleen achter de tekstregels.
    await _pumpLoaded(tester, FakeApiClient()..initialText = '', theme: notitiesDarkTheme);

    final background = find.byKey(const ValueKey('editorachtergrond'));
    expect(background, findsOneWidget);
    expect(tester.widget<ColoredBox>(background).color, notitiesEditorBackground);
    expect(notitiesEditorBackground, isNot(Colors.black));
    // Vastgelegde waarde uit SF-1967: #404040.
    expect(notitiesEditorBackground, const Color(0xFF404040));
    // De achtergrond zit rondom de editor, dus die valt er volledig binnen.
    expect(find.descendant(of: background, matching: find.byType(QuillEditor)), findsOneWidget);

    // Het gekleurde vlak loopt door tot de onderkant van het scherm.
    final box = tester.getRect(background);
    expect(box.bottom, tester.getRect(find.byType(Scaffold)).bottom);
    expect(box.height, greaterThan(0));

    // AppBar en opmaakbalk blijven zwart.
    expect(notitiesDarkTheme.appBarTheme.backgroundColor, Colors.black);
    expect(notitiesDarkTheme.scaffoldBackgroundColor, Colors.black);
    expect(box.top, greaterThanOrEqualTo(tester.getRect(find.byKey(const ValueKey('opmaakbalk'))).bottom));
  });

  testWidgets('A+/A− wijzigt alleen de lettergrootte; de themakleur blijft staan', (WidgetTester tester) async {
    await _pumpLoaded(tester, FakeApiClient(), theme: notitiesDarkTheme);
    final expected = notitiesDarkTheme.colorScheme.onSurface;

    await tester.tap(find.byTooltip('Lettergrootte vergroten'));
    await tester.pump();
    var styles = _stylesOf(tester);
    for (final style in [styles.paragraph!.style, styles.lists!.style, styles.leading!.style]) {
      expect(style.fontSize, 18);
      expect(style.color, expected);
      expect(style.fontFamily, isNot('monospace'));
    }

    await tester.tap(find.byTooltip('Lettergrootte verkleinen'));
    await tester.tap(find.byTooltip('Lettergrootte verkleinen'));
    await tester.pump();
    styles = _stylesOf(tester);
    for (final style in [styles.paragraph!.style, styles.lists!.style, styles.leading!.style]) {
      expect(style.fontSize, 14);
      expect(style.color, expected);
    }
  });

  testWidgets('A− en A+ wijzigen direct in stappen van 2 en zijn uitgeschakeld op de grenzen', (
    WidgetTester tester,
  ) async {
    await _pumpLoaded(tester, FakeApiClient());

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
    await _pumpLoaded(tester, FakeApiClient(), preferences: {'notes_editor_font_size': 22});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 22);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, FakeApiClient(), preferences: {'notes_editor_font_size': 15});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 16);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, FakeApiClient(), preferences: {'notes_editor_font_size': 100});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 28);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, FakeApiClient(), preferences: {'notes_editor_font_size': -10});
    expect(_stylesOf(tester).paragraph!.style.fontSize, 12);
  });

  testWidgets('gewijzigde grootte blijft bewaard bij een nieuw opgebouwd notitiescherm', (WidgetTester tester) async {
    await _pumpLoaded(tester, FakeApiClient());

    await tester.tap(find.byTooltip('Lettergrootte vergroten'));
    await tester.pump();
    expect((await SharedPreferences.getInstance()).getInt('notes_editor_font_size'), 18);

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, FakeApiClient(), resetPreferences: false);
    expect(_stylesOf(tester).paragraph!.style.fontSize, 18);
  });

  testWidgets('lettergrootte wijzigen raakt document, autosave en opgeslagen markdown niet', (
    WidgetTester tester,
  ) async {
    final markdown = '- gewoon\n- **vet** en *cursief* en <u>onderstreept</u>';
    final api = FakeApiClient()..initialText = markdown;
    await _pumpLoaded(tester, api);
    final deltaBefore = _controllerOf(tester).document.toDelta().toJson();

    await tester.tap(find.byTooltip('Lettergrootte vergroten'));
    await tester.pump();
    expect(_controllerOf(tester).document.toDelta().toJson(), deltaBefore);

    await tester.pump(const Duration(seconds: 11));
    await tester.pump();
    expect(api.saveCallCount, 0);

    await _tapMenu(tester, 'Opslaan');
    expect(api.saveCallCount, 1);
    expect(api.lastSavedText, markdown);
  });

  testWidgets('opmaakbalk geeft op een smal scherm geen layout-overflow', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(280, 600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await _pumpLoaded(tester, FakeApiClient());

    expect(tester.takeException(), isNull);
    expect(find.byTooltip('Lettergrootte verkleinen'), findsOneWidget);
    expect(find.byTooltip('Lettergrootte vergroten'), findsOneWidget);
  });

  testWidgets('selectie + Vet levert **tekst** bij het opslaan; Opmaak wissen haalt het weer weg', (
    WidgetTester tester,
  ) async {
    final api = FakeApiClient()..initialText = 'notitie';

    await _pumpLoaded(tester, api);

    _selectAll(tester);
    await tester.pump();
    await tester.tap(find.byTooltip('Vet'));
    await tester.pump();

    await _tapMenu(tester, 'Opslaan');
    expect(api.lastSavedText, '**notitie**');

    _selectAll(tester);
    await tester.pump();
    await tester.tap(find.byTooltip('Opmaak wissen'));
    await tester.pump();

    await _tapMenu(tester, 'Opslaan');
    expect(api.lastSavedText, 'notitie');
  });

  testWidgets('Opsomming maakt van de regel een bullet in de opgeslagen tekst', (WidgetTester tester) async {
    final api = FakeApiClient()..initialText = 'melk';

    await _pumpLoaded(tester, api);

    _selectAll(tester);
    await tester.pump();
    await tester.tap(find.byTooltip('Opsomming'));
    await tester.pump();

    await _tapMenu(tester, 'Opslaan');
    expect(api.lastSavedText, '- melk');
  });

  testWidgets('autosave slaat pas na de debounce van 10 seconden op', (WidgetTester tester) async {
    final api = FakeApiClient();

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
    final api = FakeApiClient();

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
    await _pumpLoaded(tester, FakeApiClient());

    expect(_onPressedOf(tester, 'Ongedaan maken'), isNull);
    expect(_onPressedOf(tester, 'Opnieuw'), isNull);

    // Undo indrukken kan niet, dus de geladen notitie kan er niet door leeglopen.
    await tester.tap(find.byTooltip('Ongedaan maken'), warnIfMissed: false);
    await tester.pump();
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');
  });

  testWidgets('Ongedaan maken draait een wijziging terug en Opnieuw zet hem weer terug', (WidgetTester tester) async {
    final api = FakeApiClient();
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
    final api = FakeApiClient()
      ..versionTexts = {'v1': 'oude **inhoud**'}
      ..versionSavedAt = DateTime.now();

    await _pumpLoaded(tester, api);

    await _tapMenu(tester, 'Versies');
    expect(find.textContaining('vandaag '), findsOneWidget);

    await tester.tap(find.textContaining('vandaag '));
    await tester.pumpAndSettle();
    // Alleen-lezen: platte markdown als selecteerbare tekst, geen editor.
    expect(find.text('oude **inhoud**'), findsOneWidget);
    expect(find.byType(QuillEditor), findsNothing);
  });

  testWidgets('Terugzetten vraagt bevestiging; annuleren laat de editor ongemoeid', (WidgetTester tester) async {
    final api = FakeApiClient()..versionTexts = {'v1': 'oude inhoud'};

    await _pumpLoaded(tester, api);
    await _tapMenu(tester, 'Versies');
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
    final api = FakeApiClient()
      ..initialText = 'huidige notitie'
      ..versionTexts = {'v1': 'oude inhoud'};

    await _pumpLoaded(tester, api);
    await _tapMenu(tester, 'Versies');
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
    final api = FakeApiClient()
      ..initialText = 'plat'
      ..versionTexts = {'v1': '- melk\n- **eieren**'};

    await _pumpLoaded(tester, api);
    await _tapMenu(tester, 'Versies');
    await tester.tap(find.byType(ListTile).first);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Terugzetten'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ja, terugzetten'));
    await tester.pumpAndSettle();

    await _tapMenu(tester, 'Opslaan');
    expect(api.lastSavedText, '- melk\n- **eieren**');
  });

  testWidgets('de dropdown toont alle documenten in de ingestelde volgorde', (WidgetTester tester) async {
    await _pumpLoaded(tester, _multiDocumentApi());

    // Dicht: het geselecteerde document staat in de AppBar.
    expect(find.byKey(const ValueKey('documentkeuze')), findsOneWidget);
    expect(find.text('todo'), findsOneWidget);

    // De keuzelijst staat in de volgorde die de backend teruggaf.
    final dropdown = tester.widget<DropdownButton<String>>(find.byKey(const ValueKey('documentkeuze')));
    expect(dropdown.items!.map((item) => item.value), ['note', 'recepten', 'klussen']);

    // Open: alle titels zijn aantikbaar.
    await tester.tap(find.byKey(const ValueKey('documentkeuze')));
    await tester.pumpAndSettle();
    for (final title in ['todo', 'recepten', 'klussen']) {
      expect(find.text(title), findsWidgets, reason: 'titel $title ontbreekt in de dropdown');
    }
  });

  testWidgets('wisselen van document slaat eerst op en laadt daarna de andere tekst', (WidgetTester tester) async {
    final api = _multiDocumentApi();

    await _pumpLoaded(tester, api);
    _controllerOf(tester).document.insert(0, 'extra ');
    await tester.pump();

    await _chooseDocument(tester, 'recepten');

    // Eerst het openstaande werk van 'todo' opgeslagen ...
    expect(api.saveCallCount, 1);
    expect(api.lastSavedDocumentId, 'note');
    expect(api.lastSavedText, 'extra bestaande notitie');
    // ... en daarna pas de andere tekst geladen.
    expect(_controllerOf(tester).document.toPlainText(), 'pannenkoeken\n');

    // Opslaan gaat vanaf nu naar het gekozen document.
    _controllerOf(tester).document.insert(0, 'meer ');
    await tester.pump();
    await _tapMenu(tester, 'Opslaan');
    expect(api.lastSavedDocumentId, 'recepten');
    expect(api.lastSavedText, 'meer pannenkoeken');
  });

  testWidgets('bij een mislukte save wordt er niet gewisseld en blijft de tekst staan', (WidgetTester tester) async {
    final api = _multiDocumentApi()..saveError = Exception('netwerkfout');

    await _pumpLoaded(tester, api);
    _controllerOf(tester).document.insert(0, 'extra ');
    await tester.pump();

    await _chooseDocument(tester, 'recepten');

    expect(find.textContaining('Opslaan mislukt'), findsOneWidget);
    expect(_controllerOf(tester).document.toPlainText(), 'extra bestaande notitie\n');
    expect(find.text('todo'), findsOneWidget);
    expect(find.text('recepten'), findsNothing);

    // Voorkomt een onopgevangen fout tijdens de teardown (best-effort save in dispose).
    api.saveError = null;
    await _dismissSnackBar(tester);
  });

  testWidgets('na herstart opent de app het laatst gekozen document', (WidgetTester tester) async {
    final api = _multiDocumentApi();

    await _pumpLoaded(tester, api);
    await _chooseDocument(tester, 'klussen');
    expect((await SharedPreferences.getInstance()).getString('notes_editor_document_id'), 'klussen');

    await tester.pumpWidget(const SizedBox());
    await _pumpLoaded(tester, _multiDocumentApi(), resetPreferences: false);
    expect(find.text('klussen'), findsOneWidget);
    expect(_controllerOf(tester).document.toPlainText(), 'schuur verven\n');
  });

  testWidgets('een niet meer bestaand bewaard document valt terug op het eerste', (WidgetTester tester) async {
    await _pumpLoaded(
      tester,
      _multiDocumentApi(),
      preferences: {'notes_editor_document_id': 'verwijderd'},
    );

    expect(find.text('todo'), findsOneWidget);
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');
  });

  testWidgets('Versies werkt op het gekozen document', (WidgetTester tester) async {
    final api = _multiDocumentApi()..versionTexts = {'v1': 'oude inhoud'};

    await _pumpLoaded(tester, api);
    await _chooseDocument(tester, 'recepten');

    await _tapMenu(tester, 'Versies');
    expect(api.lastVersionsDocumentId, 'recepten');
  });

  testWidgets('na het beheerscherm schakelt de editor naar het eerste document als het huidige weg is', (
    WidgetTester tester,
  ) async {
    final api = _multiDocumentApi();

    await _pumpLoaded(tester, api);
    await _chooseDocument(tester, 'recepten');
    expect(_controllerOf(tester).document.toPlainText(), 'pannenkoeken\n');

    await _tapMenu(tester, 'Documenten beheren');
    await tester.tap(find.byTooltip('Verwijderen').at(1));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ja, verwijderen'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(find.text('todo'), findsOneWidget);
    expect(_controllerOf(tester).document.toPlainText(), 'bestaande notitie\n');
  });

  testWidgets('de AppBar toont alleen de documentkeuze en één overflow-knop', (WidgetTester tester) async {
    await _pumpLoaded(tester, _multiDocumentApi());

    final appBar = find.byType(AppBar);
    expect(find.descendant(of: appBar, matching: find.byKey(const ValueKey('documentkeuze'))), findsOneWidget);
    expect(find.descendant(of: appBar, matching: find.byKey(const ValueKey('overflowmenu'))), findsOneWidget);
    // Precies één knop rechts: het overflow-menu zelf.
    expect(find.descendant(of: appBar, matching: find.byType(IconButton)), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('overflowmenu')),
        matching: find.byType(IconButton),
      ),
      findsOneWidget,
    );
    // Geen losse actieknoppen meer in de balk.
    for (final tooltip in ['Opslaan', 'Versies', 'Documenten beheren', 'Uitloggen']) {
      expect(find.byTooltip(tooltip), findsNothing, reason: 'losse knop $tooltip zit nog in de AppBar');
    }
  });

  testWidgets('een lange documenttitel kapt af en duwt indicator noch overflow-knop uit beeld', (
    WidgetTester tester,
  ) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final api = FakeApiClient()
      ..documents = const [
        NoteDocument(id: 'note', title: 'een heel erg lange documenttitel die nooit past in de balk', order: 0),
      ]
      ..texts = {'note': 'bestaande notitie'};

    await _pumpLoaded(tester, api);
    // Wijziging => de indicator staat er ook nog naast.
    _controllerOf(tester).document.insert(0, 'extra ');
    await tester.pump();
    await tester.pump();

    expect(tester.takeException(), isNull);
    final title = tester.widget<Text>(
      find.descendant(of: find.byKey(const ValueKey('documentkeuze')), matching: find.byType(Text)).first,
    );
    expect(title.maxLines, 1);
    expect(title.overflow, TextOverflow.ellipsis);

    final screen = tester.getRect(find.byType(Scaffold));
    for (final key in [const ValueKey('opslagindicator'), const ValueKey('overflowmenu')]) {
      final box = tester.getRect(find.byKey(key));
      expect(box.right, lessThanOrEqualTo(screen.right), reason: '$key valt buiten beeld');
      expect(box.width, greaterThan(0));
    }
  });

  testWidgets('de opslag-indicator verschijnt bij een wijziging en is na een geslaagde save weg', (
    WidgetTester tester,
  ) async {
    final api = FakeApiClient();
    await _pumpLoaded(tester, api);

    final indicator = find.byKey(const ValueKey('opslagindicator'));
    // Net geladen: alles opgeslagen, dus geen symbool.
    expect(indicator, findsNothing);

    _controllerOf(tester).document.insert(0, 'extra ');
    // De changes-stream levert asynchroon; pas daarna volgt de setState.
    await tester.pump();
    await tester.pump();
    expect(indicator, findsOneWidget);
    expect(find.descendant(of: indicator, matching: find.byIcon(Icons.fiber_manual_record)), findsOneWidget);
    expect(find.byTooltip('Niet-opgeslagen wijzigingen'), findsOneWidget);

    await _tapMenu(tester, 'Opslaan');
    expect(api.saveCallCount, 1);
    expect(indicator, findsNothing);
  });

  testWidgets('tijdens het opslaan toont de indicator een voortgangsindicator', (WidgetTester tester) async {
    final api = FakeApiClient()..blockSave = true;
    await _pumpLoaded(tester, api);

    _controllerOf(tester).document.insert(0, 'extra ');
    await tester.pump();
    await tester.pump();

    await tester.tap(find.byKey(const ValueKey('overflowmenu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Opslaan'));
    // Geen pumpAndSettle: de voortgangsindicator blijft frames plannen.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final indicator = find.byKey(const ValueKey('opslagindicator'));
    expect(indicator, findsOneWidget);
    expect(find.descendant(of: indicator, matching: find.byType(CircularProgressIndicator)), findsOneWidget);

    // Opslaan is uitgeschakeld zolang er een save loopt.
    await tester.tap(find.byKey(const ValueKey('overflowmenu')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(_menuItemEnabled(tester, 'Opslaan'), isFalse);
    await tester.tapAt(const Offset(5, 400));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    api.completeSave();
    await tester.pump();
    await tester.pump();
    expect(indicator, findsNothing);
  });

  testWidgets('het overflow-menu bevat de vier acties in de afgesproken volgorde', (WidgetTester tester) async {
    await _pumpLoaded(tester, FakeApiClient());

    await tester.tap(find.byKey(const ValueKey('overflowmenu')));
    await tester.pumpAndSettle();

    for (final label in ['Opslaan', 'Documenten beheren', 'Versies', 'Uitloggen']) {
      expect(find.text(label), findsOneWidget, reason: 'menu-item $label ontbreekt');
    }
    expect(_menuLabels(tester), ['Opslaan', 'Documenten beheren', 'Versies', 'Uitloggen']);
    // Zonder lopende save is Opslaan gewoon bruikbaar.
    expect(_menuItemEnabled(tester, 'Opslaan'), isTrue);
  });

  testWidgets('Uitloggen in het overflow-menu roept de callback aan', (WidgetTester tester) async {
    var loggedOut = 0;
    SharedPreferences.setMockInitialValues(const {});
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: FlutterQuillLocalizations.localizationsDelegates,
        supportedLocales: FlutterQuillLocalizations.supportedLocales,
        home: NotesEditorScreen(api: FakeApiClient(), onLoggedOut: () => loggedOut++),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await _tapMenu(tester, 'Uitloggen');
    expect(loggedOut, 1);
  });

  testWidgets('Versies en Documenten beheren openen vanuit het menu hun eigen scherm', (WidgetTester tester) async {
    final api = FakeApiClient()..versionTexts = {'v1': 'oude inhoud'};
    await _pumpLoaded(tester, api);

    await _tapMenu(tester, 'Versies');
    expect(find.text('Versies'), findsWidgets);
    expect(api.lastVersionsDocumentId, 'note');
    await tester.pageBack();
    await tester.pumpAndSettle();

    await _tapMenu(tester, 'Documenten beheren');
    expect(find.byType(NoteDocumentsScreen), findsOneWidget);
  });
}

/// Drie documenten met eigen tekst; 'todo' is het standaarddocument.
FakeApiClient _multiDocumentApi() => FakeApiClient()
  ..documents = const [
    NoteDocument(id: 'note', title: 'todo', order: 0),
    NoteDocument(id: 'recepten', title: 'recepten', order: 1),
    NoteDocument(id: 'klussen', title: 'klussen', order: 2),
  ]
  ..texts = {'note': 'bestaande notitie', 'recepten': 'pannenkoeken', 'klussen': 'schuur verven'};

/// Opent de AppBar-dropdown en kiest het document met deze titel.
Future<void> _chooseDocument(WidgetTester tester, String title) async {
  await tester.tap(find.byKey(const ValueKey('documentkeuze')));
  await tester.pumpAndSettle();
  // De titel staat zowel in de knop als in het menu; het menu-item is de laatste.
  await tester.tap(find.text(title).last);
  await tester.pumpAndSettle();
}

/// Opent het overflow-menu in de AppBar en kiest het item met dit label.
Future<void> _tapMenu(WidgetTester tester, String label) async {
  await tester.tap(find.byKey(const ValueKey('overflowmenu')));
  await tester.pumpAndSettle();
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();
}

/// De menu-items van het geopende overflow-menu, op volgorde. `find.byType`
/// vergelijkt op exacte `runtimeType`, en het waardetype van de items is
/// privé — vandaar een predicaat.
Finder _menuItems() => find.byWidgetPredicate((widget) => widget is PopupMenuItem);

List<String?> _menuLabels(WidgetTester tester) => tester
    .widgetList(_menuItems())
    .map((widget) => ((widget as PopupMenuItem).child as Text).data)
    .toList(growable: false);

bool _menuItemEnabled(WidgetTester tester, String label) => tester
    .widgetList(_menuItems())
    .map((widget) => widget as PopupMenuItem)
    .firstWhere((item) => (item.child as Text).data == label)
    .enabled;

/// Laat een getoonde SnackBar aflopen, zodat er geen timer blijft hangen na
/// het einde van de test.
Future<void> _dismissSnackBar(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 5));
  await tester.pumpAndSettle();
}

/// `find.byTooltip` levert de Tooltip-widget, niet de knop zelf.
VoidCallback? _onPressedOf(WidgetTester tester, String tooltip) =>
    tester.widget<IconButton>(find.ancestor(of: find.byTooltip(tooltip), matching: find.byType(IconButton))).onPressed;
