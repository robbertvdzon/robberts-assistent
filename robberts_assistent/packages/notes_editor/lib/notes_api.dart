import 'dart:convert';

import 'package:http/http.dart' as http;

/// Wat de notitieschermen van de backend nodig hebben. De notities-app en
/// robberts_assistent leveren elk hun eigen sessie aan via [HttpNotesApi].
abstract class NotesApi {
  /// Alle notitiedocumenten in de ingestelde volgorde. Deze aanroep triggert
  /// backend-side ook de migratie naar het 'todo'-document.
  Future<List<NoteDocument>> listNoteDocuments();

  /// Nieuw, leeg document onderaan de volgorde.
  Future<NoteDocument> createNoteDocument(String title);

  /// Nieuwe volgorde; levert de bijgewerkte lijst terug.
  Future<List<NoteDocument>> reorderNoteDocuments(List<String> ids);

  Future<NoteDocument> renameNoteDocument(String id, String title);

  /// Verwijdert het document inclusief zijn versies; levert de overgebleven
  /// documenten terug.
  Future<List<NoteDocument>> deleteNoteDocument(String id);

  /// De markdown-tekst van één document.
  Future<String> getNoteDocumentText(String id);

  /// Slaat de markdown-tekst van één document op (backend maakt er een
  /// versie-record bij aan).
  Future<void> saveNoteDocument(String id, String text);

  /// De bewaarde versies van één document, nieuwste eerst (zonder tekst).
  Future<List<NoteVersionSummary>> listNoteVersions(String documentId);

  /// De volledige markdown-tekst van één bewaarde versie van één document.
  Future<String> getNoteVersion(String documentId, String versionId);
}

/// [NotesApi] over HTTP tegen `robberts-assistent-backend`.
class HttpNotesApi implements NotesApi {
  HttpNotesApi({required this.baseUrl, required this.headers, required this.onUnauthorized});

  final String baseUrl;

  /// De Authorization-header van de huidige sessie.
  final Map<String, String> Function() headers;

  /// Wordt bij een 401 aangeroepen; hoort de sessie te wissen en de eigen
  /// "sessie verlopen"-fout van de app te gooien.
  final Future<void> Function() onUnauthorized;

  Uri _uri(String path) => Uri.parse('$baseUrl/api/v1/notes/documents$path');

  Map<String, String> get _jsonHeaders => {...headers(), 'Content-Type': 'application/json'};

  @override
  Future<List<NoteDocument>> listNoteDocuments() async {
    final response = await http.get(_uri(''), headers: headers());
    await _throwOnError(response);
    return _documentsOf(response);
  }

  @override
  Future<NoteDocument> createNoteDocument(String title) async {
    final response = await http.post(_uri(''), headers: _jsonHeaders, body: jsonEncode({'title': title}));
    await _throwOnError(response);
    return NoteDocument.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  @override
  Future<List<NoteDocument>> reorderNoteDocuments(List<String> ids) async {
    final response = await http.put(_uri('/order'), headers: _jsonHeaders, body: jsonEncode({'ids': ids}));
    await _throwOnError(response);
    return _documentsOf(response);
  }

  @override
  Future<NoteDocument> renameNoteDocument(String id, String title) async {
    final response = await http.put(_uri('/$id/title'), headers: _jsonHeaders, body: jsonEncode({'title': title}));
    await _throwOnError(response);
    return NoteDocument.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  @override
  Future<List<NoteDocument>> deleteNoteDocument(String id) async {
    final response = await http.delete(_uri('/$id'), headers: headers());
    await _throwOnError(response);
    return _documentsOf(response);
  }

  @override
  Future<String> getNoteDocumentText(String id) async {
    final response = await http.get(_uri('/$id'), headers: headers());
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return body['text']?.toString() ?? '';
  }

  @override
  Future<void> saveNoteDocument(String id, String text) async {
    final response = await http.put(_uri('/$id'), headers: _jsonHeaders, body: jsonEncode({'text': text}));
    await _throwOnError(response);
  }

  @override
  Future<List<NoteVersionSummary>> listNoteVersions(String documentId) async {
    final response = await http.get(_uri('/$documentId/versions'), headers: headers());
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final versions = (body['versions'] as List<dynamic>? ?? []);
    return versions.map((e) => NoteVersionSummary.fromJson(e as Map<String, dynamic>)).toList(growable: false);
  }

  @override
  Future<String> getNoteVersion(String documentId, String versionId) async {
    final response = await http.get(_uri('/$documentId/versions/$versionId'), headers: headers());
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return body['text']?.toString() ?? '';
  }

  List<NoteDocument> _documentsOf(http.Response response) {
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final documents = (body['documents'] as List<dynamic>? ?? []);
    return documents.map((e) => NoteDocument.fromJson(e as Map<String, dynamic>)).toList(growable: false);
  }

  Future<void> _throwOnError(http.Response response) async {
    if (response.statusCode < 400) return;
    if (response.statusCode == 401) {
      await onUnauthorized();
      throw const ApiException(401, 'Sessie verlopen. Log opnieuw in.');
    }
    // De documenten-endpoints geven bij 400/404/409 een Nederlandse melding in
    // `{"error": "..."}`; die is bruikbaarder dan de ruwe body.
    throw ApiException(response.statusCode, _extractError(response));
  }
}

/// Eén notitiedocument uit `GET /api/v1/notes/documents` (zonder tekst).
class NoteDocument {
  const NoteDocument({required this.id, required this.title, required this.order});

  final String id;
  final String title;
  final int order;

  factory NoteDocument.fromJson(Map<String, dynamic> json) => NoteDocument(
    id: json['id']?.toString() ?? '',
    title: json['title']?.toString() ?? '',
    order: (json['order'] as num?)?.toInt() ?? 0,
  );
}

/// Eén regel uit het versie-overzicht: het ondoorzichtige id en het opslagmoment.
class NoteVersionSummary {
  const NoteVersionSummary({required this.id, required this.savedAt});

  final String id;
  final DateTime savedAt;

  factory NoteVersionSummary.fromJson(Map<String, dynamic> json) => NoteVersionSummary(
    id: json['id']?.toString() ?? '',
    // De backend stuurt ISO-8601 in UTC; toLocal() gebeurt pas bij het tonen.
    savedAt: DateTime.parse(json['savedAt'].toString()),
  );
}

/// Een mislukte API-aanroep, met (waar de backend die meestuurt) de
/// Nederlandse foutmelding — bv. "Er bestaat al een document met die titel".
class ApiException implements Exception {
  const ApiException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  @override
  String toString() => message;
}

/// Haalt de `error`-melding uit de body; valt terug op de ruwe body.
String _extractError(http.Response response) {
  try {
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final error = body['error']?.toString();
    if (error != null && error.isNotEmpty) return error;
  } catch (_) {
    // Geen JSON-body.
  }
  return 'HTTP ${response.statusCode}: ${response.body}';
}
