import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Praat met `robberts-assistent-backend`. Zelfde login-/sessie-patroon als de
/// robberts_assistent-app, maar hier alleen de notities-endpoints.
class ApiClient {
  static const baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '');
  static const _tokenKey = 'notities_token';
  static const _usernameKey = 'notities_username';

  String? token;
  String? storedUsername;

  Future<void> restoreSession() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString(_tokenKey);
    storedUsername = prefs.getString(_usernameKey);
  }

  Future<void> loginWithGoogle(String idToken) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/v1/auth/google'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'idToken': idToken}),
    );
    if (response.statusCode >= 400) {
      throw GoogleLoginRejectedException(_extractMessage(response));
    }
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    token = body['token'] as String;
    storedUsername = body['username'] as String?;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenKey, token!);
    if (storedUsername != null) {
      await prefs.setString(_usernameKey, storedUsername!);
    }
  }

  Future<void> clearSession() async {
    token = null;
    storedUsername = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
    await prefs.remove(_usernameKey);
  }

  Map<String, String> authHeaders() => {
    if (token != null) 'Authorization': 'Bearer $token',
  };

  /// Alle notitiedocumenten in de ingestelde volgorde. Deze aanroep triggert
  /// backend-side ook de migratie naar het 'todo'-document.
  Future<List<NoteDocument>> listNoteDocuments() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/notes/documents'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return _documentsOf(response);
  }

  /// Nieuw, leeg document onderaan de volgorde.
  Future<NoteDocument> createNoteDocument(String title) async {
    final response = await http.post(
      Uri.parse('$baseUrl/api/v1/notes/documents'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode({'title': title}),
    );
    await _throwOnError(response);
    return NoteDocument.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Nieuwe volgorde; levert de bijgewerkte lijst terug.
  Future<List<NoteDocument>> reorderNoteDocuments(List<String> ids) async {
    final response = await http.put(
      Uri.parse('$baseUrl/api/v1/notes/documents/order'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode({'ids': ids}),
    );
    await _throwOnError(response);
    return _documentsOf(response);
  }

  Future<NoteDocument> renameNoteDocument(String id, String title) async {
    final response = await http.put(
      Uri.parse('$baseUrl/api/v1/notes/documents/$id/title'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode({'title': title}),
    );
    await _throwOnError(response);
    return NoteDocument.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Verwijdert het document inclusief zijn versies; levert de overgebleven
  /// documenten terug.
  Future<List<NoteDocument>> deleteNoteDocument(String id) async {
    final response = await http.delete(
      Uri.parse('$baseUrl/api/v1/notes/documents/$id'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return _documentsOf(response);
  }

  /// De markdown-tekst van één document.
  Future<String> getNoteDocumentText(String id) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/notes/documents/$id'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return body['text']?.toString() ?? '';
  }

  /// Slaat de markdown-tekst van één document op (backend maakt er een
  /// versie-record bij aan).
  Future<void> saveNoteDocument(String id, String text) async {
    final response = await http.put(
      Uri.parse('$baseUrl/api/v1/notes/documents/$id'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode({'text': text}),
    );
    await _throwOnError(response);
  }

  /// De bewaarde versies van één document, nieuwste eerst (zonder tekst).
  Future<List<NoteVersionSummary>> listNoteVersions(String documentId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/notes/documents/$documentId/versions'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final versions = (body['versions'] as List<dynamic>? ?? []);
    return versions
        .map((e) => NoteVersionSummary.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  /// De volledige markdown-tekst van één bewaarde versie van één document.
  Future<String> getNoteVersion(String documentId, String versionId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/notes/documents/$documentId/versions/$versionId'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return body['text']?.toString() ?? '';
  }

  List<NoteDocument> _documentsOf(http.Response response) {
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final documents = (body['documents'] as List<dynamic>? ?? []);
    return documents
        .map((e) => NoteDocument.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<void> _throwOnError(http.Response response) async {
    if (response.statusCode < 400) return;
    if (response.statusCode == 401) {
      await clearSession();
      throw const UnauthorizedException();
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

class UnauthorizedException implements Exception {
  const UnauthorizedException();
  @override
  String toString() => 'Sessie verlopen. Log opnieuw in.';
}

class GoogleLoginRejectedException implements Exception {
  final String message;
  const GoogleLoginRejectedException(this.message);
  @override
  String toString() => message;
}

String _extractMessage(http.Response response) {
  try {
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final message = body['message'] as String?;
    if (message != null && message.isNotEmpty && message != 'No message available') {
      return message;
    }
  } catch (_) {
    // Geen JSON-body; val terug op de ruwe tekst.
  }
  return 'Inloggen mislukt (HTTP ${response.statusCode}).';
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
