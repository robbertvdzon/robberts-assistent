import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/ai_models_screen.dart';
import 'package:robberts_assistent/api_client.dart';

const _sonnet = AiExecution(
  vendorId: 'anthropic',
  model: 'claude-sonnet-5',
  mode: 'SUBSCRIPTION',
);
const _opus = AiExecution(
  vendorId: 'anthropic',
  model: 'claude-opus-5',
  mode: 'SUBSCRIPTION',
);
const _old = AiExecution(
  vendorId: 'anthropic',
  model: 'claude-oud',
  mode: 'SUBSCRIPTION',
);

class _FakeApiClient extends ApiClient {
  final chosen = <String, AiExecution>{};
  String? optionsError;
  final calls = <String>[];

  AiModels _state() => AiModels(
    roles: [
      for (final (role, label) in const [
        ('CHAT', 'Assistent'),
        ('NUTRITION', 'Eetdagboek'),
        ('BACKGROUND', 'Overig'),
      ])
        AiRoleSetting(
          role: role,
          label: label,
          description: 'Omschrijving van $label',
          execution: chosen[role] ?? _sonnet,
          custom: chosen.containsKey(role),
        ),
    ],
    defaultExecution: _sonnet,
    options: optionsError != null
        ? const []
        : const [
            AiOption(execution: _sonnet, available: true),
            AiOption(execution: _opus, available: false),
          ],
    optionsError: optionsError,
  );

  @override
  Future<AiModels> getAiModels() async => _state();

  @override
  Future<AiModels> setAiModel(String role, AiExecution execution) async {
    calls.add('set $role ${execution.model}');
    chosen[role] = execution;
    return _state();
  }

  @override
  Future<AiModels> resetAiModel(String role) async {
    calls.add('reset $role');
    chosen.remove(role);
    return _state();
  }
}

Future<void> _pump(WidgetTester tester, ApiClient api) async {
  await tester.pumpWidget(MaterialApp(home: AiModelsScreen(api: api)));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('toont de drie toepassingen, alle op het standaardmodel', (
    tester,
  ) async {
    await _pump(tester, _FakeApiClient());

    expect(find.text('Assistent'), findsOneWidget);
    expect(find.text('Eetdagboek'), findsOneWidget);
    expect(find.text('Overig'), findsOneWidget);
    expect(find.text('Standaard (claude-sonnet-5)'), findsNWidgets(3));
  });

  testWidgets(
    'een model kiezen slaat het op voor die toepassing, en standaard zet het terug',
    (tester) async {
      final api = _FakeApiClient();
      await _pump(tester, api);

      await tester.tap(find.text('Standaard (claude-sonnet-5)').at(1));
      await tester.pumpAndSettle();
      await tester.tap(
        find
            .text('anthropic · claude-opus-5 · subscription (nu geen worker)')
            .last,
      );
      await tester.pumpAndSettle();

      expect(api.calls, ['set NUTRITION claude-opus-5']);
      expect(
        find.text('anthropic · claude-opus-5 · subscription (nu geen worker)'),
        findsOneWidget,
      );
      expect(find.text('Standaard (claude-sonnet-5)'), findsNWidgets(2));

      await tester.tap(
        find.text('anthropic · claude-opus-5 · subscription (nu geen worker)'),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Standaard (claude-sonnet-5)').last);
      await tester.pumpAndSettle();

      expect(api.calls.last, 'reset NUTRITION');
      expect(find.text('Standaard (claude-sonnet-5)'), findsNWidgets(3));
    },
  );

  testWidgets(
    'een gekozen model dat niet meer in de lijst staat blijft zichtbaar, met een melding als de lijst ontbreekt',
    (tester) async {
      final api = _FakeApiClient()
        ..chosen['CHAT'] = _old
        ..optionsError = 'HTTP 503';
      await _pump(tester, api);

      expect(
        find.text(
          'anthropic · claude-oud · subscription (niet meer in de lijst)',
        ),
        findsOneWidget,
      );
      expect(
        find.textContaining('kon niet bij de runtime worden opgehaald'),
        findsOneWidget,
      );
    },
  );
}
