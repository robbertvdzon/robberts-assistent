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

  Future<String> getNotes() async {
    final response = await http.get(Uri.parse('$baseUrl/api/v1/notes'), headers: authHeaders());
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return body['text']?.toString() ?? '';
  }

  Future<void> saveNotes(String text) async {
    final response = await http.put(
      Uri.parse('$baseUrl/api/v1/notes'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode({'text': text}),
    );
    await _throwOnError(response);
  }

  /// De bewaarde versies van de notitie, nieuwste eerst (zonder tekst).
  Future<List<NoteVersionSummary>> listNoteVersions() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/notes/versions'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final versions = (body['versions'] as List<dynamic>? ?? []);
    return versions
        .map((e) => NoteVersionSummary.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  /// De volledige markdown-tekst van één bewaarde versie.
  Future<String> getNoteVersion(String id) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/notes/versions/$id'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return body['text']?.toString() ?? '';
  }

  Future<void> _throwOnError(http.Response response) async {
    if (response.statusCode < 400) return;
    if (response.statusCode == 401) {
      await clearSession();
      throw const UnauthorizedException();
    }
    throw Exception('HTTP ${response.statusCode}: ${response.body}');
  }
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
