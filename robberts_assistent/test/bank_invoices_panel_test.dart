import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/api_client.dart';
import 'package:robberts_assistent/bank_invoices_panel.dart';

Map<String, dynamic> invoice(
  String number,
  String status, {
  bool overdue = false,
  bool manual = false,
  List<Map<String, dynamic>> payments = const [],
  List<Map<String, dynamic>> candidates = const [],
}) => {
  'number': number,
  'date': '2026-10-01',
  'dueDate': '2026-10-31',
  'description': 'Ontwerp $number',
  'amount': 121,
  'customer': 'Klant $number',
  'status': status,
  'overdue': overdue,
  'reason': 'Reden $number',
  'manual': manual,
  'payments': payments,
  'candidates': candidates,
};

Map<String, dynamic> payment(String id) => {
  'transactionId': id,
  'date': '2026-10-12',
  'amount': 121,
  'counterparty': 'Betaler $id',
  'description': '',
};

class InvoiceApi extends ApiClient {
  final writes = <Map<String, dynamic>>[];
  bool rejectImport = false;
  var invoices = <Map<String, dynamic>>[];

  Map<String, dynamic> report() => {
    'invoices': invoices,
    'paid': {'count': 1, 'amount': 121},
    'likely': {'count': 1, 'amount': 121},
    'open': {'count': 1, 'amount': 121},
    'overdue': {'count': 1, 'amount': 121},
    'importedAt': invoices.isEmpty ? null : '2026-11-21T15:00:00Z',
    'warnings': ['Alleen gekoppelde rekeningen tellen mee.'],
  };

  @override
  Future<Map<String, dynamic>> getJson(String path) async => report();

  @override
  Future<Map<String, dynamic>> postJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    writes.add({'path': path, ...body});
    if (rejectImport) throw Exception('HTTP 400: {"message":"nee"}');
    invoices = [
      invoice('1', 'OPEN', overdue: true, candidates: [payment('c')]),
      invoice('2', 'LIKELY', payments: [payment('l')]),
      invoice('3', 'PAID', manual: true),
    ];
    return report();
  }

  @override
  Future<Map<String, dynamic>> putJson(
    String path, [
    Map<String, dynamic> body = const {},
  ]) async {
    writes.add({'path': path, ...body});
    return report();
  }
}

void main() {
  Future<InvoiceApi> pump(WidgetTester tester, {Uint8List? file}) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final api = InvoiceApi();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: BankInvoicesPanel(api: api, pickFile: () async => file),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return api;
  }

  testWidgets('export inlezen toont open facturen en stuurt het bestand mee', (
    tester,
  ) async {
    final api = await pump(tester, file: Uint8List.fromList([1, 2, 3]));
    expect(find.textContaining('Alleen gekoppelde'), findsOneWidget);
    expect(find.text('Waarvan te laat'), findsNothing);
    await tester.tap(find.text('Rompslomp-export inlezen'));
    await tester.pumpAndSettle();
    expect(api.writes.single, {
      'path': '/api/v1/banking/invoices/import',
      'content': base64Encode([1, 2, 3]),
    });
    expect(find.text('Export ingelezen.'), findsOneWidget);
    expect(find.text('Waarvan te laat'), findsOneWidget);
    // Standaard alleen wat nog open staat.
    expect(find.text('Klant 1'), findsOneWidget);
    expect(find.text('Klant 2'), findsNothing);
    expect(find.text('kritiek · te laat'), findsOneWidget);
    expect(find.text('1 · 01-10-2026 · vervalt 31-10-2026'), findsOneWidget);
  });

  testWidgets('keuzes per factuur gaan naar de backend', (tester) async {
    final api = await pump(tester, file: Uint8List(1));
    await tester.tap(find.text('Rompslomp-export inlezen'));
    await tester.pumpAndSettle();
    api.writes.clear();
    await tester.tap(find.text('Dit is de betaling'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ChoiceChip, 'Waarschijnlijk betaald'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dit is niet de betaling'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ChoiceChip, 'Betaald'));
    await tester.pumpAndSettle();
    expect(find.text('Markeer als betaald'), findsNothing);
    await tester.tap(find.text('Eigen keuze wissen'));
    await tester.pumpAndSettle();
    expect(api.writes, [
      {
        'path': '/api/v1/banking/invoices/1',
        'action': 'PAID',
        'transactionId': 'c',
      },
      {
        'path': '/api/v1/banking/invoices/2',
        'action': 'REJECT',
        'transactionId': 'l',
      },
      {
        'path': '/api/v1/banking/invoices/3',
        'action': 'RESET',
        'transactionId': '',
      },
    ]);
  });

  testWidgets('verkeerd bestand en annuleren geven geen crash', (tester) async {
    final api = await pump(tester);
    await tester.tap(find.text('Rompslomp-export inlezen'));
    await tester.pumpAndSettle();
    expect(api.writes, isEmpty);

    final failing = await pump(tester, file: Uint8List(1));
    failing.rejectImport = true;
    await tester.tap(find.text('Rompslomp-export inlezen'));
    await tester.pumpAndSettle();
    expect(find.textContaining('geen bruikbare factuurexport'), findsOneWidget);
  });
}
