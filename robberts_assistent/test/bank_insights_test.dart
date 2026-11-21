import 'package:flutter_test/flutter_test.dart';
import 'package:robberts_assistent/bank_insights.dart';

Map<String, dynamic> transaction(
  String id,
  dynamic amount,
  String category, {
  String name = 'Winkel',
  String iban = '',
  String date = '2026-10-15',
}) => {
  'id': id,
  'amount': amount,
  'category': category,
  'counterparty': name,
  'counterpartyIban': iban,
  'date': date,
};

void main() {
  test('decimal conversion rounds before summing, without binary drift', () {
    expect(bankCents('1.005'), 101);
    expect(bankCents('-1.005'), -101);
    expect(bankCents('0.0049'), 0);
    expect(bankCents('0.005'), 1);
    expect(bankCents(0.29), 29);
    expect(bankCents('1.01e2'), 10100);
    expect(bankCents('2e-2'), 2);
    expect(bankCents(null), 0);
    expect(() => bankCents(double.nan), throwsFormatException);
    expect(() => bankCents('not money'), throwsFormatException);
    expect(() => bankCents('1e100'), throwsFormatException);
    expect(() => bankCents('90071992547409.92'), throwsFormatException);
    expect(bankMoney(1234567.89), '€ 1.234.567,89');
    expect(bankMoneyCents(-105), '−€ 1,05');
    expect(bankMoneyCents(0), '€ 0,00');
  });

  test('internal transfers excluded; receipts remain reconcilable', () {
    final insights = BankMonthInsights.fromReport({
      'spent': 99999, // Never mix an old aggregate with current row data.
      'transactions': [
        transaction('salary', 2000, 'INCOME'),
        transaction('shop', -100, 'GROCERIES'),
        transaction('refund', 15, 'GROCERIES'),
        transaction('review', -25, 'REVIEW'),
        transaction('card', -30, 'CREDIT_CARD'),
        transaction('unknown-income', 7, 'REVIEW'),
        transaction('between-out', -500, 'INTERNAL'),
        transaction('between-in', 500, 'INTERNAL'),
      ],
    });
    expect(insights.grossSpent, 15500);
    expect(insights.received, 202200);
    expect(insights.income, 200000);
    expect(insights.refunds, 2200);
    expect(insights.netSpent, 13300);
    expect(insights.cashDifference, 186700);
    expect(insights.income - insights.netSpent, insights.cashDifference);
    expect(insights.reviewSpent, 2500);
    expect(insights.creditCardSpent, 3000);
    expect(insights.transactions, hasLength(6));
    expect(insights.byCategory, isNot(contains('INTERNAL')));
    final groceries = insights.byCategory['GROCERIES']!;
    expect(groceries.grossSpent, 10000);
    expect(groceries.received, 1500);
    expect(groceries.netSpent, 8500);
    expect(groceries.count, 2);
    expect(groceries.transactions.map((t) => t['id']), ['shop', 'refund']);
  });

  test('unknown category stays in totals and empty report is safe', () {
    final insights = BankMonthInsights.fromReport({
      'transactions': [
        {...transaction('a', '-0.10', ''), 'category': null},
        transaction('b', '-0.20', 'NEW_CATEGORY'),
        transaction('c', '-0.30', 'REVIEW'),
      ],
    });
    expect(insights.grossSpent, 60);
    expect(insights.reviewSpent, 40);
    expect(insights.byCategory['NEW_CATEGORY']!.netSpent, 20);
    expect(BankMonthInsights.fromReport({}).categories, isEmpty);
  });

  test(
    'merchant grouping normalizes names but respects different known IBANs',
    () {
      final category = BankMonthInsights.fromReport({
        'transactions': [
          transaction(
            'a',
            -30,
            'INSURANCE',
            name: '  Provider  One ',
            iban: 'nl00 test 0001',
          ),
          transaction(
            'b',
            -35,
            'INSURANCE',
            name: 'provider one',
            iban: 'NL00TEST0001',
          ),
          transaction(
            'c',
            -10,
            'INSURANCE',
            name: 'Provider One',
            iban: 'NL00TEST0002',
          ),
          transaction(
            'd',
            5,
            'INSURANCE',
            name: 'provider one',
            iban: 'NL00TEST0001',
          ),
          transaction('e', -7, 'INSURANCE', name: '', iban: ''),
          transaction('f', -6, 'INSURANCE', name: '', iban: ''),
        ],
      }).byCategory['INSURANCE']!;
      expect(category.merchants, hasLength(4));
      final first = category.merchants.first;
      expect(first.label, 'Provider One');
      expect(first.iban, 'NL00TEST0001');
      expect(first.count, 3);
      expect(first.grossSpent, 6500);
      expect(first.received, 500);
      expect(first.netSpent, 6000);
      expect(
        category.merchants.where((m) => m.label == 'Onbekende tegenpartij'),
        hasLength(2),
      );
    },
  );

  test(
    'trend distinguishes missing, empty and ongoing months without completeness claim',
    () {
      final trends = bankTrends(
        [
          {
            'month': '2026-08',
            'transactions': [
              transaction('a', -100, 'GROCERIES', date: '2026-08-29'),
            ],
          },
          {'month': '2026-10', 'transactions': []},
          {
            'month': '2026-11',
            'transactions': [
              transaction('b', -25, 'GROCERIES', date: '2026-11-05'),
              transaction('c', 5, 'GROCERIES', date: '2026-11-06'),
              transaction('d', -250, 'INTERNAL', date: '2026-11-11'),
              // Incorrectly routed data cannot leak into this month's chart.
              transaction('e', -99, 'GROCERIES', date: '2026-12-01'),
            ],
          },
        ],
        category: 'GROCERIES',
        now: DateTime(2026, 11, 21),
      );
      expect(trends.map((t) => t.month), [
        '2026-08',
        '2026-09',
        '2026-10',
        '2026-11',
      ]);
      expect(trends.first.netSpent, 10000);
      expect(
        trends.first.coverageLabel,
        'Opgeslagen boekingen: 2026-08-29 t/m 2026-08-29',
      );
      expect(trends[1].missingReport, isTrue);
      expect(trends[1].coverageLabel, 'Geen maandgegevens');
      expect(trends[2].missingReport, isFalse);
      expect(trends[2].coverageLabel, 'Geen boekingen opgeslagen');
      expect(trends.last.netSpent, 2000);
      expect(trends.last.isCurrentMonth, isTrue);
      expect(trends.last.coverageLabel, 'Lopende maand · t/m 2026-11-11');
      expect(trends.last.firstDate, '2026-11-05');
    },
  );

  test('trend bounds can represent entirely unavailable history', () {
    final empty = bankTrends([], fromMonth: '2026-01', toMonth: '2026-03');
    expect(empty, hasLength(3));
    expect(empty.every((m) => m.missingReport && !m.hasTransactions), isTrue);
    expect(bankTrends([]), isEmpty);
    expect(
      () => bankTrends([], fromMonth: '2026-13', toMonth: '2026-14'),
      throwsFormatException,
    );
  });
}
