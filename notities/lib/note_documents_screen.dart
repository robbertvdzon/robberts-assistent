import 'package:flutter/material.dart';

import 'api_client.dart';

/// Beheerscherm voor de notitiedocumenten: toevoegen, hernoemen, verwijderen
/// (met bevestiging) en de volgorde slepen.
///
/// Het scherm wijzigt alles meteen bij de backend; de editor herlaadt bij
/// terugkomst zelf de documentenlijst.
class NoteDocumentsScreen extends StatefulWidget {
  const NoteDocumentsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<NoteDocumentsScreen> createState() => _NoteDocumentsScreenState();
}

class _NoteDocumentsScreenState extends State<NoteDocumentsScreen> {
  var _loading = true;
  String? _error;
  List<NoteDocument> _documents = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final documents = await widget.api.listNoteDocuments();
      if (mounted) {
        setState(() {
          _documents = documents;
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

  void _report(Object error) {
    final messenger = ScaffoldMessenger.maybeOf(context);
    messenger
      ?..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(error.toString())));
  }

  Future<void> _add() async {
    final title = await _askTitle(dialogTitle: 'Nieuw document');
    if (title == null || !mounted) return;
    try {
      await widget.api.createNoteDocument(title);
      await _load();
    } catch (e) {
      if (mounted) _report(e);
    }
  }

  Future<void> _rename(NoteDocument document) async {
    final title = await _askTitle(dialogTitle: 'Document hernoemen', initial: document.title);
    if (title == null || !mounted) return;
    try {
      await widget.api.renameNoteDocument(document.id, title);
      await _load();
    } catch (e) {
      if (mounted) _report(e);
    }
  }

  Future<void> _delete(NoteDocument document) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Document verwijderen?'),
        content: Text(
          "'${document.title}' wordt verwijderd, inclusief alle bewaarde versies. "
          'Dat kan niet ongedaan gemaakt worden.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Annuleren'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Ja, verwijderen'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await widget.api.deleteNoteDocument(document.id);
      await _load();
    } catch (e) {
      if (mounted) _report(e);
    }
  }

  /// Callback van `onReorderItem`: die corrigeert `newIndex` al voor het
  /// weggehaalde item, dus hier is geen `-1` bij naar beneden slepen nodig.
  Future<void> _reorder(int oldIndex, int newIndex) async {
    final reordered = [..._documents];
    reordered.insert(newIndex, reordered.removeAt(oldIndex));
    // Meteen tonen; de backend-volgorde volgt hierna.
    setState(() => _documents = reordered);
    try {
      final documents = await widget.api.reorderNoteDocuments(
        reordered.map((d) => d.id).toList(growable: false),
      );
      if (mounted) setState(() => _documents = documents);
    } catch (e) {
      if (mounted) {
        _report(e);
        // Terug naar wat de backend echt heeft.
        await _load();
      }
    }
  }

  /// Titeldialoog voor toevoegen én hernoemen; `null` bij annuleren of een
  /// lege titel.
  Future<String?> _askTitle({required String dialogTitle, String? initial}) async {
    final title = await showDialog<String>(
      context: context,
      builder: (_) => _TitleDialog(dialogTitle: dialogTitle, initial: initial ?? ''),
    );
    if (title == null || title.isEmpty) return null;
    return title;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Documenten'),
        actions: [
          IconButton(
            tooltip: 'Document toevoegen',
            icon: const Icon(Icons.add),
            onPressed: _loading ? null : _add,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
          : ReorderableListView.builder(
              itemCount: _documents.length,
              onReorderItem: _reorder,
              itemBuilder: (context, index) {
                final document = _documents[index];
                return ListTile(
                  key: ValueKey(document.id),
                  leading: ReorderableDragStartListener(
                    index: index,
                    child: const Icon(Icons.drag_handle),
                  ),
                  title: Text(document.title, overflow: TextOverflow.ellipsis),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'Hernoemen',
                        icon: const Icon(Icons.edit),
                        onPressed: () => _rename(document),
                      ),
                      IconButton(
                        tooltip: 'Verwijderen',
                        icon: const Icon(Icons.delete),
                        // Het laatste document kan niet weg; de backend geeft
                        // daar 409 op, dus de knop is hier al uitgeschakeld.
                        onPressed: _documents.length == 1 ? null : () => _delete(document),
                      ),
                    ],
                  ),
                );
              },
            ),
    );
  }
}

/// Titeldialoog voor toevoegen én hernoemen. Bewust een eigen widget: zo leeft
/// de [TextEditingController] precies zolang het dialoog zelf, ook tijdens de
/// sluit-animatie.
class _TitleDialog extends StatefulWidget {
  const _TitleDialog({required this.dialogTitle, required this.initial});

  final String dialogTitle;
  final String initial;

  @override
  State<_TitleDialog> createState() => _TitleDialogState();
}

class _TitleDialogState extends State<_TitleDialog> {
  late final _controller = TextEditingController(text: widget.initial);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() => Navigator.of(context).pop(_controller.text.trim());

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.dialogTitle),
      content: TextField(
        controller: _controller,
        autofocus: true,
        // Zelfde grens als de backend hanteert.
        maxLength: 60,
        decoration: const InputDecoration(labelText: 'Titel'),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Annuleren')),
        TextButton(onPressed: _submit, child: const Text('Opslaan')),
      ],
    );
  }
}
