import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/banking_screen.dart';

class BankApi extends ApiClient {
  bool configured = true;
  bool fail = false;
  final writes = <String>[];
  @override
  Future<Map<String, dynamic>> getJson(String path) async {
    if (fail) throw StateError('offline');
    if (path.endsWith('/rules')) return {'rules': []};
    if (path.contains('/month?')) {
      return {
        'spent': 90.25,
        'received': 0,
        'extra': 20.25,
        'review': 70,
        'warnings': ['Creditcarddetails ontbreken nog.'],
        'categories': [],
        'transactions': [
          {
            'id': 'extra',
            'date': '2026-11-01',
            'counterparty': 'Uitje',
            'description': 'Concert test',
            'amount': -20.25,
            'category': 'EXTRA',
          },
          {
            'id': 'review',
            'date': '2026-11-02',
            'counterparty': 'Onbekende winkel',
            'description': 'Aankoop test',
            'amount': -70,
            'category': 'REVIEW',
            'question': 'Waarvoor was deze aankoop?',
          },
        ],
      };
    }
    return {'configured': configured, 'accounts': [], 'lastSync': null};
  }

  @override
  Future<Map<String, dynamic>> putJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    writes.add('$path:${body['category']}');
    return {'saved': true};
  }
}

void main() {
  testWidgets('smal scherm toont overzicht en transacties zonder overflow', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(MaterialApp(home: BankingScreen(api: BankApi())));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.dragUntilVisible(
      find.textContaining('Uitje ·'),
      find.byType(ListView),
      const Offset(0, -200),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.textContaining('Uitje ·'), findsOneWidget);
  });
  testWidgets('niet ingestelde koppeling toont geen misleidende nulbegroting', (
    tester,
  ) async {
    final api = BankApi()..configured = false;
    await tester.pumpWidget(MaterialApp(home: BankingScreen(api: api)));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('De bankkoppeling wordt voorbereid'),
      findsOneWidget,
    );
    expect(find.text('Bankgegevens bijwerken'), findsNothing);
    expect(find.textContaining('€ 0'), findsNothing);
  });
  testWidgets(
    'extra filter scheidt onbekende betalingen en toont exact bedrag',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1000, 1600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(MaterialApp(home: BankingScreen(api: BankApi())));
      await tester.pumpAndSettle();
      expect(find.textContaining('Uitje · € -20,25'), findsOneWidget);
      expect(
        find.textContaining('Onbekende winkel · € -70,00'),
        findsOneWidget,
      );
      await tester.tap(find.widgetWithText(ChoiceChip, 'Overig / extra'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Uitje · € -20,25'), findsOneWidget);
      expect(find.textContaining('Onbekende winkel · € -70,00'), findsNothing);
      expect(find.text('Creditcarddetails ontbreken nog.'), findsOneWidget);
    },
  );
  testWidgets('categorie corrigeren schrijft alleen de administratie', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = BankApi();
    await tester.pumpWidget(MaterialApp(home: BankingScreen(api: api)));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('Onbekende winkel ·'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Boodschappen'));
    await tester.pumpAndSettle();
    expect(
      api.writes.single,
      contains('/transactions/review/category:GROCERIES'),
    );
  });
  testWidgets('ophalen mislukt toont herstelactie', (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: BankingScreen(api: BankApi()..fail = true)),
    );
    await tester.pumpAndSettle();
    expect(find.text('Opnieuw proberen'), findsOneWidget);
    expect(
      find.textContaining('Bankzaken laden is niet gelukt'),
      findsOneWidget,
    );
  });
}
