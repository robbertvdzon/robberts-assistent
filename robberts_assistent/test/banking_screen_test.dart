import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/banking_screen.dart';

class BankApi extends ApiClient {
  bool configured = true;
  bool fail = false;
  bool historyFails = false;
  Map<String, dynamic>? categorization;
  List<Map<String, dynamic>> reviewGroups = [];
  final writes = <String>[];
  final requests = <String>[];
  final overrides = <String, String>{};

  Map<String, dynamic> report(String month) => {
    'month': month,
    'spent': 190.25,
    'received': 5,
    'extra': 20.25,
    'review': 70,
    'warnings': ['Creditcarddetails ontbreken nog.'],
    'categories': [],
    'transactions': [
      {
        'id': 'extra',
        'accountId': 'account-one',
        'date': '$month-01',
        'counterparty': 'Uitje',
        'description': 'Concert test',
        'amount': -20.25,
        'category': overrides['extra'] ?? 'EXTRA',
      },
      {
        'id': 'review',
        'accountId': 'account-one',
        'date': '$month-02',
        'counterparty': 'Onbekende winkel',
        'description': 'Aankoop test',
        'amount': -70,
        'category': overrides['review'] ?? 'REVIEW',
        'question': 'Waarvoor was deze aankoop?',
      },
      {
        'id': 'premium-auto',
        'accountId': 'account-one',
        'date': '$month-03',
        'counterparty': 'Verzekeraar Test',
        'description': 'Autoverzekering polis TEST-001',
        'amount': -60,
        'category': overrides['premium-auto'] ?? 'INSURANCE',
        'classificationSource': 'RULE',
        'reason': 'Herkenbare incasso',
      },
      {
        'id': 'premium-home',
        'accountId': 'account-one',
        'date': '$month-04',
        'counterparty': 'Verzekeraar Test',
        'description': 'Inboedelverzekering polis TEST-002',
        'amount': -40,
        'category': overrides['premium-home'] ?? 'INSURANCE',
      },
      {
        'id': 'premium-refund',
        'accountId': 'account-one',
        'date': '$month-05',
        'counterparty': 'Verzekeraar Test',
        'description': 'Premiecorrectie TEST-001',
        'amount': 5,
        'category': 'INSURANCE',
      },
      {
        'id': 'transfer',
        'accountId': 'account-one',
        'date': '$month-06',
        'counterparty': 'Eigen rekening',
        'description': 'Interne overboeking',
        'amount': -1000,
        'category': 'INTERNAL',
      },
    ],
  };

  @override
  Future<Map<String, dynamic>> getJson(String path) async {
    requests.add(path);
    if (fail) throw StateError('offline');
    final uri = Uri.parse(path);
    if (uri.path.endsWith('/review-merchants')) {
      return {
        'groups': reviewGroups,
        'transactionCount': reviewGroups.length,
        'from': '2025-11-21',
        'to': '2026-11-20',
      };
    }
    if (uri.path.endsWith('/categorization')) {
      return categorization ?? {'status': 'IDLE'};
    }
    if (uri.path.endsWith('/rules')) return {'rules': []};
    if (uri.path.endsWith('/month')) {
      return report(uri.queryParameters['month']!);
    }
    if (uri.path.endsWith('/history')) {
      if (historyFails) throw StateError('history unavailable');
      final month = uri.queryParameters['end']!;
      return {
        'months': [report(month)],
        'from': '$month-01',
        'to': '$month-06',
        'warnings': [],
      };
    }
    if (uri.path.endsWith('/planning')) {
      return {
        'year': int.parse(uri.queryParameters['year']!),
        'monthlyIncome': null,
        'monthlyExpenses': null,
        'openingReserve': 0,
        'budgets': {'EXTRA': 100},
        'items': [],
      };
    }
    return {
      'configured': configured,
      'accounts': [
        {
          'id': 'account-one',
          'iban': 'NL00TEST0000000001',
          'name': 'Testrekening',
          'validUntil': '2027-05-01T00:00:00Z',
        },
      ],
      'lastSync': null,
    };
  }

