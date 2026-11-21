import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/bank_merchant_review.dart';
import 'package:robberts_assistent/banking_screen.dart';

Map<String, dynamic> group(
  String key,
  String merchant, {
  bool canLearn = true,
  int count = 2,
  num spent = 80,
}) => {
  'key': key,
  'merchant': merchant,
  'count': count,
  'spent': spent,
  'from': '2025-12-03',
  'to': '2026-10-19',
  'canLearn': canLearn,
  'examples': [
    {
      'id': 'payment-$key',
      'date': '2025-12-03',
      'amount': -40,
      'description': 'Aankoop bij $merchant',
    },
  ],
};

class MerchantApi extends ApiClient {
  final requests = <String>[];
  final writes = <Map<String, dynamic>>[];
  var groups = [group('clothes', 'Test Kledingzaak')];
  bool failSave = false;
  bool failGet = false;

  @override
  Future<Map<String, dynamic>> getJson(String path) async {
    requests.add(path);
    if (failGet) throw StateError('offline');
    return {
      'groups': groups,
      'transactionCount': groups.fold<int>(
        0,
        (sum, g) => sum + (g['count'] as int),
      ),
      'from': '2025-11-21',
      'to': '2026-11-20',
    };
  }

  @override
  Future<Map<String, dynamic>> putJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    writes.add({'path': path, ...body});
    if (failSave) throw StateError('offline');
    groups = [];
    return {'count': 2};
  }
}

Widget app(MerchantApi api, {Future<void> Function()? onSaved}) => MaterialApp(
  home: Scaffold(
    body: SingleChildScrollView(
      child: BankMerchantReview(
        api: api,
        categories: bankCategories,
        onSaved: onSaved ?? () async {},
      ),
    ),
  ),
);

Future<void> tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'whole history loads on opening and category choice saves exact merchant then refreshes',
    (tester) async {
      final api = MerchantApi();
      var saved = 0;
      await tester.pumpWidget(
        app(
          api,
          onSaved: () async {
            saved++;
          },
        ),
      );
      expect(api.requests, isEmpty);
      await tap(tester, find.text('Winkels indelen'));
      expect(api.requests, ['/api/v1/banking/review-merchants']);
      expect(find.textContaining('2025-11-21 t/m 2026-11-20'), findsOneWidget);
      expect(find.text('Test Kledingzaak'), findsOneWidget);
      await tap(tester, find.byKey(const ValueKey('category-clothes')));
      await tap(tester, find.text('Kleding').last);
      await tap(tester, find.text('Indelen en onthouden'));
      expect(api.writes, [
        {
          'path': '/api/v1/banking/review-merchants/clothes',
          'category': 'CLOTHING',
        },
      ]);
      expect(saved, 1);
      expect(
        find.text('Er zijn geen betalingen meer te beoordelen.'),
        findsOneWidget,
      );
      expect(
        find.text('2 betaling(en) ingedeeld als Kleding.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'failed save keeps selected category and retry does not require reselection',
    (tester) async {
      final api = MerchantApi()..failSave = true;
      await tester.pumpWidget(app(api));
      await tap(tester, find.text('Winkels indelen'));
      await tap(tester, find.byKey(const ValueKey('category-clothes')));
      await tap(tester, find.text('Kleding').last);
      await tap(tester, find.text('Indelen en onthouden'));
      expect(
        find.textContaining('Je categoriekeuze blijft staan'),
        findsOneWidget,
      );
      expect(find.text('Test Kledingzaak'), findsOneWidget);
      api.failSave = false;
      await tap(tester, find.text('Indelen en onthouden'));
      expect(api.writes.map((w) => w['category']), ['CLOTHING', 'CLOTHING']);
      expect(
        find.text('Er zijn geen betalingen meer te beoordelen.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'ambiguous payee is corrected per transaction without learning a rule',
    (tester) async {
      final api = MerchantApi()
        ..groups = [
          group('person', 'Betaalverzoek', canLearn: false, count: 1),
        ];
      await tester.pumpWidget(app(api));
      await tap(tester, find.text('Winkels indelen'));
      expect(find.text('Indelen en onthouden'), findsNothing);
      expect(find.byKey(const ValueKey('category-person')), findsNothing);
      await tap(tester, find.byTooltip('Deze betaling indelen'));
      expect(
        find.textContaining('Alleen deze betaling verandert'),
        findsOneWidget,
      );
      await tap(
        tester,
        find.descendant(
          of: find.byType(SimpleDialog),
          matching: find.text('Overig / extra'),
        ),
      );
      expect(api.writes, [
        {
          'path':
              '/api/v1/banking/month/2025-12/transactions/payment-person/category',
          'category': 'EXTRA',
          'remember': false,
        },
      ]);
    },
  );

  testWidgets(
    'large list is paged and searchable and sorts by count on small screen',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = MerchantApi()
        ..groups = List.generate(
          25,
          (i) => group(
            'g$i',
            'Winkel ${i.toString().padLeft(2, '0')}',
            count: i + 1,
            spent: 100 - i,
          ),
        );
      await tester.pumpWidget(app(api));
      await tap(tester, find.text('Winkels indelen'));
      expect(find.text('Winkel 00'), findsOneWidget);
      expect(find.text('Winkel 24'), findsNothing);
      expect(find.text('Pagina 1 van 2'), findsOneWidget);
      await tap(tester, find.byTooltip('Volgende winkels'));
      expect(find.text('Winkel 24'), findsOneWidget);
      expect(find.text('Winkel 00'), findsNothing);
      await tester.ensureVisible(find.byType(TextField));
      await tester.enterText(find.byType(TextField), 'Winkel 24');
      await tester.pumpAndSettle();
      expect(find.text('1–1 van 1 winkels / tegenpartijen'), findsOneWidget);
      expect(find.byKey(const ValueKey('merchant-g24')), findsOneWidget);
      await tester.enterText(find.byType(TextField), '');
      await tester.pumpAndSettle();
      await tap(
        tester,
        find.widgetWithText(DropdownButtonFormField<String>, 'Sorteren'),
      );
      await tap(tester, find.text('Meeste betalingen eerst').last);
      expect(find.text('Winkel 24'), findsOneWidget);
      expect(find.text('Winkel 00'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'load failure offers retry without displaying a fake empty result',
    (tester) async {
      final api = MerchantApi()..failGet = true;
      await tester.pumpWidget(app(api));
      await tap(tester, find.text('Winkels indelen'));
      expect(find.textContaining('De winkellijst kon niet'), findsOneWidget);
      expect(
        find.text('Er zijn geen betalingen meer te beoordelen.'),
        findsNothing,
      );
      api.failGet = false;
      await tap(tester, find.text('Winkellijst vernieuwen'));
      expect(find.text('Test Kledingzaak'), findsOneWidget);
    },
  );
}
