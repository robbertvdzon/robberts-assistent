import 'package:notities/api_client.dart';

/// In-memory [ApiClient] voor de widget-tests: houdt documenten, teksten en
/// versies vast en registreert wat er is aangeroepen.
class FakeApiClient extends ApiClient {
  FakeApiClient({this.saveError});

  /// Document-id => markdown-tekst.
  Map<String, String> texts = {'note': 'bestaande notitie'};

  /// De documenten zoals de backend ze zou teruggeven (op volgorde).
  List<NoteDocument> documents = [const NoteDocument(id: 'note', title: 'todo', order: 0)];

  /// Versie-id => markdown-tekst van het document waarvan versies worden
  /// opgevraagd; de lijst wordt hieruit afgeleid.
  Map<String, String> versionTexts = {};
  DateTime versionSavedAt = DateTime.now();

  String? lastSavedText;
  String? lastSavedDocumentId;
  String? lastVersionsDocumentId;
  var saveCallCount = 0;
  Object? saveError;

  /// Gooit deze fout bij het aanmaken/hernoemen (bv. een dubbele titel).
  Object? writeError;

  /// De tekst van het standaarddocument; handig in de bestaande tests.
  set initialText(String value) => texts['note'] = value;

  @override
  Future<List<NoteDocument>> listNoteDocuments() async => List.unmodifiable(documents);

  @override
  Future<String> getNoteDocumentText(String id) async => texts[id] ?? '';

  @override
  Future<void> saveNoteDocument(String id, String text) async {
    saveCallCount++;
    if (saveError != null) throw saveError!;
    texts[id] = text;
    lastSavedDocumentId = id;
    lastSavedText = text;
  }

  @override
  Future<NoteDocument> createNoteDocument(String title) async {
    if (writeError != null) throw writeError!;
    final document = NoteDocument(id: 'doc-${documents.length + 1}', title: title, order: documents.length);
    documents = [...documents, document];
    texts[document.id] = '';
    return document;
  }

  @override
  Future<NoteDocument> renameNoteDocument(String id, String title) async {
    if (writeError != null) throw writeError!;
    final index = documents.indexWhere((document) => document.id == id);
    final renamed = NoteDocument(id: id, title: title, order: documents[index].order);
    documents = [...documents]..[index] = renamed;
    return renamed;
  }

  @override
  Future<List<NoteDocument>> deleteNoteDocument(String id) async {
    if (writeError != null) throw writeError!;
    documents = [
      for (final document in documents)
        if (document.id != id) document,
    ];
    texts.remove(id);
    return _renumbered();
  }

  @override
  Future<List<NoteDocument>> reorderNoteDocuments(List<String> ids) async {
    if (writeError != null) throw writeError!;
    documents = [
      for (final id in ids) documents.firstWhere((document) => document.id == id),
    ];
    return _renumbered();
  }

  @override
  Future<List<NoteVersionSummary>> listNoteVersions(String documentId) async {
    lastVersionsDocumentId = documentId;
    return versionTexts.keys
        .map((id) => NoteVersionSummary(id: id, savedAt: versionSavedAt))
        .toList(growable: false);
  }

  @override
  Future<String> getNoteVersion(String documentId, String versionId) async => versionTexts[versionId]!;

  /// Zoals de backend: posities altijd dicht op 0..n-1.
  List<NoteDocument> _renumbered() {
    documents = [
      for (var i = 0; i < documents.length; i++)
        NoteDocument(id: documents[i].id, title: documents[i].title, order: i),
    ];
    return List.unmodifiable(documents);
  }
}
