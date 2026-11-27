import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:notes_editor/notes_api.dart';
import 'package:shared_preferences/shared_preferences.dart';

export 'package:notes_editor/notes_api.dart' show ApiException, NoteDocument, NoteVersionSummary, NotesApi;

/// Praat met `robberts-assistent-backend`. Zelfde login-/sessie-patroon als de
/// robberts_assistent-app, maar hier alleen de notities-endpoints; die zitten
/// in het gedeelde pakket `notes_editor` ([HttpNotesApi]).
class ApiClient implements NotesApi {
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

  late final NotesApi _notes = HttpNotesApi(
    baseUrl: baseUrl,
    headers: authHeaders,
    onUnauthorized: () async {
      await clearSession();
      throw const UnauthorizedException();
    },
  );

  @override
  Future<List<NoteDocument>> listNoteDocuments() => _notes.listNoteDocuments();

  @override
  Future<NoteDocument> createNoteDocument(String title) => _notes.createNoteDocument(title);

  @override
  Future<List<NoteDocument>> reorderNoteDocuments(List<String> ids) => _notes.reorderNoteDocuments(ids);

  @override
  Future<NoteDocument> renameNoteDocument(String id, String title) => _notes.renameNoteDocument(id, title);

  @override
  Future<List<NoteDocument>> deleteNoteDocument(String id) => _notes.deleteNoteDocument(id);

  @override
  Future<String> getNoteDocumentText(String id) => _notes.getNoteDocumentText(id);

  @override
  Future<void> saveNoteDocument(String id, String text) => _notes.saveNoteDocument(id, text);

  @override
  Future<List<NoteVersionSummary>> listNoteVersions(String documentId) => _notes.listNoteVersions(documentId);

  @override
  Future<String> getNoteVersion(String documentId, String versionId) => _notes.getNoteVersion(documentId, versionId);
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
