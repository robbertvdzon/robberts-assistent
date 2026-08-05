import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_quill/flutter_quill.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import 'main.dart' show notitiesEditorBackground;
import 'markdown_delta.dart';
import 'note_documents_screen.dart';
import 'note_versions_screen.dart';
import 'self_update_prompt.dart';

/// De vier acties in het overflow-menu van de AppBar.
enum _EditorAction { save, documents, versions, logout }

/// Toont het gekozen notitiedocument in een WYSIWYG-editor (Quill) met een
/// compacte opmaakbalk; bovenin kiest een dropdown welk document je bewerkt.
/// Slaat vanzelf op:
/// - 10 seconden na de laatste wijziging (debounce), of
/// - meteen zodra de app naar de achtergrond gaat of gesloten wordt.
///
/// Wat er wordt opgeslagen is altijd platte markdown-tekst
/// ([deltaToMarkdown]); er komt nooit Delta-JSON in het gedeelde notitieveld.
class NotesEditorScreen extends StatefulWidget {
  const NotesEditorScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<NotesEditorScreen> createState() => _NotesEditorScreenState();
}

class _NotesEditorScreenState extends State<NotesEditorScreen> with WidgetsBindingObserver {
  final _controller = QuillController.basic();
  final _focusNode = FocusNode();
  final _scrollController = ScrollController();
  StreamSubscription<DocChange>? _documentChanges;
  Timer? _debounce;
  var _loading = true;
  var _dirty = false;
  var _saving = false;
  String? _error;
  late SharedPreferences _preferences;
  var _fontSize = _defaultFontSize;
  List<NoteDocument> _documents = const [];
  String? _documentId;

