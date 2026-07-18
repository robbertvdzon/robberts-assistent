import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Praat met `robberts-assistent-backend`. Bewaart het sessie-token in
/// `SharedPreferences` en stuurt 'm als Bearer-header mee met elke call.
/// Web: lege baseUrl (relatieve /api-paden, same-origin via nginx-proxy).
/// Mobiel: API_BASE_URL wijst naar de publieke backend-URL.
class ApiClient {
  static const baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '');
  static const _tokenKey = 'groentetuin_token';
  static const _usernameKey = 'groentetuin_username';

  String? token;
  String? storedUsername;

  Future<void> restoreSession() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString(_tokenKey);
    storedUsername = prefs.getString(_usernameKey);
  }

  /// Ruilt een Google ID-token in voor een eigen sessie-token (`POST /api/v1/auth/google`).
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

  /// De test-call: geauthenticeerde GET /api/v1/ping. Geeft de body terug (o.a. status "ok").
  Future<Map<String, dynamic>> ping() async {
    final response = await http.get(Uri.parse('$baseUrl/api/v1/ping'), headers: authHeaders());
    if (response.statusCode == 401) {
      await clearSession();
      throw const UnauthorizedException();
    }
    if (response.statusCode >= 400) {
      throw Exception('HTTP ${response.statusCode}: ${response.body}');
    }
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }
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
    // Geen JSON-body; val terug op de statuscode.
  }
  return 'Inloggen mislukt (HTTP ${response.statusCode}).';
}
