import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Praat met `robberts-assistent-backend`. Zelfde patroon als de softwarefactory
/// dashboard-frontend's `ApiClient`: bewaart het sessie-token in `SharedPreferences`,
/// stuurt 'm als Bearer-header mee met elke call.
class ApiClient {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );
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

  Future<NotificationPage> notifications({String? before}) async {
    final query = before == null
        ? ''
        : '?${Uri(queryParameters: {'before': before}).query}';
    final json = await getJson('/api/v1/notifications$query');
    return NotificationPage(
      items: (json['items'] as List? ?? [])
          .map(
            (item) => AppNotification.fromJson(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList(),
      nextCursor: json['nextCursor'] as String?,
    );
  }

  Future<AppNotification> markNotificationRead(String id) async =>
      AppNotification.fromJson(
        await postJson('/api/v1/notifications/${Uri.encodeComponent(id)}/read'),
      );

  Future<Map<String, dynamic>> getJson(String path) async {
    final response = await http.get(
      Uri.parse('$baseUrl$path'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> postJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: {...authHeaders(), 'Content-Type': 'application/json'},
      body: jsonEncode(body),
    );
    await _throwOnError(response);
    if (response.body.isEmpty) return {};
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> putJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
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
    final response = await http.patch(
      Uri.parse('$baseUrl$path'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    if (response.body.isEmpty) return {};
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  /// Meldt een app-start bij de backend (`POST /api/v1/app-launches`), zodat de launch-gegevens
  /// als één regel in de backend-log belanden. Fire-and-forget: zonder sessie-token wordt de
  /// launch stil overgeslagen en een mislukte post wordt genegeerd — dit mag de app nooit
  /// blokkeren of laten crashen.
  Future<void> logAppLaunch(Map<String, dynamic> launch) async {
    if (token == null) return;
    try {
      await postJson('/api/v1/app-launches', launch);
    } catch (_) {
      // Loggen van een app-start is bijzaak; de app werkt gewoon door.
    }
  }

  /// Registreert het FCM-device-token bij de backend, zodat de agent er push naartoe kan sturen.
  Future<void> registerFcmToken(String token) async {
    await postJson('/api/v1/fcm/token', {'token': token});
  }

  Future<void> _delete(String path) async {
    final response = await http.delete(
      Uri.parse('$baseUrl$path'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
  }

  // -- Assistent-gesprekken -----------------------------------------------------
  /// Stuurt een tekstbericht + optionele foto's naar de assistent (multipart POST
  /// `/api/v1/assistant/chat`) en geeft het antwoord + bijgewerkte gesprek terug. Zonder
  /// `conversationId` maakt de backend een nieuw gesprek aan. Met [voice] `true` (praatmodus: het
  /// antwoord wordt voorgelezen) gaat het extra veld `voice` mee, waarop de backend een kort
  /// spreektaal-antwoord geeft; de getypte route stuurt de vlag niet mee.
  /// Interval waarmee [assistantChat] de status van een lopende beurt ophaalt, en de maximale
  /// totale wachttijd. Aanpasbaar zodat tests niet echt hoeven te wachten.
  Duration chatPollInterval = const Duration(seconds: 2);
  Duration chatMaxWait = const Duration(minutes: 15);

  /// Stuurt een chat-beurt naar de assistent en wacht op het antwoord.
  ///
  /// Het antwoord komt via de Agent Runtime en kan langer duren dan een HTTP-request mag duren
  /// (Cloudflare kapt na ~100 s af). Daarom start dit `POST /api/v1/assistant/chat/async`
  /// (direct een `jobId` terug) en haalt daarna elke [chatPollInterval] de status op via
  /// `GET /api/v1/assistant/chat/jobs/{jobId}` tot die klaar of mislukt is. Losse netwerkfouten
  /// tijdens het wachten worden een paar keer genegeerd; de beurt loopt op de server gewoon door.
  Future<AssistantChatReply> assistantChat({
    required String message,
    String? conversationId,
    List<AssistantAttachment> photos = const [],
    bool voice = false,
  }) async {
    final request = http.MultipartRequest(
      'POST',
      Uri.parse('$baseUrl/api/v1/assistant/chat/async'),
    );
    request.headers.addAll(authHeaders());
    request.fields['message'] = message;
    if (conversationId != null) {
      request.fields['conversationId'] = conversationId;
    }
    if (voice) request.fields['voice'] = 'true';
    for (final photo in photos) {
      request.files.add(
        http.MultipartFile.fromBytes(
          'photos',
          photo.bytes,
          filename: photo.filename,
          contentType: MediaType.parse(photo.contentType),
        ),
      );
    }
    final response = await http.Response.fromStream(await request.send());
    await _throwOnError(response);
    final jobId =
        (jsonDecode(response.body) as Map<String, dynamic>)['jobId'] as String;

    final deadline = DateTime.now().add(chatMaxWait);
    var failedPolls = 0;
    while (DateTime.now().isBefore(deadline)) {
      await Future<void>.delayed(chatPollInterval);
      final http.Response poll;
      try {
        poll = await http.get(
          Uri.parse('$baseUrl/api/v1/assistant/chat/jobs/$jobId'),
          headers: authHeaders(),
        );
      } catch (_) {
        // Tijdelijke netwerkfout (bv. tijdens een herstart van de tunnel): blijf proberen.
        if (++failedPolls >= 20) rethrow;
        continue;
      }
      if (poll.statusCode >= 500 && ++failedPolls < 20) continue;
      await _throwOnError(poll);
      failedPolls = 0;
      final body = jsonDecode(poll.body) as Map<String, dynamic>;
      switch (body['status']) {
        case 'DONE':
          return AssistantChatReply(
            conversationId: body['conversationId'] as String,
            title: body['title'] as String,
            reply: body['reply'] as String,
          );
        case 'FAILED':
          throw Exception(
            (body['error'] as String?) ?? 'De assistent kon niet antwoorden.',
          );
      }
    }
    throw Exception('De assistent deed er te lang over. Probeer het opnieuw.');
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
      Uri.parse(
        '$baseUrl/api/v1/assistant/conversations',
      ).replace(queryParameters: query),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    final list = jsonDecode(response.body) as List;
    return list
        .map(
          (e) =>
              AssistantConversationSummary.fromJson(e as Map<String, dynamic>),
        )
        .toList();
  }

  /// Archiveert een gesprek (`PATCH .../archive`) zodat het niet meer in de standaardlijst staat.
  Future<void> archiveConversation(String id) =>
      patchJson('/api/v1/assistant/conversations/$id/archive');

  /// Haalt een gesprek terug uit het archief (`PATCH .../unarchive`).
  Future<void> unarchiveConversation(String id) =>
      patchJson('/api/v1/assistant/conversations/$id/unarchive');

  /// Verwijdert een gesprek definitief (`DELETE /api/v1/assistant/conversations/{id}`).
  Future<void> deleteConversation(String id) =>
      _delete('/api/v1/assistant/conversations/$id');

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
  Future<void> saveMemoryText(String text) =>
      putJson('/api/v1/assistant/memory', {'text': text});

  // -- Morgen-briefing ----------------------------------------------------------
  /// De dagelijkse 'Morgen'-briefing (`GET /api/v1/briefing`): pluggable secties (weerkaart,
  /// kite/strandfiets, agenda, weektaken, moestuin), zie backend `BriefingSectionProvider`. Levert
  /// standaard de gecachete versie (zie `updatedAt`); zonder bestaande cache bouwt de backend live op.
  Future<BriefingData> getBriefing() async {
    final body = await getJson('/api/v1/briefing');
    return BriefingData.fromJson(body);
  }

  /// Bouwt de briefing live opnieuw op en overschrijft de cache (`POST /api/v1/briefing/refresh`).
  Future<BriefingData> refreshBriefing() async {
    final body = await postJson('/api/v1/briefing/refresh');
    return BriefingData.fromJson(body);
  }

  // -- Health check ---------------------------------------------------------
  /// De systeemstatus-sectie voor de 'Health check'-tab (`GET /api/v1/briefing/health`), met een
  /// eigen `updatedAt` los van de Upcoming-briefing (zie backend `BriefingService`).
  Future<BriefingData> getHealthCheck() async {
    final body = await getJson('/api/v1/briefing/health');
    return BriefingData.fromJson(body);
  }

  /// Bouwt uitsluitend de systeemstatus-sectie live opnieuw op en overschrijft alléén de Health
  /// check-cache (`POST /api/v1/briefing/health/refresh`), zonder de Upcoming-cache te raken.
  Future<BriefingData> refreshHealthCheck() async {
    final body = await postJson('/api/v1/briefing/health/refresh');
    return BriefingData.fromJson(body);
  }

  /// Voert een generieke één-tap-actie uit die bij een briefing-item hoort (bv. "reminder
  /// aanmaken" bij een afspraak zonder reminder) — de app kent de betekenis van het endpoint niet,
  /// stuurt gewoon de door de backend meegegeven `endpoint`/`payload` door.
  Future<void> runBriefingAction(BriefingAction action) =>
      postJson(action.endpoint, action.payload);

  // -- Reminders --------------------------------------------------------------
  Future<List<Reminder>> listReminders() async {
    final body = await getJson('/api/v1/reminders');
    return (body['reminders'] as List)
        .map((e) => Reminder.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> createReminder({
    required String message,
    required DateTime at,
    Recurrence? recurrence,
  }) async {
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
    return (body['alarms'] as List)
        .map((e) => Alarm.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> createAlarm({
    required String message,
    required DateTime at,
    Recurrence? recurrence,
  }) async {
    await postJson('/api/v1/alarms', {
      'message': message,
      'time': at.toUtc().toIso8601String(),
      if (recurrence != null) 'recurrence': recurrence.toJson(),
    });
  }

  Future<void> deleteAlarm(String id) => _delete('/api/v1/alarms/$id');

  // -- Langdurige zoekopdrachten ----------------------------------------------
  Future<List<Watch>> listWatches() async {
    final body = await getJson('/api/v1/watches');
    return (body['watches'] as List)
        .map((e) => Watch.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> createWatch({
    required String title,
    required String url,
    required String instruction,
    required bool notifyOnFound,
  }) async {
    await postJson('/api/v1/watches', {
      'title': title,
      'url': url,
      'instruction': instruction,
      'notifyOnFound': notifyOnFound,
    });
  }

  Future<void> updateWatch({
    required String id,
    required String title,
    required String url,
    required String instruction,
    required bool notifyOnFound,
  }) async {
    await putJson('/api/v1/watches/$id', {
      'title': title,
      'url': url,
      'instruction': instruction,
      'notifyOnFound': notifyOnFound,
    });
  }

  Future<void> deleteWatch(String id) => _delete('/api/v1/watches/$id');

  /// Controleert nu meteen alle actieve zoekopdrachten (`POST /api/v1/watches/run-now`) en geeft
  /// de bijgewerkte lijst terug, zodat er na de run geen extra `GET` nodig is.
  /// Bevestigt dat een gevonden zoekopdracht gezien is; de dagelijkse herinnering stopt.
  Future<List<Watch>> acknowledgeWatch(String id) async {
    final body = await postJson('/api/v1/watches/$id/acknowledge');
    return (body['watches'] as List)
        .map((e) => Watch.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<Watch>> runWatchesNow() async {
    final body = await postJson('/api/v1/watches/run-now');
    return (body['watches'] as List)
        .map((e) => Watch.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  // -- Koppelingen ------------------------------------------------------------
  /// Status van alle externe koppelingen (geconfigureerd + echt/fallback), zonder live-test.
  Future<List<Coupling>> listCouplings() async {
    final body = await getJson('/api/v1/couplings');
    return (body['couplings'] as List)
        .map((e) => Coupling.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Test alle koppelingen live en geeft de status inclusief testresultaat terug.
  Future<List<Coupling>> testCouplings() async {
    final body = await postJson('/api/v1/couplings/test');
    return (body['couplings'] as List)
        .map((e) => Coupling.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  // -- Nachtchecks --------------------------------------------------------------
  /// Alle nightly checks + hun laatste resultaat (voor het Nachtchecks-scherm).
  Future<List<NightlyCheck>> listNightlyChecks() async {
    final body = await getJson('/api/v1/nightly-checks');
    return (body['checks'] as List)
        .map((e) => NightlyCheck.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Historie van één check, nieuwste eerst.
  Future<List<CheckRun>> nightlyCheckHistory(
    String id, {
    int limit = 30,
  }) async {
    final body = await getJson(
      '/api/v1/nightly-checks/$id/history?limit=$limit',
    );
    return (body['runs'] as List)
        .map((e) => CheckRun.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Draait een check meteen (buiten zijn schema om) en geeft het nieuwe resultaat terug.
  Future<CheckRun> runNightlyCheck(String id) async {
    final body = await postJson('/api/v1/nightly-checks/$id/run');
    return CheckRun.fromJson(body);
  }

  // -- Eetdagboek ---------------------------------------------------------------
  /// Nieuw eetmoment (`POST /api/v1/meals`, multipart). De backend geeft de maaltijd direct terug
  /// met status `ANALYSEREN`; het voorstel volgt later (pollen via [getMeal] of na de push).
  Future<Meal> createMeal({
    List<AssistantAttachment> photos = const [],
    String note = '',
    DateTime? eatenAt,
  }) => _mealMultipart(
    '/api/v1/meals',
    photos: photos,
    note: note,
    eatenAt: eatenAt,
  );

  /// Extra foto's (tweede bord, etiket, recept) bij een bestaand eetmoment.
  Future<Meal> addMealPhotos(
    String id, {
    List<AssistantAttachment> photos = const [],
    String note = '',
  }) => _mealMultipart('/api/v1/meals/$id/photos', photos: photos, note: note);

  Future<Meal> _mealMultipart(
    String path, {
    required List<AssistantAttachment> photos,
    required String note,
    DateTime? eatenAt,
  }) async {
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl$path'));
    request.headers.addAll(authHeaders());
    request.fields['note'] = note;
    if (eatenAt != null) {
      request.fields['eatenAt'] = eatenAt.toUtc().toIso8601String();
    }
    for (final photo in photos) {
      request.files.add(
        http.MultipartFile.fromBytes(
          'photos',
          photo.bytes,
          filename: photo.filename,
          contentType: MediaType.parse(photo.contentType),
        ),
      );
    }
    final response = await http.Response.fromStream(await request.send());
    await _throwOnError(response);
    return Meal.fromJson(
      Map<String, dynamic>.from(jsonDecode(response.body) as Map),
    );
  }

  Future<Meal> getMeal(String id) async =>
      Meal.fromJson(await getJson('/api/v1/meals/$id'));

  /// Dagoverzicht (`GET /api/v1/meals?date=jjjj-mm-dd`).
  Future<NutritionDay> getNutritionDay(DateTime date) async {
    final d =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    return NutritionDay.fromJson(await getJson('/api/v1/meals?date=$d'));
  }

  /// Bevestigt een eetmoment. Zonder [items] wordt het voorstel overgenomen.
  Future<Meal> confirmMeal(String id, {List<MealItem>? items}) async =>
      Meal.fromJson(
        await putJson('/api/v1/meals/$id', {
          if (items != null) 'items': items.map((i) => i.toJson()).toList(),
        }),
      );

  /// Niet alles opgegeten: de portie blijft staan, de dag telt alleen [percent] procent.
  Future<Meal> setMealEaten(String id, int percent) async => Meal.fromJson(
    await putJson('/api/v1/meals/$id/eaten', {'percent': percent}),
  );

  /// Beheer: alle bevestigde maaltijden met foto en hun rol in de referentiebibliotheek.
  Future<List<Meal>> getReferenceMeals() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/meals/references'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return (jsonDecode(response.body) as List)
        .map((e) => Meal.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  /// Beheer: zelf bepalen of een maaltijd als modelgerecht dient, met een eigen beschrijving.
  Future<Meal> setMealReference(
    String id, {
    required bool use,
    String? description,
  }) async => Meal.fromJson(
    await putJson('/api/v1/meals/$id/reference', {
      'use': use,
      if (description != null) 'description': description,
    }),
  );

  // -- Moestuin -------------------------------------------------------------------
  Future<Map<String, dynamic>> _gardenMultipart(
    String path, {
    Map<String, String> fields = const {},
    String fileField = 'photos',
    List<AssistantAttachment> photos = const [],
  }) async {
    final request = http.MultipartRequest('POST', Uri.parse('$baseUrl$path'));
    request.headers.addAll(authHeaders());
    request.fields.addAll(fields);
    for (final photo in photos) {
      request.files.add(
        http.MultipartFile.fromBytes(
          fileField,
          photo.bytes,
          filename: photo.filename,
          contentType: MediaType.parse(photo.contentType),
        ),
      );
    }
    final response = await http.Response.fromStream(await request.send());
    await _throwOnError(response);
    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  /// Vraag aan de moestuin-assistent, met foto's; [bedId] als de vraag over één bak gaat.
  Future<GardenChatReply> gardenChat({
    required String message,
    String? conversationId,
    String? bedId,
    List<AssistantAttachment> photos = const [],
  }) async {
    final body = await _gardenMultipart(
      '/api/v1/garden/chat',
      fields: {
        'message': message,
        if (conversationId != null) 'conversationId': conversationId,
        if (bedId != null) 'bedId': bedId,
      },
      photos: photos,
    );
    return GardenChatReply(
      conversationId: body['conversationId'] as String,
      reply: body['reply'] as String? ?? '',
    );
  }

  Future<GardenPlan> getGardenPlan() async =>
      GardenPlan.fromJson(await getJson('/api/v1/garden/plan'));

  /// Een foto of tekening van de tuin als achtergrond; [aspectRatio] is breedte gedeeld door hoogte.
  Future<GardenPlan> setGardenBackground(
    AssistantAttachment photo,
    double aspectRatio,
  ) async => GardenPlan.fromJson(
    await _gardenMultipart(
      '/api/v1/garden/plan/background',
      fields: {'aspectRatio': aspectRatio.toString()},
      fileField: 'photo',
      photos: [photo],
    ),
  );

  Future<GardenBed> createGardenBed(GardenBed bed) async =>
      GardenBed.fromJson(await postJson('/api/v1/garden/beds', bed.toJson()));

  Future<GardenBed> updateGardenBed(GardenBed bed) async => GardenBed.fromJson(
    await putJson('/api/v1/garden/beds/${bed.id}', bed.toJson()),
  );

  Future<GardenBed> getGardenBed(String id) async =>
      GardenBed.fromJson(await getJson('/api/v1/garden/beds/$id'));

  Future<void> deleteGardenBed(String id) => _delete('/api/v1/garden/beds/$id');

  Future<GardenBed> addGardenBedEntry(
    String bedId, {
    String text = '',
    List<AssistantAttachment> photos = const [],
  }) async => GardenBed.fromJson(
    await _gardenMultipart(
      '/api/v1/garden/beds/$bedId/entries',
      fields: {'text': text},
      photos: photos,
    ),
  );

  Future<void> deleteGardenBedEntry(String bedId, String entryId) =>
      _delete('/api/v1/garden/beds/$bedId/entries/$entryId');

  Future<Uint8List> fetchGardenPhoto(String photoId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/garden/photos/$photoId'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return response.bodyBytes;
  }

  // -- Vacatures ------------------------------------------------------------------
  Future<JobOverview> getJobs() async =>
      JobOverview.fromJson(await getJson('/api/v1/jobs'));

  /// Start een zoekronde op de achtergrond; het overzicht meldt `running` tot hij klaar is.
  Future<JobOverview> runJobSearch() async =>
      JobOverview.fromJson(await postJson('/api/v1/jobs/run'));

  /// Oordeel over een vacature: `INTERESSANT` of `NIET`, met de reden waarvan geleerd wordt.
  Future<JobOverview> rateJob(String id, String verdict, String reason) async =>
      JobOverview.fromJson(
        await putJson('/api/v1/jobs/$id/verdict', {
          'verdict': verdict,
          'reason': reason,
        }),
      );

  Future<JobOverview> saveJobSettings({
    required String description,
    required String preferences,
    required List<String> blockedCompanies,
  }) async => JobOverview.fromJson(
    await putJson('/api/v1/jobs/settings', {
      'description': description,
      'preferences': preferences,
      'blockedCompanies': blockedCompanies,
    }),
  );

  /// Dagopbrengst van de zonnepanelen naast de verwachting bij het weer.
  Future<SolarOverview> getSolar() async =>
      SolarOverview.fromJson(await getJson('/api/v1/solar'));

  /// Verbetering in gewone woorden; de AI rekent het eetmoment opnieuw door.
  Future<Meal> correctMeal(String id, String text) async => Meal.fromJson(
    await postJson('/api/v1/meals/$id/correct', {'text': text}),
  );

  Future<Meal> retryMeal(String id) async =>
      Meal.fromJson(await postJson('/api/v1/meals/$id/retry'));

  Future<void> deleteMeal(String id) => _delete('/api/v1/meals/$id');

  Future<Uint8List> fetchMealPhoto(String photoId) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/v1/meals/photos/$photoId'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return response.bodyBytes;
  }

  Future<String> getFoodMemory() async =>
      (await getJson('/api/v1/nutrition/memory'))['text'] as String? ?? '';

  Future<void> saveFoodMemory(String text) =>
      putJson('/api/v1/nutrition/memory', {'text': text});

  // -- Check-ins (honger en energie) ---------------------------------------------
  Future<CheckIn> recordCheckIn({
    int? hunger,
    int? energy,
    String note = '',
    String source = 'APP',
    String? mealId,
  }) async => CheckIn.fromJson(
    await postJson('/api/v1/checkins', {
      if (hunger != null) 'hunger': hunger,
      if (energy != null) 'energy': energy,
      'note': note,
      'source': source,
      if (mealId != null) 'mealId': mealId,
    }),
  );

  Future<List<CheckIn>> getCheckIns(DateTime date) async {
    final d =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final body = await getJson('/api/v1/checkins?date=$d');
    return ((body['checkIns'] as List?) ?? const [])
        .map((e) => CheckIn.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  // -- Dutjes --------------------------------------------------------------------
  /// Start een dutje (zonder [end]) of vult er achteraf een in (met [end]).
  Future<Nap> createNap({
    DateTime? start,
    DateTime? end,
    DateTime? alarmAt,
  }) async => Nap.fromJson(
    await postJson('/api/v1/naps', {
      if (start != null) 'start': start.toUtc().toIso8601String(),
      if (end != null) 'end': end.toUtc().toIso8601String(),
      if (alarmAt != null) 'alarmAt': alarmAt.toUtc().toIso8601String(),
    }),
  );

  /// Past begin en/of eind aan. Een opgegeven eind sluit een lopend dutje af.
  Future<Nap> updateNap(
    String id, {
    DateTime? start,
    DateTime? end,
    bool clearAlarm = false,
  }) async => Nap.fromJson(
    await putJson('/api/v1/naps/$id', {
      if (start != null) 'start': start.toUtc().toIso8601String(),
      if (end != null) 'end': end.toUtc().toIso8601String(),
      if (clearAlarm) 'clearAlarm': true,
    }),
  );

  Future<void> deleteNap(String id) => _delete('/api/v1/naps/$id');

  /// De dutjes van één dag plus het dutje dat nu loopt.
  Future<NapDay> getNaps(DateTime date) async {
    final d =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    return NapDay.fromJson(await getJson('/api/v1/naps?date=$d'));
  }

  /// Dutjes waarvan het eind nog ontbreekt of geschat is.
  Future<List<Nap>> getPendingNaps() async {
    final body = await getJson('/api/v1/naps/pending');
    return ((body['naps'] as List?) ?? const [])
        .map((e) => Nap.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  /// Laat de backend het eind schatten; [movementAt] is de eerste minuut met stappen na het begin.
  Future<Nap> estimateNap(String id, {DateTime? movementAt}) async =>
      Nap.fromJson(
        await postJson('/api/v1/naps/$id/estimate', {
          if (movementAt != null)
            'movementAt': movementAt.toUtc().toIso8601String(),
        }),
      );

  // -- Bewegen, verbruik en slaap (Garmin via Health Connect) ---------------------
  /// Stuurt de laatste dagen uit Health Connect naar de backend (`POST /api/v1/activity/days`).
  Future<void> syncActivityDays(List<Map<String, dynamic>> days) => postJson(
    '/api/v1/activity/days',
    {'source': 'health-connect', 'days': days},
  );

  /// De gegevens van één dag, of `null` als er voor die dag niets is.
  Future<ActivityDay?> getActivityDay(DateTime date) async {
    final d =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
    final body = await getJson('/api/v1/activity/days?from=$d&to=$d');
    final days = (body['days'] as List?) ?? const [];
    if (days.isEmpty) return null;
    return ActivityDay.fromJson(Map<String, dynamic>.from(days.first as Map));
  }

  // -- Weekoverzicht, gewicht en verbanden ----------------------------------------
  Future<WeekOverview> getWeekOverview() async =>
      WeekOverview.fromJson(await getJson('/api/v1/insights/week'));

  Future<List<Insight>> getInsights() async {
    final body = await getJson('/api/v1/insights');
    return ((body['insights'] as List?) ?? const [])
        .map((e) => Insight.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  /// Gewicht op [date] (standaard vandaag), `PUT /api/v1/activity/weight`.
  Future<void> setWeight(
    double weightKg, {
    DateTime? date,
  }) => putJson('/api/v1/activity/weight', {
    'weightKg': weightKg,
    if (date != null)
      'date':
          '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}',
  });

  // -- AI-modellen (beheer) -------------------------------------------------------
  Future<AiModels> getAiModels() async =>
      AiModels.fromJson(await getJson('/api/v1/ai-models'));

  Future<AiModels> setAiModel(String role, AiExecution execution) async =>
      AiModels.fromJson(
        await putJson('/api/v1/ai-models/$role', execution.toJson()),
      );

  /// Terug naar het standaardmodel (`DELETE /api/v1/ai-models/{role}`).
  Future<AiModels> resetAiModel(String role) async {
    final response = await http.delete(
      Uri.parse('$baseUrl/api/v1/ai-models/$role'),
      headers: authHeaders(),
    );
    await _throwOnError(response);
    return AiModels.fromJson(
      Map<String, dynamic>.from(jsonDecode(response.body) as Map),
    );
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
    if (message != null &&
        message.isNotEmpty &&
        message != 'No message available') {
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
        return DateTime(
          from.year,
          from.month + interval,
          from.day,
          from.hour,
          from.minute,
          from.second,
        );
      case 'YEARS':
        return DateTime(
          from.year + interval,
          from.month,
          from.day,
          from.hour,
          from.minute,
          from.second,
        );
      default:
        return from.add(Duration(days: interval));
    }
  }

  String get label {
    const nl = {
      'DAYS': 'dag',
      'WEEKS': 'week',
      'MONTHS': 'maand',
      'YEARS': 'jaar',
    };
    return 'elke $interval ${nl[unit] ?? unit.toLowerCase()}';
  }
}

/// De volledige 'Morgen'-briefing: de secties plus wanneer die data is opgehaald (gecachet of
/// live), zodat het scherm "Bijgewerkt om ..." kan tonen.
class BriefingData {
  final List<BriefingSection> sections;
  final DateTime updatedAt;
  const BriefingData({required this.sections, required this.updatedAt});

  static BriefingData fromJson(Map<String, dynamic> m) => BriefingData(
    sections: (m['sections'] as List? ?? [])
        .map(
          (e) => BriefingSection.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList(),
    updatedAt: DateTime.parse(m['updatedAt'] as String).toLocal(),
  );
}

/// Eén sectie van de 'Morgen'-briefing (weerkaart, kite/strandfiets, agenda, weektaken, moestuin, ...).
enum BriefingStatus {
  goed('goed'),
  letOp('let op'),
  niet('niet');

  const BriefingStatus(this.label);
  final String label;

  static BriefingStatus? fromJson(Object? value) {
    switch (value) {
      case 'GOED':
        return BriefingStatus.goed;
      case 'LET_OP':
        return BriefingStatus.letOp;
      case 'NIET':
        return BriefingStatus.niet;
      default:
        return null;
    }
  }
}

class BriefingSection {
  final String key;
  final String title;
  final String text;
  final List<BriefingItem> items;
  final BriefingStatus? status;
  final String? tileLabel;

  /// Waarom de tegel 'let op' of 'niet' toont; `null` als het goed is.
  final String? statusReason;
  const BriefingSection({
    required this.key,
    required this.title,
    required this.text,
    required this.items,
    this.status,
    this.tileLabel,
    this.statusReason,
  });

  static BriefingSection fromJson(Map<String, dynamic> m) => BriefingSection(
    key: m['key'] as String,
    title: m['title'] as String,
    text: m['text'] as String,
    items: (m['items'] as List? ?? [])
        .map((e) => BriefingItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
    status: BriefingStatus.fromJson(m['status']),
    tileLabel: m['tileLabel'] as String?,
    statusReason: m['statusReason'] as String?,
  );
}

/// Eén regel binnen een [BriefingSection] (bv. één afspraak, een weerkaart-dagdeel, of een
/// systeemstatus-onderdeel), met een eventuele één-tap-actie, afbeelding (`imageUrl`, relatief
/// pad — de app rendert dit als `Image.network` met auth-header i.p.v. platte tekst, zie
/// `SummaryScreen`) en/of `heading` (bv. per systeemstatus-onderdeel, zie `HealthCheckScreen`).
class BriefingItem {
  final String text;
  final BriefingAction? action;
  final String? imageUrl;
  final String? heading;

  /// Oordeel per onderdeel op het Health check-scherm; `null` bij gewone briefingregels.
  final BriefingStatus? status;
  const BriefingItem({
    required this.text,
    this.action,
    this.imageUrl,
    this.heading,
    this.status,
  });

  static BriefingItem fromJson(Map<String, dynamic> m) => BriefingItem(
    text: m['text'] as String,
    action: m['action'] == null
        ? null
        : BriefingAction.fromJson(
            Map<String, dynamic>.from(m['action'] as Map),
          ),
    imageUrl: m['imageUrl'] as String?,
    heading: m['heading'] as String?,
    status: BriefingStatus.fromJson(m['status']),
  );
}

/// Generieke één-tap-actie bij een briefing-item (bv. "reminder aanmaken"): de app kent de
/// betekenis niet, stuurt gewoon een POST naar [endpoint] met [payload] (zie `ApiClient.runBriefingAction`).
class BriefingAction {
  final String label;
  final String endpoint;
  final Map<String, String> payload;
  const BriefingAction({
    required this.label,
    required this.endpoint,
    required this.payload,
  });

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
  const Reminder({
    required this.id,
    required this.message,
    required this.dueAt,
    this.recurrence,
    required this.active,
  });

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
  const AssistantAttachment({
    required this.bytes,
    required this.filename,
    required this.contentType,
  });
}

/// Het antwoord van de assistent op één chat-beurt.
class AssistantChatReply {
  final String conversationId;
  final String title;
  final String reply;
  const AssistantChatReply({
    required this.conversationId,
    required this.title,
    required this.reply,
  });
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

  static AssistantConversationSummary fromJson(Map<String, dynamic> m) =>
      AssistantConversationSummary(
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

  static AssistantConversationMessage fromJson(Map<String, dynamic> m) =>
      AssistantConversationMessage(
        id: m['id'] as String,
        role: m['role'] as String,
        text: m['text'] as String,
        imageIds: (m['imageIds'] as List? ?? const [])
            .map((e) => e as String)
            .toList(),
      );
}

/// Volledig gesprek inclusief berichten.
class AssistantConversationDetail {
  final String conversationId;
  final String title;
  final List<AssistantConversationMessage> messages;
  const AssistantConversationDetail({
    required this.conversationId,
    required this.title,
    required this.messages,
  });

  static AssistantConversationDetail fromJson(Map<String, dynamic> m) =>
      AssistantConversationDetail(
        conversationId: m['conversationId'] as String,
        title: m['title'] as String,
        messages: (m['messages'] as List)
            .map(
              (e) => AssistantConversationMessage.fromJson(
                e as Map<String, dynamic>,
              ),
            )
            .toList(),
      );
}

class Alarm {
  final String id;
  final String message;
  final DateTime time;
  final Recurrence? recurrence;
  final bool active;
  const Alarm({
    required this.id,
    required this.message,
    required this.time,
    this.recurrence,
    required this.active,
  });

  static Alarm fromJson(Map<String, dynamic> m) => Alarm(
    id: m['id'] as String,
    message: m['message'] as String,
    time: DateTime.parse(m['time'] as String).toLocal(),
    recurrence: Recurrence.fromJson(m['recurrence'] as Map<String, dynamic>?),
    active: m['active'] as bool? ?? true,
  );
}

class Watch {
  final String id;
  final String title;
  final String url;
  final String instruction;
  final bool notifyOnFound;
  final String status;
  final String statusDescription;
  final DateTime? lastCheckedAt;
  final bool active;

  /// Sinds wanneer de opdracht (opnieuw) zoekt.
  final DateTime? searchingSince;
  final DateTime? foundAt;

  /// Wanneer de vondst als gezien is bevestigd; tot dan volgt elke dag een herinnering.
  final DateTime? acknowledgedAt;

  /// Sinds wanneer de controle achter elkaar mislukt.
  final DateTime? failingSince;

  /// Wat de AI verder opviel (pagina weg, product uit het assortiment, verwachte datum).
  final String note;

  bool get found => status == 'GEVONDEN';
  bool get needsAcknowledge => found && acknowledgedAt == null;

  const Watch({
    required this.id,
    required this.title,
    required this.url,
    required this.instruction,
    required this.notifyOnFound,
    required this.status,
    required this.statusDescription,
    this.lastCheckedAt,
    required this.active,
    this.searchingSince,
    this.foundAt,
    this.acknowledgedAt,
    this.failingSince,
    this.note = '',
  });

  static DateTime? _time(Object? value) =>
      value == null ? null : DateTime.parse(value as String).toLocal();

  static Watch fromJson(Map<String, dynamic> m) => Watch(
    id: m['id'] as String,
    title: m['title'] as String,
    url: m['url'] as String,
    instruction: m['instruction'] as String,
    notifyOnFound: m['notifyOnFound'] as bool? ?? false,
    status: m['status'] as String? ?? 'NOG_NIET_GECONTROLEERD',
    statusDescription:
        m['statusDescription'] as String? ?? 'Nog niet gecontroleerd.',
    lastCheckedAt: m['lastCheckedAt'] == null
        ? null
        : DateTime.parse(m['lastCheckedAt'] as String).toLocal(),
    active: m['active'] as bool? ?? true,
    searchingSince: _time(m['searchingSince']),
    foundAt: _time(m['foundAt']),
    acknowledgedAt: _time(m['acknowledgedAt']),
    failingSince: _time(m['failingSince']),
    note: m['note'] as String? ?? '',
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
    test: m['test'] == null
        ? null
        : CouplingTest.fromJson(m['test'] as Map<String, dynamic>),
  );
}

/// Uitkomst van de live-test van een koppeling.
class CouplingTest {
  final bool ok;
  final String detail;
  final int durationMs;

  const CouplingTest({
    required this.ok,
    required this.detail,
    required this.durationMs,
  });

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

  const CheckRun({
    required this.ranAt,
    required this.ok,
    required this.summary,
    this.detail,
  });

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
    lastRun: m['lastRun'] == null
        ? null
        : CheckRun.fromJson(m['lastRun'] as Map<String, dynamic>),
  );
}

// -- Eetdagboek -----------------------------------------------------------------

/// Eén onderdeel van een maaltijd, met voedingswaarden voor de gegeten hoeveelheid.
class MealItem {
  final String name;
  final int grams;
  final int kcal;
  final double proteinG;
  final double carbsG;
  final double fatG;
  final double fiberG;
  final int vegetableG;
  final int fruitG;
  final bool uncertain;

  /// `GEWOGEN`, `ETIKET`, `RECEPT`, `VERGELEKEN` of `GESCHAT`.
  final String basis;

  const MealItem({
    required this.name,
    required this.grams,
    required this.kcal,
    this.proteinG = 0,
    this.carbsG = 0,
    this.fatG = 0,
    this.fiberG = 0,
    this.vegetableG = 0,
    this.fruitG = 0,
    this.uncertain = false,
    this.basis = 'GESCHAT',
  });

  bool get weighed => basis == 'GEWOGEN';

  /// Zelfde product, andere hoeveelheid: alle waarden schalen mee.
  MealItem withGrams(int newGrams, {bool? weighed}) {
    final f = grams > 0 ? newGrams / grams : 1.0;
    return MealItem(
      name: name,
      grams: newGrams,
      kcal: (kcal * f).round(),
      proteinG: proteinG * f,
      carbsG: carbsG * f,
      fatG: fatG * f,
      fiberG: fiberG * f,
      vegetableG: (vegetableG * f).round(),
      fruitG: (fruitG * f).round(),
      uncertain: false,
      basis: (weighed ?? this.weighed)
          ? 'GEWOGEN'
          : (basis == 'GEWOGEN' ? 'GESCHAT' : basis),
    );
  }

  MealItem copyWith({String? name, int? kcal}) => MealItem(
    name: name ?? this.name,
    grams: grams,
    kcal: kcal ?? this.kcal,
    proteinG: proteinG,
    carbsG: carbsG,
    fatG: fatG,
    fiberG: fiberG,
    vegetableG: vegetableG,
    fruitG: fruitG,
    uncertain: false,
    basis: basis,
  );

  static MealItem fromJson(Map<String, dynamic> m) => MealItem(
    name: m['name'] as String? ?? '',
    grams: (m['grams'] as num?)?.toInt() ?? 0,
    kcal: (m['kcal'] as num?)?.toInt() ?? 0,
    proteinG: (m['proteinG'] as num?)?.toDouble() ?? 0,
    carbsG: (m['carbsG'] as num?)?.toDouble() ?? 0,
    fatG: (m['fatG'] as num?)?.toDouble() ?? 0,
    fiberG: (m['fiberG'] as num?)?.toDouble() ?? 0,
    vegetableG: (m['vegetableG'] as num?)?.toInt() ?? 0,
    fruitG: (m['fruitG'] as num?)?.toInt() ?? 0,
    uncertain: m['uncertain'] as bool? ?? false,
    basis: m['basis'] as String? ?? 'GESCHAT',
  );

  Map<String, dynamic> toJson() => {
    'name': name,
    'grams': grams,
    'kcal': kcal,
    'proteinG': proteinG,
    'carbsG': carbsG,
    'fatG': fatG,
    'fiberG': fiberG,
    'vegetableG': vegetableG,
    'fruitG': fruitG,
    'uncertain': uncertain,
    'basis': basis,
  };
}

/// Een eetmoment. [status] is `ANALYSEREN`, `VOORSTEL`, `BEVESTIGD`, `VERBETERD` of `MISLUKT`.
class Meal {
  final String id;
  final DateTime eatenAt;
  final String title;
  final List<String> photoIds;
  final String note;
  final String status;
  final int kcal;
  final List<MealItem> items;
  final String remark;
  final String learned;
  final String error;

  /// Welk deel is opgegeten. [items] is de hele portie; [kcal] het opgegeten deel.
  final int eatenPercent;

  /// Rol in de referentiebibliotheek: ONBEKEND, REFERENTIE, DUBBEL of GEEN.
  final String referenceRole;
  final String referenceDescription;

  /// De foto die als referentie dient; leeg als de maaltijd geen referentie is.
  final String referencePhotoId;

  /// `true` als de rol in het beheerscherm is gezet en niet door de AI.
  final bool referenceManual;

  const Meal({
    required this.id,
    required this.eatenAt,
    required this.title,
    required this.photoIds,
    required this.note,
    required this.status,
    required this.kcal,
    required this.items,
    this.remark = '',
    this.learned = '',
    this.error = '',
    this.eatenPercent = 100,
    this.referenceRole = 'ONBEKEND',
    this.referenceDescription = '',
    this.referencePhotoId = '',
    this.referenceManual = false,
  });

  /// Wat deze maaltijd betekent voor het vergelijken van nieuwe foto's; leeg als hij niet meedoet.
  String get referenceLabel => switch (referenceRole) {
    'REFERENTIE' =>
      'Referentiefoto${referenceDescription.isEmpty ? '' : ': $referenceDescription'}',
    'DUBBEL' =>
      'Dubbel van een eerdere referentie; deze foto wordt niet nog eens gebruikt',
    _ => '',
  };

  bool get analysing => status == 'ANALYSEREN';
  bool get failed => status == 'MISLUKT';
  bool get proposal => status == 'VOORSTEL';
  bool get confirmed => status == 'BEVESTIGD' || status == 'VERBETERD';

  static Meal fromJson(Map<String, dynamic> m) => Meal(
    id: m['id'] as String,
    eatenAt: DateTime.parse(m['eatenAt'] as String).toLocal(),
    title: m['title'] as String? ?? '',
    photoIds: ((m['photoIds'] as List?) ?? const []).cast<String>(),
    note: m['note'] as String? ?? '',
    status: m['status'] as String? ?? 'MISLUKT',
    kcal: (m['kcal'] as num?)?.toInt() ?? 0,
    items: ((m['items'] as List?) ?? const [])
        .map((e) => MealItem.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
    remark: m['remark'] as String? ?? '',
    learned: m['learned'] as String? ?? '',
    error: m['error'] as String? ?? '',
    eatenPercent: (m['eatenPercent'] as num?)?.toInt() ?? 100,
    referenceRole: m['referenceRole'] as String? ?? 'ONBEKEND',
    referenceDescription: m['referenceDescription'] as String? ?? '',
    referencePhotoId: m['referencePhotoId'] as String? ?? '',
    referenceManual: m['referenceManual'] as bool? ?? false,
  );
}

/// Eén gevonden vacature met het oordeel (NIEUW, INTERESSANT of NIET) en de reden.
class JobLead {
  final String id;
  final String title;
  final String company;
  final String location;
  final String url;
  final String summary;
  final DateTime foundAt;
  final String verdict;
  final String reason;
  const JobLead({
    required this.id,
    required this.title,
    required this.company,
    this.location = '',
    this.url = '',
    this.summary = '',
    required this.foundAt,
    this.verdict = 'NIEUW',
    this.reason = '',
  });

  static JobLead fromJson(Map<String, dynamic> m) => JobLead(
    id: m['id'] as String,
    title: m['title'] as String? ?? '',
    company: m['company'] as String? ?? '',
    location: m['location'] as String? ?? '',
    url: m['url'] as String? ?? '',
    summary: m['summary'] as String? ?? '',
    foundAt: DateTime.parse(m['foundAt'] as String).toLocal(),
    verdict: m['verdict'] as String? ?? 'NIEUW',
    reason: m['reason'] as String? ?? '',
  );
}

/// De zoekopdracht voor werk: de zoekvraag, wat er geleerd is en de gevonden vacatures.
class JobOverview {
  final String description;
  final String preferences;
  final List<String> blockedCompanies;
  final DateTime? lastRunAt;
  final String lastRunNote;
  final bool running;
  final List<JobLead> leads;
  const JobOverview({
    this.description = '',
    this.preferences = '',
    this.blockedCompanies = const [],
    this.lastRunAt,
    this.lastRunNote = '',
    this.running = false,
    this.leads = const [],
  });

  static JobOverview fromJson(Map<String, dynamic> m) => JobOverview(
    description: m['description'] as String? ?? '',
    preferences: m['preferences'] as String? ?? '',
    blockedCompanies: ((m['blockedCompanies'] as List?) ?? const [])
        .cast<String>(),
    lastRunAt: m['lastRunAt'] == null
        ? null
        : DateTime.parse(m['lastRunAt'] as String).toLocal(),
    lastRunNote: m['lastRunNote'] as String? ?? '',
    running: m['running'] as bool? ?? false,
    leads: ((m['leads'] as List?) ?? const [])
        .map((e) => JobLead.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
  );
}

class GardenChatReply {
  final String conversationId;
  final String reply;
  const GardenChatReply({required this.conversationId, required this.reply});
}

/// Eén vastlegging bij een moestuinbak: wat er op [date] in stond, met foto's.
class GardenBedEntry {
  final String id;
  final DateTime date;
  final String text;
  final List<String> photoIds;
  const GardenBedEntry({
    required this.id,
    required this.date,
    this.text = '',
    this.photoIds = const [],
  });

  static GardenBedEntry fromJson(Map<String, dynamic> m) => GardenBedEntry(
    id: m['id'] as String,
    date: DateTime.parse(m['date'] as String),
    text: m['text'] as String? ?? '',
    photoIds: ((m['photoIds'] as List?) ?? const []).cast<String>(),
  );
}

/// Een bak op de plattegrond. Plaats en grootte zijn fracties (0..1) van de plattegrond.
class GardenBed {
  final String id;
  final String name;
  final double x;
  final double y;
  final double width;
  final double height;

  /// Nieuwste eerst.
  final List<GardenBedEntry> entries;
  const GardenBed({
    required this.id,
    required this.name,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    this.entries = const [],
  });

  GardenBed copyWith({
    String? name,
    double? x,
    double? y,
    double? width,
    double? height,
  }) => GardenBed(
    id: id,
    name: name ?? this.name,
    x: x ?? this.x,
    y: y ?? this.y,
    width: width ?? this.width,
    height: height ?? this.height,
    entries: entries,
  );

  Map<String, dynamic> toJson() => {
    'name': name,
    'x': x,
    'y': y,
    'width': width,
    'height': height,
  };

  static GardenBed fromJson(Map<String, dynamic> m) => GardenBed(
    id: m['id'] as String,
    name: m['name'] as String? ?? '',
    x: (m['x'] as num).toDouble(),
    y: (m['y'] as num).toDouble(),
    width: (m['width'] as num).toDouble(),
    height: (m['height'] as num).toDouble(),
    entries: ((m['entries'] as List?) ?? const [])
        .map(
          (e) => GardenBedEntry.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList(),
  );
}

class GardenPlan {
  final String backgroundPhotoId;
  final double aspectRatio;
  final List<GardenBed> beds;
  const GardenPlan({
    this.backgroundPhotoId = '',
    this.aspectRatio = 1,
    this.beds = const [],
  });

  static GardenPlan fromJson(Map<String, dynamic> m) => GardenPlan(
    backgroundPhotoId: m['backgroundPhotoId'] as String? ?? '',
    aspectRatio: (m['aspectRatio'] as num?)?.toDouble() ?? 1,
    beds: ((m['beds'] as List?) ?? const [])
        .map((e) => GardenBed.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
  );
}

/// Eén dag van de zonnepanelen. [complete] is `false` voor vandaag: die dag loopt nog.
class SolarDay {
  final DateTime date;
  final double? yieldKwh;
  final double? expectedKwh;
  final double? radiationKwhM2;
  final double? sunshineHours;
  final bool complete;
  const SolarDay({
    required this.date,
    this.yieldKwh,
    this.expectedKwh,
    this.radiationKwhM2,
    this.sunshineHours,
    this.complete = true,
  });

  static SolarDay fromJson(Map<String, dynamic> m) => SolarDay(
    date: DateTime.parse(m['date'] as String),
    yieldKwh: (m['yieldKwh'] as num?)?.toDouble(),
    expectedKwh: (m['expectedKwh'] as num?)?.toDouble(),
    radiationKwhM2: (m['radiationKwhM2'] as num?)?.toDouble(),
    sunshineHours: (m['sunshineHours'] as num?)?.toDouble(),
    complete: m['complete'] as bool? ?? true,
  );
}

/// Opbrengst per dag met het oordeel over de laatste week (GOED, LET_OP, NIET of ONBEKEND).
class SolarOverview {
  final List<SolarDay> days;
  final String verdict;
  final String verdictText;
  final double? weekYieldKwh;
  final double? weekExpectedKwh;
  final int? weekPercent;
  final int? currentPowerWatt;
  final String? error;
  const SolarOverview({
    required this.days,
    required this.verdict,
    required this.verdictText,
    this.weekYieldKwh,
    this.weekExpectedKwh,
    this.weekPercent,
    this.currentPowerWatt,
    this.error,
  });

  static SolarOverview fromJson(Map<String, dynamic> m) => SolarOverview(
    days: ((m['days'] as List?) ?? const [])
        .map((e) => SolarDay.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
    verdict: m['verdict'] as String? ?? 'ONBEKEND',
    verdictText: m['verdictText'] as String? ?? '',
    weekYieldKwh: (m['weekYieldKwh'] as num?)?.toDouble(),
    weekExpectedKwh: (m['weekExpectedKwh'] as num?)?.toDouble(),
    weekPercent: (m['weekPercent'] as num?)?.toInt(),
    currentPowerWatt: (m['currentPowerWatt'] as num?)?.toInt(),
    error: m['error'] as String?,
  );
}

class NutritionTotals {
  final int kcal;
  final double proteinG;
  final double fiberG;
  final int vegetableG;
  final int fruitG;

  const NutritionTotals({
    this.kcal = 0,
    this.proteinG = 0,
    this.fiberG = 0,
    this.vegetableG = 0,
    this.fruitG = 0,
  });

  static NutritionTotals fromJson(Map<String, dynamic> m) => NutritionTotals(
    kcal: (m['kcal'] as num?)?.toInt() ?? 0,
    proteinG: (m['proteinG'] as num?)?.toDouble() ?? 0,
    fiberG: (m['fiberG'] as num?)?.toDouble() ?? 0,
    vegetableG: (m['vegetableG'] as num?)?.toInt() ?? 0,
    fruitG: (m['fruitG'] as num?)?.toInt() ?? 0,
  );
}

class NutritionDay {
  final NutritionTotals totals;
  final List<Meal> meals;

  const NutritionDay({required this.totals, required this.meals});

  static NutritionDay fromJson(Map<String, dynamic> m) => NutritionDay(
    totals: NutritionTotals.fromJson(
      Map<String, dynamic>.from(m['totals'] as Map? ?? const {}),
    ),
    meals: ((m['meals'] as List?) ?? const [])
        .map((e) => Meal.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
  );
}

/// Hoe Robbert zich op één moment voelde. [hunger] 0 = geen … 4 = slap van de honger,
/// [energy] 1 = moe … 5 = fit; elk mag ontbreken.
class CheckIn {
  final String id;
  final DateTime at;
  final int? hunger;
  final int? energy;
  final String note;
  final String source;
  final String? mealId;

  const CheckIn({
    required this.id,
    required this.at,
    this.hunger,
    this.energy,
    this.note = '',
    this.source = 'APP',
    this.mealId,
  });

  static CheckIn fromJson(Map<String, dynamic> m) => CheckIn(
    id: m['id'] as String,
    at: DateTime.parse(m['at'] as String).toLocal(),
    hunger: (m['hunger'] as num?)?.toInt(),
    energy: (m['energy'] as num?)?.toInt(),
    note: m['note'] as String? ?? '',
    source: m['source'] as String? ?? 'APP',
    mealId: m['mealId'] as String?,
  );
}

/// Een dutje overdag. Zonder [end] loopt het nog; [endEstimated] zegt dat het eind door de app
/// is geschat omdat Robbert zich niet heeft afgemeld.
class Nap {
  final String id;
  final DateTime start;
  final DateTime? end;
  final bool endEstimated;
  final DateTime? alarmAt;

  const Nap({
    required this.id,
    required this.start,
    this.end,
    this.endEstimated = false,
    this.alarmAt,
  });

  bool get open => end == null;

  static Nap fromJson(Map<String, dynamic> m) => Nap(
    id: m['id'] as String,
    start: DateTime.parse(m['start'] as String).toLocal(),
    end: DateTime.tryParse(m['end'] as String? ?? '')?.toLocal(),
    endEstimated: m['endEstimated'] as bool? ?? false,
    alarmAt: DateTime.tryParse(m['alarmAt'] as String? ?? '')?.toLocal(),
  );
}

class NapDay {
  final List<Nap> naps;

  /// Het dutje dat nu loopt, ook als dat op een andere dag begon.
  final Nap? open;

  const NapDay({this.naps = const [], this.open});

  static NapDay fromJson(Map<String, dynamic> m) => NapDay(
    naps: ((m['naps'] as List?) ?? const [])
        .map((e) => Nap.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
    open: m['open'] == null
        ? null
        : Nap.fromJson(Map<String, dynamic>.from(m['open'] as Map)),
  );
}

class ActivityWorkout {
  final String type;
  final int minutes;
  final int? kcal;
  final DateTime? start;
  final DateTime? end;

  const ActivityWorkout({
    required this.type,
    required this.minutes,
    this.kcal,
    this.start,
    this.end,
  });

  /// Nederlandse naam voor de soorten die Garmin het vaakst aanlevert.
  String get label => switch (type) {
    'WALKING' => 'wandelen',
    'RUNNING' => 'hardlopen',
    'BIKING' => 'fietsen',
    'SWIMMING' => 'zwemmen',
    'STRENGTH_TRAINING' => 'krachttraining',
    'HIKING' => 'wandelen',
    _ => type.toLowerCase().replaceAll('_', ' '),
  };
}

/// Wat Robbert op één dag bewoog, verbruikte en sliep (van Garmin, via Health Connect).
class ActivityDay {
  final int? steps;
  final int? totalKcal;
  final int? sleepMinutes;
  final DateTime? sleepStart;
  final DateTime? sleepEnd;
  final double? weightKg;
  final List<ActivityWorkout> workouts;

  const ActivityDay({
    this.steps,
    this.totalKcal,
    this.sleepMinutes,
    this.sleepStart,
    this.sleepEnd,
    this.weightKg,
    this.workouts = const [],
  });

  static ActivityDay fromJson(Map<String, dynamic> m) => ActivityDay(
    steps: (m['steps'] as num?)?.toInt(),
    totalKcal: (m['totalKcal'] as num?)?.toInt(),
    sleepMinutes: ((m['sleep'] as Map?)?['minutes'] as num?)?.toInt(),
    weightKg: (m['weightKg'] as num?)?.toDouble(),
    sleepStart: DateTime.tryParse(
      (m['sleep'] as Map?)?['start'] as String? ?? '',
    )?.toLocal(),
    sleepEnd: DateTime.tryParse(
      (m['sleep'] as Map?)?['end'] as String? ?? '',
    )?.toLocal(),
    workouts: ((m['workouts'] as List?) ?? const []).map((e) {
      final w = Map<String, dynamic>.from(e as Map);
      final start = DateTime.tryParse(w['start'] as String? ?? '');
      final end = DateTime.tryParse(w['end'] as String? ?? '');
      return ActivityWorkout(
        type: w['type'] as String? ?? '',
        minutes: start == null || end == null
            ? 0
            : end.difference(start).inMinutes,
        kcal: (w['kcal'] as num?)?.toInt(),
        start: start?.toLocal(),
        end: end?.toLocal(),
      );
    }).toList(),
  );
}

class OverviewDay {
  final DateTime date;
  final int eatenKcal;
  final int? burnedKcal;
  final int? steps;
  final int? sleepMinutes;
  final int mealCount;

  const OverviewDay({
    required this.date,
    required this.eatenKcal,
    this.burnedKcal,
    this.steps,
    this.sleepMinutes,
    this.mealCount = 0,
  });

  static OverviewDay fromJson(Map<String, dynamic> m) => OverviewDay(
    date: DateTime.parse(m['date'] as String),
    eatenKcal: (m['eatenKcal'] as num?)?.toInt() ?? 0,
    burnedKcal: (m['burnedKcal'] as num?)?.toInt(),
    steps: (m['steps'] as num?)?.toInt(),
    sleepMinutes: (m['sleepMinutes'] as num?)?.toInt(),
    mealCount: (m['mealCount'] as num?)?.toInt() ?? 0,
  );
}

class WeightPoint {
  final DateTime date;
  final double weightKg;

  const WeightPoint({required this.date, required this.weightKg});
}

/// Zeven dagen eten en bewegen, met gemiddelden (`GET /api/v1/insights/week`).
class WeekOverview {
  final List<OverviewDay> days;
  final int? avgEatenKcal;
  final int? avgBurnedKcal;

  /// Gemiddeld gegeten min verbruikt over afgeronde dagen; `null` zolang die er niet zijn.
  final int? avgBalanceKcal;
  final double? avgProteinG;
  final double? avgFiberG;
  final int? avgVegetableG;

  /// Aandeel van de calorieën per onderbouwing (`GEWOGEN`, `GESCHAT`, …), in procenten.
  final Map<String, int> basisShare;
  final int? unchangedShare;
  final List<WeightPoint> weights;

  const WeekOverview({
    required this.days,
    this.avgEatenKcal,
    this.avgBurnedKcal,
    this.avgBalanceKcal,
    this.avgProteinG,
    this.avgFiberG,
    this.avgVegetableG,
    this.basisShare = const {},
    this.unchangedShare,
    this.weights = const [],
  });

  static WeekOverview fromJson(Map<String, dynamic> m) => WeekOverview(
    days: ((m['days'] as List?) ?? const [])
        .map((e) => OverviewDay.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList(),
    avgEatenKcal: (m['avgEatenKcal'] as num?)?.toInt(),
    avgBurnedKcal: (m['avgBurnedKcal'] as num?)?.toInt(),
    avgBalanceKcal: (m['avgBalanceKcal'] as num?)?.toInt(),
    avgProteinG: (m['avgProteinG'] as num?)?.toDouble(),
    avgFiberG: (m['avgFiberG'] as num?)?.toDouble(),
    avgVegetableG: (m['avgVegetableG'] as num?)?.toInt(),
    basisShare: ((m['basisShare'] as Map?) ?? const {}).map(
      (k, v) => MapEntry(k as String, (v as num).toInt()),
    ),
    unchangedShare: (m['unchangedShare'] as num?)?.toInt(),
    weights: ((m['weights'] as List?) ?? const []).map((e) {
      final w = Map<String, dynamic>.from(e as Map);
      return WeightPoint(
        date: DateTime.parse(w['date'] as String),
        weightKg: (w['weightKg'] as num).toDouble(),
      );
    }).toList(),
  );
}

/// Eén verband uit Robberts eigen gegevens, met de getallen in [text].
class Insight {
  final String title;
  final String text;

  /// Of er genoeg waarnemingen zijn om er iets aan te hebben.
  final bool clear;

  const Insight({required this.title, required this.text, required this.clear});

  static Insight fromJson(Map<String, dynamic> m) => Insight(
    title: m['title'] as String? ?? '',
    text: m['text'] as String? ?? '',
    clear: m['strength'] == 'DUIDELIJK',
  );
}

/// Leverancier, model en uitvoeringswijze bij de Agent Runtime.
class AiExecution {
  final String vendorId;
  final String model;
  final String mode;

  const AiExecution({
    required this.vendorId,
    required this.model,
    required this.mode,
  });

  String get key => '$vendorId|$model|$mode';
  String get label => '$vendorId · $model · ${mode.toLowerCase()}';

  static AiExecution fromJson(Map<String, dynamic> m) => AiExecution(
    vendorId: m['vendorId'] as String? ?? '',
    model: m['model'] as String? ?? '',
    mode: m['mode'] as String? ?? '',
  );

  Map<String, dynamic> toJson() => {
    'vendorId': vendorId,
    'model': model,
    'mode': mode,
  };
}

/// Eén toepassing (assistent, eetdagboek, overig) met het model dat er nu voor geldt.
class AiRoleSetting {
  final String role;
  final String label;
  final String description;
  final AiExecution execution;

  /// `true` als er een eigen keuze is; anders geldt het standaardmodel.
  final bool custom;

  const AiRoleSetting({
    required this.role,
    required this.label,
    required this.description,
    required this.execution,
    required this.custom,
  });
}

class AiOption {
  final AiExecution execution;
  final bool available;

  const AiOption({required this.execution, required this.available});
}

class AiModels {
  final List<AiRoleSetting> roles;
  final AiExecution defaultExecution;
  final List<AiOption> options;
  final String? optionsError;

  const AiModels({
    required this.roles,
    required this.defaultExecution,
    required this.options,
    this.optionsError,
  });

  static AiModels fromJson(Map<String, dynamic> m) => AiModels(
    roles: ((m['roles'] as List?) ?? const []).map((e) {
      final r = Map<String, dynamic>.from(e as Map);
      return AiRoleSetting(
        role: r['role'] as String? ?? '',
        label: r['label'] as String? ?? '',
        description: r['description'] as String? ?? '',
        execution: AiExecution.fromJson(
          Map<String, dynamic>.from(r['execution'] as Map? ?? const {}),
        ),
        custom: r['custom'] as bool? ?? false,
      );
    }).toList(),
    defaultExecution: AiExecution.fromJson(
      Map<String, dynamic>.from(m['default'] as Map? ?? const {}),
    ),
    options: ((m['options'] as List?) ?? const []).map((e) {
      final o = Map<String, dynamic>.from(e as Map);
      return AiOption(
        execution: AiExecution.fromJson(
          Map<String, dynamic>.from(o['execution'] as Map? ?? const {}),
        ),
        available: o['available'] as bool? ?? false,
      );
    }).toList(),
    optionsError: m['optionsError'] as String?,
  );
}

/// Een bewaarde melding; lezen verwijdert hem niet uit de geschiedenis.
class AppNotification {
  final String id;
  final String title;
  final String body;
  final Map<String, String> data;
  final DateTime createdAt;
  final DateTime? readAt;

  const AppNotification({
    required this.id,
    required this.title,
    required this.body,
    this.data = const {},
    required this.createdAt,
    this.readAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) =>
      AppNotification(
        id: json['id'] as String,
        title: json['title'] as String? ?? '',
        body: json['body'] as String? ?? '',
        data: Map<String, String>.from(json['data'] as Map? ?? {}),
        createdAt: DateTime.parse(json['createdAt'] as String).toLocal(),
        readAt: DateTime.tryParse(json['readAt'] as String? ?? '')?.toLocal(),
      );
}

class NotificationPage {
  final List<AppNotification> items;
  final String? nextCursor;
  const NotificationPage({required this.items, this.nextCursor});
}
