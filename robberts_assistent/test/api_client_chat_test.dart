import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:robberts_assistent/api_client.dart';

void main() {
  http.Response json(Object body, [int status = 200]) => http.Response(
        jsonEncode(body),
        status,
        headers: {'content-type': 'application/json'},
      );

  ApiClient newClient() => ApiClient()
    ..token = 'tok'
    ..chatPollInterval = const Duration(milliseconds: 5)
    ..chatMaxWait = const Duration(seconds: 5);

  test('start een async beurt en pollt tot het antwoord er is', () async {
    final calls = <String>[];
    var polls = 0;
    final mock = MockClient((request) async {
      calls.add('${request.method} ${request.url.path}');
      if (request.method == 'POST') return json({'jobId': 'j1'}, 202);
      polls++;
      if (polls < 3) return json({'jobId': 'j1', 'status': 'RUNNING'});
      return json({
        'jobId': 'j1',
        'status': 'DONE',
        'conversationId': 'c1',
        'title': 'Titel',
        'reply': 'Hoi!',
      });
    });

    final reply = await http.runWithClient(
      () => newClient().assistantChat(message: 'hallo'),
      () => mock,
    );

    expect(reply.reply, 'Hoi!');
    expect(reply.conversationId, 'c1');
    expect(reply.title, 'Titel');
    expect(calls.first, 'POST /api/v1/assistant/chat/async');
    expect(calls.where((c) => c == 'GET /api/v1/assistant/chat/jobs/j1').length, 3);
  });

  test('een mislukte beurt geeft de foutmelding van de server', () async {
    final mock = MockClient((request) async {
      if (request.method == 'POST') return json({'jobId': 'j1'}, 202);
      return json({'jobId': 'j1', 'status': 'FAILED', 'error': 'runtime stuk'});
    });

    expect(
      http.runWithClient(
        () => newClient().assistantChat(message: 'hallo'),
        () => mock,
      ),
      throwsA(predicate((e) => e.toString().contains('runtime stuk'))),
    );
  });

  test('een kortstondige netwerkfout tijdens het wachten wordt overleefd', () async {
    var polls = 0;
    final mock = MockClient((request) async {
      if (request.method == 'POST') return json({'jobId': 'j1'}, 202);
      polls++;
      if (polls == 1) throw http.ClientException('verbinding weg');
      return json({
        'jobId': 'j1',
        'status': 'DONE',
        'conversationId': 'c1',
        'title': 'T',
        'reply': 'Klaar',
      });
    });

    final reply = await http.runWithClient(
      () => newClient().assistantChat(message: 'hallo'),
      () => mock,
    );

    expect(reply.reply, 'Klaar');
  });
}