  final connectBodies = <Map<String, dynamic>>[];
  @override
  Future<Map<String, dynamic>> postJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    if (path.endsWith('/connect')) {
      connectBodies.add(body);
      // Stop before launching a real browser in this widget test.
      return {'url': 'https://example.invalid/auth'};
    }
    return {};
  }

  @override
  Future<Map<String, dynamic>> putJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    writes.add('$path:${body['category']}');
    if (path.contains('/transactions/')) {
      final id = path.split('/transactions/')[1].split('/')[0];
      overrides[id] = '${body['category']}';
    }
    return {'saved': true};
  }
}

Future<void> revealAndTap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('nieuwe rekening valideert IBAN en vraagt die rekening aan', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1100, 1600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = BankApi();
    await tester.pumpWidget(
      MaterialApp(
        home: BankingScreen(api: api, initialMonth: '2026-10'),
      ),
    );
    await tester.pumpAndSettle();
    await revealAndTap(tester, find.textContaining('Rekeningen en gegevens'));
    await revealAndTap(tester, find.text('Nieuwe rekening toevoegen'));
    await tester.enterText(
      find.widgetWithText(TextFormField, 'IBAN'),
      'NL00ABNA0417164300',
    );
    await tester.tap(find.text('Verder naar de bank'));
    await tester.pumpAndSettle();
    expect(find.textContaining('het controlegetal klopt niet'), findsOneWidget);
    expect(api.connectBodies, isEmpty);
    await tester.enterText(
      find.widgetWithText(TextFormField, 'IBAN'),
      'nl91 abna 0417 1643 00',
    );
    await tester.tap(find.text('Verder naar de bank'));
    await tester.pumpAndSettle();
    expect(api.connectBodies, [
      {'iban': 'NL91ABNA0417164300'},
    ]);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'winkellijst opent binnen Bankzaken met PageStorage en behoudt zoekveld na opnieuw openen',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1100, 1600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = BankApi()
        ..reviewGroups = [
          {
            'key': 'test-shop',
            'merchant': 'Test Kledingzaak',
            'count': 1,
            'spent': 40,
            'from': '2025-12-03',
            'to': '2025-12-03',
            'canLearn': true,
            'examples': [
              {
                'id': 'test-payment',
                'date': '2025-12-03',
                'amount': -40,
                'description': 'Testaankoop',
              },
            ],
          },
        ];
      await tester.pumpWidget(
        MaterialApp(
          home: PageStorage(
            bucket: PageStorageBucket(),
            child: BankingScreen(api: api, initialMonth: '2026-10'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await revealAndTap(tester, find.text('Winkels indelen'));
      expect(find.text('Test Kledingzaak'), findsOneWidget);
      final search = find.byKey(
        const PageStorageKey('bank-merchant-search-scroll'),
      );
      await tester.ensureVisible(search);
      await tester.enterText(search, 'Kleding');
      await tester.pumpAndSettle();
      await revealAndTap(tester, find.text('Bekijk 1 voorbeeldbetaling(en)'));
      expect(find.text('Testaankoop'), findsOneWidget);
      await revealAndTap(tester, find.text('Winkels indelen'));
      await revealAndTap(tester, find.text('Winkels indelen'));
      expect(find.text('Test Kledingzaak'), findsOneWidget);
      expect(find.text('Testaankoop'), findsOneWidget);
      expect(find.text('Kleding'), findsWidgets);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('rubricering toont fractie naast categoriepercentages', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1100, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = BankApi()
      ..categorization = {
        'status': 'RUNNING',
        'total': 100,
        'completed': 25,
        'message': 'Batches van 100 patronen, maximaal 3 tegelijk.',
      };
    await tester.pumpWidget(
      MaterialApp(
        home: BankingScreen(api: api, initialMonth: '2026-10'),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.textContaining('25 van 100'));
    await tester.pumpAndSettle();
    final panel = find
        .ancestor(
          of: find.textContaining('25 van 100'),
          matching: find.byType(Card),
        )
        .first;
    expect(
      tester
          .widget<LinearProgressIndicator>(
            find.descendant(
              of: panel,
              matching: find.byType(LinearProgressIndicator),
            ),
          )
          .value,
      .25,
    );
    expect(find.textContaining('maximaal 3 tegelijk'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('smal scherm toont bedragen en betalingen zonder overflow', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: BankingScreen(api: BankApi(), initialMonth: '2026-10'),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.ensureVisible(find.text('Uitje · −€ 20,25'));
    await tester.pumpAndSettle();
    expect(find.text('Uitje · −€ 20,25'), findsOneWidget);
    expect(find.textContaining('Eigen rekening ·'), findsNothing);
    expect(tester.takeException(), isNull);
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

  testWidgets('categorie naar aanbieder naar betaling en correctie', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1100, 1600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = BankApi();
    await tester.pumpWidget(
      MaterialApp(
        home: BankingScreen(api: api, initialMonth: '2026-10'),
      ),
    );
    await tester.pumpAndSettle();
    await revealAndTap(tester, find.text('Verzekeringen'));
    expect(find.text('Aanbieders en betalingen'), findsOneWidget);
    expect(find.textContaining('3 boekingen · netto € 95,00'), findsOneWidget);
    expect(find.text('Autoverzekering polis TEST-001'), findsNothing);
    await revealAndTap(tester, find.text('Verzekeraar Test'));
    expect(find.text('Autoverzekering polis TEST-001'), findsOneWidget);
    expect(find.text('Inboedelverzekering polis TEST-002'), findsOneWidget);
    expect(find.text('Premiecorrectie TEST-001'), findsOneWidget);
    expect(
      find.textContaining('Vaste regel: Herkenbare incasso'),
      findsOneWidget,
    );
    await revealAndTap(tester, find.text('Verzekeraar Test · −€ 60,00'));
    await revealAndTap(
      tester,
      find.descendant(
        of: find.byType(SimpleDialog),
        matching: find.text('Vervoer'),
      ),
    );
    expect(api.writes, [
      '/api/v1/banking/month/2026-10/transactions/premium-auto/category:TRANSPORT',
    ]);
    expect(find.textContaining('2 boekingen · netto € 35,00'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('extra selectie houdt onbekende betalingen apart', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        home: BankingScreen(api: BankApi(), initialMonth: '2026-10'),
      ),
    );
    await tester.pumpAndSettle();
    await revealAndTap(tester, find.text('Overig / extra'));
    await revealAndTap(tester, find.text('Uitje'));
    expect(find.text('Uitje · −€ 20,25'), findsOneWidget);
    expect(find.textContaining('Onbekende winkel ·'), findsNothing);
    expect(find.textContaining('Eigen rekening ·'), findsNothing);
    await revealAndTap(
      tester,
      find.widgetWithText(ChoiceChip, 'Nog te beoordelen'),
    );
    await revealAndTap(tester, find.text('Onbekende winkel'));
    expect(
      find.textContaining('Vraag: Waarvoor was deze aankoop?'),
      findsOneWidget,
    );
    expect(find.textContaining('Uitje ·'), findsNothing);
  });

  testWidgets(
    'kleding heeft eigen detailpagina en is als correctie te kiezen',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1100, 1600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = BankApi()..overrides['review'] = 'CLOTHING';
      await tester.pumpWidget(
        MaterialApp(
          home: BankingScreen(api: api, initialMonth: '2026-10'),
        ),
      );
      await tester.pumpAndSettle();
      await revealAndTap(tester, find.text('Kleding'));
      expect(
        find.textContaining('1 boekingen · netto € 70,00'),
        findsOneWidget,
      );
      await revealAndTap(tester, find.text('Onbekende winkel'));
      await revealAndTap(tester, find.text('Onbekende winkel · −€ 70,00'));
      await revealAndTap(
        tester,
        find.descendant(
          of: find.byType(SimpleDialog),
          matching: find.text('Kleding'),
        ),
      );
      expect(api.writes, [
        '/api/v1/banking/month/2026-10/transactions/review/category:CLOTHING',
      ]);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('historiefout laat maandgegevens zichtbaar en biedt herstel', (
    tester,
  ) async {
    final api = BankApi()..historyFails = true;
    await tester.pumpWidget(
      MaterialApp(
        home: BankingScreen(api: api, initialMonth: '2026-10'),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Waar ging ons geld heen?'), findsOneWidget);
    expect(find.textContaining('Opnieuw proberen'), findsOneWidget);
    api.historyFails = false;
    await revealAndTap(tester, find.textContaining('Opnieuw proberen'));
    expect(find.text('Netto uitgegaan per maand'), findsOneWidget);
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