  static const _debounceDuration = Duration(seconds: 10);
  static const _fontSizePreferenceKey = 'notes_editor_font_size';
  static const _documentPreferenceKey = 'notes_editor_document_id';
  static const _fontSizes = <int>[12, 14, 16, 18, 20, 22, 24, 26, 28];
  static const _defaultFontSize = 16;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
    // Async/niet-blokkerend: de editor wacht niet op deze check.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) maybePromptSelfUpdate(context);
    });
  }

  Future<void> _load() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      final fontSize = _readFontSize(preferences);
      // Deze aanroep triggert backend-side ook de migratie naar 'todo', dus er
      // is altijd minstens één document.
      final documents = await widget.api.listNoteDocuments();
      if (documents.isEmpty) throw Exception('Geen notitiedocumenten gevonden.');
      final documentId = _preferredDocumentId(preferences, documents);
      final text = await widget.api.getNoteDocumentText(documentId);
      if (mounted) {
        setState(() {
          _preferences = preferences;
          _fontSize = fontSize;
          _documents = documents;
          _documentId = documentId;
          // Eerst het document zetten, dán pas luisteren: het initiële laden
          // mag geen save triggeren.
          _controller.document = Document.fromDelta(markdownToDelta(text));
          _listenForChanges();
          _loading = false;
        });
        unawaited(preferences.setString(_documentPreferenceKey, documentId));
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  /// Het laatst gekozen document, of — als dat niet meer bestaat — het eerste
  /// document in de ingestelde volgorde.
  String _preferredDocumentId(SharedPreferences preferences, List<NoteDocument> documents) {
    final stored = preferences.getString(_documentPreferenceKey);
    if (stored != null && documents.any((document) => document.id == stored)) return stored;
    return documents.first.id;
  }

  /// Wisselt van document. Openstaand werk van het huidige document wordt
  /// eerst opgeslagen; mislukt dat, dan wordt er niet gewisseld (de bestaande
  /// foutmelding blijft staan) zodat er geen tekst verloren gaat.
  Future<void> _switchDocument(String id) async {
    if (id == _documentId || _loading) return;
    if (!await _save()) return;
    await _openDocument(id);
  }

  /// Laadt de tekst van [id] in de editor en onthoudt de keuze lokaal.
  Future<void> _openDocument(String id) async {
    _debounce?.cancel();
    setState(() => _loading = true);
    try {
      final text = await widget.api.getNoteDocumentText(id);
      if (!mounted) return;
      setState(() {
        _documentId = id;
        _dirty = false;
        _controller.document = Document.fromDelta(markdownToDelta(text));
        _listenForChanges();
        _loading = false;
      });
      unawaited(_preferences.setString(_documentPreferenceKey, id));
    } catch (e) {
      if (mounted) {
        setState(() => _loading = false);
        _showMessage('Laden mislukt: $e');
      }
    }
  }

  /// Opent het beheerscherm en herlaadt daarna de documentenlijst; is het
  /// huidige document verwijderd, dan schakelt de editor naar het eerste.
  Future<void> _openDocuments() async {
    // Openstaand werk eerst veiligstellen, zolang het document nog bestaat.
    await _save();
    if (!mounted) return;
    await Navigator.of(
      context,
    ).push(MaterialPageRoute(builder: (_) => NoteDocumentsScreen(api: widget.api)));
    if (!mounted) return;
    try {
      final documents = await widget.api.listNoteDocuments();
      if (!mounted || documents.isEmpty) return;
      setState(() => _documents = documents);
      if (!documents.any((document) => document.id == _documentId)) {
        await _openDocument(documents.first.id);
      }
    } catch (e) {
      if (mounted) _showMessage('Laden mislukt: $e');
    }
  }

  /// Meldingen gaan als SnackBar; `hideCurrentSnackBar()` ervoor zodat ze niet
  /// stapelen, en `maybeOf` omdat de scaffold al weg kan zijn.
  void _showMessage(String message) {
    if (!mounted) return;
    final messenger = ScaffoldMessenger.maybeOf(context);
    if (messenger == null) return;
    messenger.hideCurrentSnackBar();
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  int _readFontSize(SharedPreferences preferences) {
    final stored = preferences.get(_fontSizePreferenceKey);
    if (stored is! int) return _defaultFontSize;
    if (stored < _fontSizes.first) return _fontSizes.first;
    if (stored > _fontSizes.last) return _fontSizes.last;
    return _fontSizes.contains(stored) ? stored : _defaultFontSize;
  }

  void _changeFontSize(int step) {
    final index = _fontSizes.indexOf(_fontSize);
    final newIndex = (index + step).clamp(0, _fontSizes.length - 1);
    if (newIndex == index) return;

    setState(() => _fontSize = _fontSizes[newIndex]);
    // Alleen een lokale weergavevoorkeur: het Quill-document en daarmee de
    // dirty-/autosave-flow worden niet geraakt.
    unawaited(_preferences.setInt(_fontSizePreferenceKey, _fontSize));
  }

  /// De basisstijl van de bewerkbare tekst, expliciet uit het thema afgeleid.
  ///
  /// Bewust *niet* via `DefaultStyles.getInstance(context)`: die leest
  /// `DefaultTextStyle.of(context)`, en boven de `Material` van het `Scaffold`
  /// is dat in debug-builds Flutters error-fallback (rood `0xD0FF0000`,
  /// monospace). Die lekte zo in de editor. Door de stijl hier zelf op
  /// te bouwen — met `color` en `fontFamily` expliciet gezet — maakt het niet
  /// meer uit vanaf welke `BuildContext` de stijlen worden gemaakt.
  TextStyle _baseTextStyle(BuildContext context) {
    final theme = Theme.of(context);
    final body = theme.textTheme.bodyMedium ?? const TextStyle();
    return TextStyle(
      color: theme.colorScheme.onSurface,
      fontFamily: body.fontFamily,
      fontFamilyFallback: body.fontFamilyFallback,
      fontWeight: body.fontWeight,
      letterSpacing: body.letterSpacing,
      fontSize: _fontSize.toDouble(),
      // Dezelfde regelhoogte/decoratie als Quills eigen basisstijl.
      height: 1.15,
      decoration: TextDecoration.none,
    );
  }

  /// Alleen de drie onderdelen die de documentbrede lettergrootte bepalen:
  /// [DefaultStyles.paragraph] voor gewone tekst, [DefaultStyles.lists] voor
  /// lijsttekst en [DefaultStyles.leading] voor de bulletmarkering. De rest
  /// van de stijlen komt ongewijzigd uit Quill zelf (die worden intern, onder
  /// de `Material`, met deze overschrijvingen samengevoegd).
  DefaultStyles _editorStyles(BuildContext context) {
    final style = _baseTextStyle(context);
    // Dezelfde afstanden als Quills eigen basisstijlen; alleen de tekststijl
    // wijkt af.
    const spacing = HorizontalSpacing(0, 0);
    return DefaultStyles(
      paragraph: DefaultTextBlockStyle(style, spacing, VerticalSpacing.zero, VerticalSpacing.zero, null),
      lists: DefaultListBlockStyle(
        style,
        spacing,
        const VerticalSpacing(6, 0),
        const VerticalSpacing(0, 6),
        null,
        null,
      ),
      // Quill tekent de bulletmarkering met deze aparte stijl.
      leading: DefaultTextBlockStyle(style, spacing, VerticalSpacing.zero, VerticalSpacing.zero, null),
    );
  }

  /// `_controller.document` is een nieuw [Document]-object na het laden, dus de
  /// abonnering gebeurt op die verse changes-stream.
  void _listenForChanges() {
    _documentChanges?.cancel();
    _documentChanges = _controller.document.changes.listen((_) => _onChanged());
  }

  /// Zet [_dirty] én laat de opslag-indicator hertekenen. De toekenning
  /// gebeurt hoe dan ook meteen (ook zonder mount), want `dispose()` leest
  /// [_dirty] voor de best-effort save.
  void _setDirty(bool value) {
    if (!mounted) {
      _dirty = value;
      return;
    }
    setState(() => _dirty = value);
  }

  void _onChanged() {
    _setDirty(true);
    _debounce?.cancel();
    _debounce = Timer(_debounceDuration, _save);
  }

  String _currentMarkdown() => deltaToMarkdown(_controller.document.toDelta());

  /// [force] slaat op ongeacht [_dirty], zodat de "Opslaan"-knop ook werkt
  /// als er niets is gewijzigd sinds de laatste (auto-)save.
  ///
  /// Levert `false` op als het opslaan mislukte; het wisselen van document
  /// leunt daarop.
  Future<bool> _save({bool force = false}) async {
    final documentId = _documentId;
    if (documentId == null) return true;
    if (!_dirty && !force) return true;
    _debounce?.cancel();
    // Bewust vóór de await: wijzigingen die tijdens het opslaan binnenkomen
    // markeren de notitie meteen weer als niet-opgeslagen.
    _setDirty(false);
    if (mounted) setState(() => _saving = true);
    final text = _currentMarkdown();
    try {
      await widget.api.saveNoteDocument(documentId, text);
      return true;
    } catch (e) {
      // Niet-opgeslagen wijzigingen blijven gewoon in de editor staan; de
      // volgende debounce-tik of app-pauze probeert opnieuw.
      _setDirty(true);
      _showMessage('Opslaan mislukt: $e');
      return false;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) {
      _save();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _debounce?.cancel();
    _documentChanges?.cancel();
    final documentId = _documentId;
    if (_dirty && documentId != null) {
      // Best-effort: geen await mogelijk in dispose(), dus de tekst wordt
      // opgehaald vóórdat de controller wordt vrijgegeven.
      widget.api.saveNoteDocument(documentId, _currentMarkdown());
    }
    _controller.dispose();
    _focusNode.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  bool _isActive(Attribute attribute) => _controller.getSelectionStyle().attributes.containsKey(attribute.key);

  void _toggle(Attribute attribute) {
    _controller.formatSelection(_isActive(attribute) ? Attribute.clone(attribute, null) : attribute);
  }

  /// Haalt vet/cursief/onderstreept én de bullet-opmaak van de selectie af.
  void _clearFormatting() {
    for (final attribute in [Attribute.bold, Attribute.italic, Attribute.underline, Attribute.ul]) {
      _controller.formatSelection(Attribute.clone(attribute, null));
    }
  }

  /// Opent de versielijst; komt daar een tekst uit terug, dan wordt die in het
  /// bestaande document teruggezet.
  Future<void> _openVersions() async {
    final documentId = _documentId;
    if (documentId == null) return;
    final restored = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => NoteVersionsScreen(api: widget.api, documentId: documentId),
      ),
    );
    if (restored != null && mounted) _restore(restored);
  }

  /// Vervangt de inhoud als bewerking op het bestaande document (dus niet via
  /// `_controller.document = …`), zodat de undo-historie én het
  /// changes-abonnement intact blijven: het terugzetten is met de
  /// Ongedaan maken-knop terug te draaien en de gewone debounce-autosave pikt
  /// het op.
  void _restore(String markdown) {
    // De hele inhoud inclusief de afsluitende newline wordt vervangen; die
    // newline draagt bij een bullet-regel de opmaak van de laatste regel, dus
    // die mag niet blijven staan.
    _controller.replaceText(
      0,
      _controller.document.length,
      markdownToDelta(markdown),
      const TextSelection.collapsed(offset: 0),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        // De documentnaam krijgt vrijwel de hele balk; alleen de indicator en
        // het overflow-menu staan ernaast.
        title: Row(
          children: [
            Expanded(child: _documentDropdown()),
            _saveIndicator(),
          ],
        ),
        actions: [_overflowMenu()],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            )
          : Column(
              children: [
                _toolbar(),
                const Divider(height: 1),
                // Het hele resterende vlak onder de opmaakbalk krijgt de
                // donkergrijze editor-achtergrond, ook bij een leeg of kort
                // document — vandaar de ColoredBox rondom (en niet in) de
                // editor.
                Expanded(
                  child: ColoredBox(
                    key: const ValueKey('editorachtergrond'),
                    color: notitiesEditorBackground,
                    child: QuillEditor(
                      controller: _controller,
                      focusNode: _focusNode,
                      scrollController: _scrollController,
                      config: QuillEditorConfig(
                        placeholder: 'Typ hier je notities…',
                        padding: const EdgeInsets.all(16),
                        expands: true,
                        customStyles: _editorStyles(context),
                      ),
                    ),
                  ),
                ),
              ],
            ),
    );
  }

  /// De documentkeuze in de AppBar; zolang de lijst nog niet geladen is (of
  /// het laden mislukte) staat er gewoon de app-titel.
  Widget _documentDropdown() {
    if (_documents.isEmpty || _documentId == null) return const Text('Notities');
    final theme = Theme.of(context);
    final style = theme.appBarTheme.titleTextStyle ?? theme.textTheme.titleLarge;
    return DropdownButton<String>(
      key: const ValueKey('documentkeuze'),
      value: _documentId,
      isExpanded: true,
      underline: const SizedBox.shrink(),
      style: style,
      dropdownColor: theme.colorScheme.surface,
      iconEnabledColor: style?.color,
      // Tijdens laden/opslaan niet wisselen: eerst de lopende save afronden.
      onChanged: (_loading || _saving)
          ? null
          : (id) {
              if (id != null) unawaited(_switchDocument(id));
            },
      items: _documents
          .map(
            (document) => DropdownMenuItem<String>(
              value: document.id,
              child: Text(document.title, maxLines: 1, softWrap: false, overflow: TextOverflow.ellipsis),
            ),
          )
          .toList(growable: false),
    );
  }

  /// Compacte opslagstatus naast de documentnaam: draaiend tijdens het
  /// opslaan, een bolletje zolang er niet-opgeslagen wijzigingen zijn, en
  /// niets als alles opgeslagen is.
  Widget _saveIndicator() {
    if (_saving) {
      return const Padding(
        key: ValueKey('opslagindicator'),
        padding: EdgeInsets.symmetric(horizontal: 8),
        child: Tooltip(
          message: 'Bezig met opslaan',
          child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
        ),
      );
    }
    if (_dirty) {
      return const Padding(
        key: ValueKey('opslagindicator'),
        padding: EdgeInsets.symmetric(horizontal: 8),
        child: Tooltip(
          message: 'Niet-opgeslagen wijzigingen',
          child: Icon(Icons.fiber_manual_record, size: 12),
        ),
      );
    }
    // Alles opgeslagen: rustig, geen symbool.
    return const SizedBox(width: 8);
  }

  /// Alle acties uit de oude AppBar-knoppen, nu onder één drie-puntjes-knop.
  Widget _overflowMenu() {
    return PopupMenuButton<_EditorAction>(
      key: const ValueKey('overflowmenu'),
      tooltip: 'Menu',
      onSelected: (action) {
        switch (action) {
          case _EditorAction.save:
            unawaited(_save(force: true));
          case _EditorAction.documents:
            unawaited(_openDocuments());
          case _EditorAction.versions:
            unawaited(_openVersions());
          case _EditorAction.logout:
            widget.onLoggedOut();
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem(
          value: _EditorAction.save,
          enabled: !_saving,
          child: const Text('Opslaan'),
        ),
        const PopupMenuItem(value: _EditorAction.documents, child: Text('Documenten beheren')),
        const PopupMenuItem(value: _EditorAction.versions, child: Text('Versies')),
        const PopupMenuItem(value: _EditorAction.logout, child: Text('Uitloggen')),
      ],
    );
  }

  /// Lettergrootte, undo/redo en de vijf opmaakknoppen; bewust zelfgebouwd
  /// i.p.v. `QuillSimpleToolbar`, zodat er geen andere knoppen kunnen opduiken.
  Widget _toolbar() {
    final scheme = Theme.of(context).colorScheme;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: ListenableBuilder(
        listenable: _controller,
        builder: (context, _) => Row(
          key: const ValueKey('opmaakbalk'),
          children: [
            IconButton(
              tooltip: 'Lettergrootte verkleinen',
              icon: const Text('A−'),
              color: scheme.onSurface,
              onPressed: _fontSize == _fontSizes.first ? null : () => _changeFontSize(-1),
            ),
            IconButton(
              tooltip: 'Lettergrootte vergroten',
              icon: const Text('A+'),
              color: scheme.onSurface,
              onPressed: _fontSize == _fontSizes.last ? null : () => _changeFontSize(1),
            ),
            IconButton(
              tooltip: 'Ongedaan maken',
              icon: const Icon(Icons.undo),
              color: scheme.onSurface,
              // Niets te doen => onPressed null => uitgegrijsd. Het initiële laden
              // zit niet in de historie (het abonnement start pas daarna), dus
              // direct na openen staan beide knoppen uit.
              onPressed: _controller.hasUndo ? _controller.undo : null,
            ),
            IconButton(
              tooltip: 'Opnieuw',
              icon: const Icon(Icons.redo),
              color: scheme.onSurface,
              onPressed: _controller.hasRedo ? _controller.redo : null,
            ),
            _toolbarButton(tooltip: 'Vet', icon: Icons.format_bold, attribute: Attribute.bold),
            _toolbarButton(tooltip: 'Cursief', icon: Icons.format_italic, attribute: Attribute.italic),
            _toolbarButton(tooltip: 'Onderstreept', icon: Icons.format_underlined, attribute: Attribute.underline),
            _toolbarButton(tooltip: 'Opsomming', icon: Icons.format_list_bulleted, attribute: Attribute.ul),
            IconButton(
              tooltip: 'Opmaak wissen',
              icon: const Icon(Icons.format_clear),
              color: Theme.of(context).colorScheme.onSurface,
              onPressed: _clearFormatting,
            ),
          ],
        ),
      ),
    );
  }

  Widget _toolbarButton({required String tooltip, required IconData icon, required Attribute attribute}) {
    final active = _isActive(attribute);
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: tooltip,
      icon: Icon(icon),
      // Actief is zichtbaar aan zowel de accentkleur als de gevulde achtergrond.
      color: active ? scheme.primary : scheme.onSurface,
      style: active ? IconButton.styleFrom(backgroundColor: scheme.surfaceContainerHighest) : null,
      onPressed: () => _toggle(attribute),
    );
  }
}
