import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_quill/flutter_quill.dart';

import 'api_client.dart';
import 'markdown_delta.dart';
import 'note_versions_screen.dart';
import 'self_update_prompt.dart';

/// Toont de ene notitie-string in een WYSIWYG-editor (Quill) met een compacte
/// opmaakbalk. Slaat vanzelf op:
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
  String _status = '';

  static const _debounceDuration = Duration(seconds: 10);

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
      final text = await widget.api.getNotes();
      if (mounted) {
        setState(() {
          // Eerst het document zetten, dán pas luisteren: het initiële laden
          // mag geen save triggeren.
          _controller.document = Document.fromDelta(markdownToDelta(text));
          _listenForChanges();
          _loading = false;
        });
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

  /// `_controller.document` is een nieuw [Document]-object na het laden, dus de
  /// abonnering gebeurt op die verse changes-stream.
  void _listenForChanges() {
    _documentChanges?.cancel();
    _documentChanges = _controller.document.changes.listen((_) => _onChanged());
  }

  void _onChanged() {
    _dirty = true;
    if (mounted) setState(() => _status = '');
    _debounce?.cancel();
    _debounce = Timer(_debounceDuration, _save);
  }

  String _currentMarkdown() => deltaToMarkdown(_controller.document.toDelta());

  /// [force] slaat op ongeacht [_dirty], zodat de "Opslaan"-knop ook werkt
  /// als er niets is gewijzigd sinds de laatste (auto-)save.
  Future<void> _save({bool force = false}) async {
    if (!_dirty && !force) return;
    _debounce?.cancel();
    _dirty = false;
    if (mounted) setState(() => _saving = true);
    final text = _currentMarkdown();
    try {
      await widget.api.saveNotes(text);
      if (mounted) setState(() => _status = 'Opgeslagen');
    } catch (e) {
      // Niet-opgeslagen wijzigingen blijven gewoon in de editor staan; de
      // volgende debounce-tik of app-pauze probeert opnieuw.
      _dirty = true;
      if (mounted) setState(() => _status = 'Opslaan mislukt: $e');
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
    if (_dirty) {
      // Best-effort: geen await mogelijk in dispose(), dus de tekst wordt
      // opgehaald vóórdat de controller wordt vrijgegeven.
      widget.api.saveNotes(_currentMarkdown());
    }
    _controller.dispose();
    _focusNode.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  bool _isActive(Attribute attribute) =>
      _controller.getSelectionStyle().attributes.containsKey(attribute.key);

  void _toggle(Attribute attribute) {
    _controller.formatSelection(
      _isActive(attribute) ? Attribute.clone(attribute, null) : attribute,
    );
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
    final restored = await Navigator.of(context).push<String>(
      MaterialPageRoute(builder: (_) => NoteVersionsScreen(api: widget.api)),
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
        title: const Text('Notities'),
        actions: [
          if (_status.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Center(child: Text(_status, style: const TextStyle(fontSize: 12))),
            ),
          IconButton(
            tooltip: 'Opslaan',
            icon: _saving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save),
            onPressed: _saving ? null : () => _save(force: true),
          ),
          IconButton(
            tooltip: 'Versies',
            icon: const Icon(Icons.history),
            onPressed: _openVersions,
          ),
          IconButton(
            tooltip: 'Uitloggen',
            icon: const Icon(Icons.logout),
            onPressed: widget.onLoggedOut,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
          : Column(
              children: [
                _toolbar(),
                const Divider(height: 1),
                Expanded(
                  child: QuillEditor(
                    controller: _controller,
                    focusNode: _focusNode,
                    scrollController: _scrollController,
                    config: const QuillEditorConfig(
                      placeholder: 'Typ hier je notities…',
                      padding: EdgeInsets.all(16),
                      expands: true,
                    ),
                  ),
                ),
              ],
            ),
    );
  }

  /// Undo/redo links, daarna de vijf opmaakknoppen; bewust zelfgebouwd i.p.v.
  /// `QuillSimpleToolbar`, zodat er geen andere knoppen kunnen opduiken.
  Widget _toolbar() {
    final scheme = Theme.of(context).colorScheme;
    return ListenableBuilder(
      listenable: _controller,
      builder: (context, _) => Row(
        key: const ValueKey('opmaakbalk'),
        children: [
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
          _toolbarButton(
            tooltip: 'Onderstreept',
            icon: Icons.format_underlined,
            attribute: Attribute.underline,
          ),
          _toolbarButton(
            tooltip: 'Opsomming',
            icon: Icons.format_list_bulleted,
            attribute: Attribute.ul,
          ),
          IconButton(
            tooltip: 'Opmaak wissen',
            icon: const Icon(Icons.format_clear),
            color: Theme.of(context).colorScheme.onSurface,
            onPressed: _clearFormatting,
          ),
        ],
      ),
    );
  }

  Widget _toolbarButton({
    required String tooltip,
    required IconData icon,
    required Attribute attribute,
  }) {
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
