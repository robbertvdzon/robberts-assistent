import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Praat met `robberts-assistent-backend`. Zelfde patroon als de softwarefactory
/// dashboard-frontend's `ApiClient`: bewaart het sessie-token in `SharedPreferences`,
/// stuurt 'm als Bearer-header mee met elke call.
class ApiClient {
  static const baseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '');
  static const _tokenKey = 'robberts_assistent_token';
  static const _usernameKey = 'robberts_assistent_username';

  String? token;
  String? storedUsername;

  Future<void> restoreSession() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString(_tokenKey);
    storedUsername = prefs.getString(_usernameKey);
  }

  /// Ruilt een Google ID-token in voor een eigen sessie-token bij de backend
  /// (`POST /api/v1/auth/google`).
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

  Future<Map<String, dynamic>> getJson(String path) async {
    final response = await http.get(Uri.parse('$baseUrl$path'), headers: authHeaders());
    await _throwOnError(response);
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> postJson(String path, [Map<String, dynamic> body = const {}]) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    await _throwOnError(response);
    if (response.body.isEmpty) return {};
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> putJson(String path, [Map<String, dynamic> body = const {}]) async {
    final response = await http.put(
      Uri.parse('$baseUrl$path'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    await _throwOnError(response);
    if (response.body.isEmpty) return {};
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> patchJson(String path) async {
    final response = await http.patch(Uri.parse('$baseUrl$path'), headers: authHeaders());
    await _throwOnError(response);
    if (response.body.isEmpty) return {};
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  /// Registreert het FCM-device-token bij de backend, zodat de agent er push naartoe kan sturen.
  Future<void> registerFcmToken(String token) async {
    await postJson('/api/v1/fcm/token', {'token': token});
  }

  Future<void> _delete(String path) async {
    final response = await http.delete(Uri.parse('$baseUrl$path'), headers: authHeaders());
    await _throwOnError(response);
  }

  // -- Assistent-gesprekken -----------------------------------------------------
  /// Stuurt een tekstbericht + optionele foto's naar de assistent (multipart POST
  /// `/api/v1/assistant/chat`) en geeft het antwoord + bijgewerkte gesprek terug. Zonder
  /// `conversationId` maakt de backend een nieuw gesprek aan.
  Future<AssistantChatReply> assistantChat({
    required String message,
    String? conversationId,
    List<AssistantAttachment> photos = const [],
  }) async {
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl/api/v1/assistant/chat'));
    request.headers.addAll(authHeaders());
    request.fields['message'] = message;
    if (conversationId != null) request.fields['conversationId'] = conversationId;
    for (final photo in photos) {
      request.files.add(http.MultipartFile.fromBytes(
        'photos',
        photo.bytes,
        filename: photo.filename,
        contentType: MediaType.parse(photo.contentType),
      ));
    }
    final response = await http.Response.fromStream(await request.send());
    await _throwOnError(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return AssistantChatReply(
      conversationId: body['conversationId'] as String,
      title: body['title'] as String,
      reply: body['reply'] as String,
    );
  }

  /// Lijst van gesprekken (`GET /api/v1/assistant/conversations`), meest recent eerst.
  /// `includeArchived` toont ook gearchiveerde gesprekken; `limit`/`offset` pagineren.
  Future<List<AssistantConversationSummary>> assistantConversations({
    bool includeArchived = false,
    int? limit,
    int offset = 0,
  }) async {
    final query = {
      'includeArchived': includeArchived.toString(),
      'offset': offset.toString(),
      if (limit != null) 'limit': limit.toString(),
    };
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/assistant/conversations').replace(queryParameters: query),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final list = jsonDecode(response.body) as List;
    return list
        .map((e) => AssistantConversationSummary.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Archiveert een gesprek (`PATCH .../archive`) zodat het niet meer in de standaardlijst staat.
  Future<void> archiveConversation(String id) => patchJson('/api/v1/assistant/conversations/$id/archive');

  /// Haalt een gesprek terug uit het archief (`PATCH .../unarchive`).
  Future<void> unarchiveConversation(String id) => patchJson('/api/v1/assistant/conversations/$id/unarchive');

  /// Verwijdert een gesprek definitief (`DELETE /api/v1/assistant/conversations/{id}`).
  Future<void> deleteConversation(String id) => _delete('/api/v1/assistant/conversations/$id');

  /// Volledig gesprek inclusief berichten (`GET /api/v1/assistant/conversations/{id}`).
  Future<AssistantConversationDetail> assistantConversation(String id) async {
    final body = await getJson('/api/v1/assistant/conversations/$id');
    return AssistantConversationDetail.fromJson(body);
  }

  /// Haalt de ruwe bytes van een eerder verstuurde gespreksfoto op
  /// (`GET /api/v1/assistant/photos/{id}`, auth-gated, vandaar geen kale `Image.network`-URL).
  Future<Uint8List> fetchAssistantPhoto(String photoId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/assistant/photos/$photoId'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return response.bodyBytes;
  }

  // -- Geheugen -----------------------------------------------------------------
  /// De volledige geheugen-tekst (`GET /api/v1/assistant/memory`).
  Future<String> getMemoryText() async {
    final body = await getJson('/api/v1/assistant/memory');
    return body['text']?.toString() ?? '';
  }

  /// Slaat de volledige geheugen-tekst op (`PUT /api/v1/assistant/memory`).
  Future<void> saveMemoryText(String text) => putJson('/api/v1/assistant/memory', {'text': text});

  // -- Morgen-briefing ----------------------------------------------------------
  /// De dagelijkse 'Morgen'-briefing (`GET /api/v1/briefing`): pluggable secties (kite/
  /// strandfiets, agenda, weektaken, moestuin), zie backend `BriefingSectionProvider`.
  Future<List<BriefingSection>> getBriefing() async {
    final body = await getJson('/api/v1/briefing');
    return (body['sections'] as List? ?? [])
        .map((e) => BriefingSection.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  /// Voert een generieke één-tap-actie uit die bij een briefing-item hoort (bv. "reminder
  /// aanmaken" bij een afspraak zonder reminder) — de app kent de betekenis van het endpoint niet,
  /// stuurt gewoon de door de backend meegegeven `endpoint`/`payload` door.
  Future<void> runBriefingAction(BriefingAction action) => postJson(action.endpoint, action.payload);

  // -- Reminders --------------------------------------------------------------
  Future<List<Reminder>> listReminders() async {
    final body = await getJson('/api/v1/reminders');
    return (body['reminders'] as List).map((e) => Reminder.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> createReminder({required String message, required DateTime at, Recurrence? recurrence}) async {
    await postJson('/api/v1/reminders', {
      'message': message,
      'dueAt': at.toUtc().toIso8601String(),
      if (recurrence != null) 'recurrence': recurrence.toJson(),
    });
  }

  Future<void> deleteReminder(String id) => _delete('/api/v1/reminders/$id');

  // -- Alarms -----------------------------------------------------------------
  Future<List<Alarm>> listAlarms() async {
    final body = await getJson('/api/v1/alarms');
    return (body['alarms'] as List).map((e) => Alarm.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> createAlarm({required String message, required DateTime at, Recurrence? recurrence}) async {
    await postJson('/api/v1/alarms', {
      'message': message,
      'time': at.toUtc().toIso8601String(),
      if (recurrence != null) 'recurrence': recurrence.toJson(),
    });
  }

  Future<void> deleteAlarm(String id) => _delete('/api/v1/alarms/$id');

  // -- Koppelingen ------------------------------------------------------------
  /// Status van alle externe koppelingen (geconfigureerd + echt/fallback), zonder live-test.
  Future<List<Coupling>> listCouplings() async {
    final body = await getJson('/api/v1/couplings');
    return (body['couplings'] as List).map((e) => Coupling.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Test alle koppelingen live en geeft de status inclusief testresultaat terug.
  Future<List<Coupling>> testCouplings() async {
    final body = await postJson('/api/v1/couplings/test');
    return (body['couplings'] as List).map((e) => Coupling.fromJson(e as Map<String, dynamic>)).toList();
  }

  // -- Nachtchecks --------------------------------------------------------------
  /// Alle nightly checks + hun laatste resultaat (voor het Nachtchecks-scherm).
  Future<List<NightlyCheck>> listNightlyChecks() async {
    final body = await getJson('/api/v1/nightly-checks');
    return (body['checks'] as List).map((e) => NightlyCheck.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Historie van één check, nieuwste eerst.
  Future<List<CheckRun>> nightlyCheckHistory(String id, {int limit = 30}) async {
    final body = await getJson('/api/v1/nightly-checks/$id/history?limit=$limit');
    return (body['runs'] as List).map((e) => CheckRun.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Draait een check meteen (buiten zijn schema om) en geeft het nieuwe resultaat terug.
  Future<CheckRun> runNightlyCheck(String id) async {
    final body = await postJson('/api/v1/nightly-checks/$id/run');
    return CheckRun.fromJson(body);
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

/// Herhaling: unit = DAYS/WEEKS/MONTHS/YEARS, elke [interval].
class Recurrence {
  final String unit;
  final int interval;
  const Recurrence(this.unit, this.interval);

  Map<String, dynamic> toJson() => {'unit': unit, 'interval': interval};

  static Recurrence? fromJson(Map<String, dynamic>? m) {
    if (m == null || m['interval'] == null) return null;
    return Recurrence(m['unit'] as String, (m['interval'] as num).toInt());
  }

  /// Volgende voorkomen ná [from] (kalender-correct voor maanden/jaren).
  DateTime nextAfter(DateTime from) {
    switch (unit) {
      case 'DAYS':
        return from.add(Duration(days: interval));
      case 'WEEKS':
        return from.add(Duration(days: 7 * interval));
      case 'MONTHS':
        return DateTime(from.year, from.month + interval, from.day, from.hour, from.minute, from.second);
      case 'YEARS':
        return DateTime(from.year + interval, from.month, from.day, from.hour, from.minute, from.second);
      default:
        return from.add(Duration(days: interval));
    }
  }

  String get label {
    const nl = {'DAYS': 'dag', 'WEEKS': 'week', 'MONTHS': 'maand', 'YEARS': 'jaar'};
    return 'elke $interval ${nl[unit] ?? unit.toLowerCase()}';
  }
}

/// Eén sectie van de 'Morgen'-briefing (kite/strandfiets, agenda, weektaken, moestuin, ...).
class BriefingSection {
  final String key;
  final String title;
  final String text;
  final List<BriefingItem> items;
  const BriefingSection({required this.key, required this.title, required this.text, required this.items});

  static BriefingSection fromJson(Map<String, dynamic> m) => BriefingSection(
        key: m['key'] as String,
        title: m['title'] as String,
        text: m['text'] as String,
        items: (m['items'] as List? ?? [])
            .map((e) => BriefingItem.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
      );
}

/// Eén regel binnen een [BriefingSection] (bv. één afspraak), met een eventuele één-tap-actie.
class BriefingItem {
  final String text;
  final BriefingAction? action;
  const BriefingItem({required this.text, this.action});

  static BriefingItem fromJson(Map<String, dynamic> m) => BriefingItem(
        text: m['text'] as String,
        action: m['action'] == null ? null : BriefingAction.fromJson(Map<String, dynamic>.from(m['action'] as Map)),
      );
}

/// Generieke één-tap-actie bij een briefing-item (bv. "reminder aanmaken"): de app kent de
/// betekenis niet, stuurt gewoon een POST naar [endpoint] met [payload] (zie `ApiClient.runBriefingAction`).
class BriefingAction {
  final String label;
  final String endpoint;
  final Map<String, String> payload;
  const BriefingAction({required this.label, required this.endpoint, required this.payload});

  static BriefingAction fromJson(Map<String, dynamic> m) => BriefingAction(
        label: m['label'] as String,
        endpoint: m['endpoint'] as String,
        payload: Map<String, String>.from(m['payload'] as Map? ?? {}),
      );
}

class Reminder {
  final String id;
  final String message;
  final DateTime dueAt;
  final Recurrence? recurrence;
  final bool active;
  const Reminder({required this.id, required this.message, required this.dueAt, this.recurrence, required this.active});

  static Reminder fromJson(Map<String, dynamic> m) => Reminder(
        id: m['id'] as String,
        message: m['message'] as String,
        dueAt: DateTime.parse(m['dueAt'] as String).toLocal(),
        recurrence: Recurrence.fromJson(m['recurrence'] as Map<String, dynamic>?),
        active: m['active'] as bool? ?? true,
      );
}

/// Een foto-bijlage voor een assistent-bericht.
class AssistantAttachment {
  final Uint8List bytes;
  final String filename;
  final String contentType;
  const AssistantAttachment({required this.bytes, required this.filename, required this.contentType});
}

/// Het antwoord van de assistent op één chat-beurt.
class AssistantChatReply {
  final String conversationId;
  final String title;
  final String reply;
  const AssistantChatReply({required this.conversationId, required this.title, required this.reply});
}

/// Eén regel in de gesprekkenlijst.
class AssistantConversationSummary {
  final String conversationId;
  final String title;
  final DateTime updatedAt;
  final bool archived;
  const AssistantConversationSummary({
    required this.conversationId,
    required this.title,
    required this.updatedAt,
    required this.archived,
  });

  static AssistantConversationSummary fromJson(Map<String, dynamic> m) => AssistantConversationSummary(
        conversationId: m['conversationId'] as String,
        title: m['title'] as String,
        updatedAt: DateTime.parse(m['updatedAt'] as String).toLocal(),
        archived: m['archived'] as bool? ?? false,
      );
}

/// Eén bericht binnen een gesprek, zoals teruggegeven door de backend.
class AssistantConversationMessage {
  final String id;
  final String role;
  final String text;
  final List<String> imageIds;
  const AssistantConversationMessage({
    required this.id,
    required this.role,
    required this.text,
    required this.imageIds,
  });

  bool get fromUser => role == 'user';

  static AssistantConversationMessage fromJson(Map<String, dynamic> m) => AssistantConversationMessage(
        id: m['id'] as String,
        role: m['role'] as String,
        text: m['text'] as String,
        imageIds: (m['imageIds'] as List? ?? const []).map((e) => e as String).toList(),
      );
}

/// Volledig gesprek inclusief berichten.
class AssistantConversationDetail {
  final String conversationId;
  final String title;
  final List<AssistantConversationMessage> messages;
  const AssistantConversationDetail({required this.conversationId, required this.title, required this.messages});

  static AssistantConversationDetail fromJson(Map<String, dynamic> m) => AssistantConversationDetail(
        conversationId: m['conversationId'] as String,
        title: m['title'] as String,
        messages: (m['messages'] as List)
            .map((e) => AssistantConversationMessage.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class Alarm {
  final String id;
  final String message;
  final DateTime time;
  final Recurrence? recurrence;
  final bool active;
  const Alarm({required this.id, required this.message, required this.time, this.recurrence, required this.active});

  static Alarm fromJson(Map<String, dynamic> m) => Alarm(
        id: m['id'] as String,
        message: m['message'] as String,
        time: DateTime.parse(m['time'] as String).toLocal(),
        recurrence: Recurrence.fromJson(m['recurrence'] as Map<String, dynamic>?),
        active: m['active'] as bool? ?? true,
      );
}

/// Status van één externe koppeling (voor het Koppelingen-scherm).
class Coupling {
  final String id;
  final String name;
  final String description;
  final bool configured;

  /// `'echt'` (echte koppeling actief) of `'fallback'` (stub/in-memory/log).
  final String mode;
  final CouplingTest? test;

  const Coupling({
    required this.id,
    required this.name,
    required this.description,
    required this.configured,
    required this.mode,
    this.test,
  });

  bool get isReal => mode == 'echt';

  static Coupling fromJson(Map<String, dynamic> m) => Coupling(
        id: m['id'] as String,
        name: m['name'] as String,
        description: m['description'] as String,
        configured: m['configured'] as bool? ?? false,
        mode: m['mode'] as String? ?? 'fallback',
        test: m['test'] == null ? null : CouplingTest.fromJson(m['test'] as Map<String, dynamic>),
      );
}

/// Uitkomst van de live-test van een koppeling.
class CouplingTest {
  final bool ok;
  final String detail;
  final int durationMs;

  const CouplingTest({required this.ok, required this.detail, required this.durationMs});

  static CouplingTest fromJson(Map<String, dynamic> m) => CouplingTest(
        ok: m['ok'] as bool? ?? false,
        detail: m['detail'] as String? ?? '',
        durationMs: (m['durationMs'] as num?)?.toInt() ?? 0,
      );
}

/// Eén uitvoering van een nightly check.
class CheckRun {
  final DateTime ranAt;
  final bool ok;
  final String summary;
  final String? detail;

  const CheckRun({required this.ranAt, required this.ok, required this.summary, this.detail});

  static CheckRun fromJson(Map<String, dynamic> m) => CheckRun(
        ranAt: DateTime.parse(m['ranAt'] as String).toLocal(),
        ok: m['ok'] as bool? ?? false,
        summary: m['summary'] as String? ?? '',
        detail: m['detail'] as String?,
      );
}

/// Status van één nightly check (voor het Nachtchecks-scherm).
class NightlyCheck {
  final String id;
  final String name;
  final String description;
  final String cronSchedule;
  final CheckRun? lastRun;

  const NightlyCheck({
    required this.id,
    required this.name,
    required this.description,
    required this.cronSchedule,
    this.lastRun,
  });

  static NightlyCheck fromJson(Map<String, dynamic> m) => NightlyCheck(
        id: m['id'] as String,
        name: m['name'] as String,
        description: m['description'] as String,
        cronSchedule: m['cronSchedule'] as String,
        lastRun: m['lastRun'] == null ? null : CheckRun.fromJson(m['lastRun'] as Map<String, dynamic>),
      );
}
