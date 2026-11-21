/// Financial calculations use integer cents throughout. Convert API decimals at
/// the boundary, before summing, to avoid binary floating point drift.
int bankCents(dynamic value) {
  if (value == null) return 0;
  final text = value.toString().trim();
  final match = RegExp(
    r'^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$',
  ).firstMatch(text);
  if (match == null) throw FormatException('Ongeldig geldbedrag', text);
  final fraction = match.group(3) ?? '';
  final exponent = int.tryParse(match.group(4) ?? '0');
  if (exponent == null || exponent.abs() > 20 || text.length > 80) {
    throw FormatException('Geldbedrag buiten bereik', text);
  }
  var absolute = BigInt.parse('${match.group(2)}$fraction');
  final scale = 2 + exponent - fraction.length;
  if (scale >= 0) {
    absolute *= BigInt.from(10).pow(scale);
  } else {
    final divisor = BigInt.from(10).pow(-scale);
    // Round half away from zero, including negative amounts.
    absolute = (absolute + divisor ~/ BigInt.two) ~/ divisor;
  }
  if (absolute > BigInt.from(9007199254740991)) {
    throw FormatException('Geldbedrag buiten bereik', text);
  }
  return (match.group(1) == '-' ? -absolute : absolute).toInt();
}

String bankMoney(num euro) => bankMoneyCents(bankCents(euro));

String bankMoneyCents(int cents) {
  final absolute = cents.abs();
  final whole = (absolute ~/ 100).toString().replaceAllMapped(
    RegExp(r'\B(?=(\d{3})+(?!\d))'),
    (_) => '.',
  );
  return '${cents < 0 ? '−' : ''}€ $whole,${(absolute % 100).toString().padLeft(2, '0')}';
}

List<Map<String, dynamic>> _bankRows(dynamic value) =>
    (value as List? ?? const [])
        .map((row) => Map<String, dynamic>.from(row as Map))
        .toList(growable: false);

String _category(Map<String, dynamic> row) =>
    (row['category'] as String?)?.trim().isNotEmpty == true
    ? row['category'] as String
    : 'REVIEW';

int _spent(Iterable<Map<String, dynamic>> rows) => rows.fold(0, (sum, row) {
  final cents = bankCents(row['amount']);
  return sum + (cents < 0 ? -cents : 0);
});

int _received(Iterable<Map<String, dynamic>> rows) => rows.fold(0, (sum, row) {
  final cents = bankCents(row['amount']);
  return sum + (cents > 0 ? cents : 0);
});

/// The source transactions are authoritative. Summary fields from an older API
/// response are deliberately not mixed with a potentially different row set.
class BankMonthInsights {
  BankMonthInsights.fromReport(Map<String, dynamic> report)
    : transactions = _bankRows(
        report['transactions'],
      ).where((row) => _category(row) != 'INTERNAL').toList(growable: false) {
    grossSpent = _spent(transactions);
    received = _received(transactions);
    income = _received(transactions.where((row) => _category(row) == 'INCOME'));
    refunds = received - income;
    netSpent = grossSpent - refunds;
    cashDifference = received - grossSpent;
    reviewSpent = _spent(
      transactions.where((row) => _category(row) == 'REVIEW'),
    );
    creditCardSpent = _spent(
      transactions.where((row) => _category(row) == 'CREDIT_CARD'),
    );
    final grouped = <String, List<Map<String, dynamic>>>{};
    for (final row in transactions) {
      grouped.putIfAbsent(_category(row), () => []).add(row);
    }
    byCategory = Map.unmodifiable({
      for (final entry in grouped.entries)
        entry.key: BankCategoryInsights(entry.key, entry.value),
    });
    categories = byCategory.values.toList()
      ..sort((a, b) {
        final amount = b.netSpent.compareTo(a.netSpent);
        return amount != 0 ? amount : a.category.compareTo(b.category);
      });
  }

  final List<Map<String, dynamic>> transactions;
  late final int grossSpent;
  late final int received;
  late final int income;

  /// Receipts allocated to expense categories. A bank description alone does
  /// not prove they are refunds; present this as "terug/overig ontvangen".
  late final int refunds;
  late final int netSpent;
  late final int cashDifference;
  late final int reviewSpent;
  late final int creditCardSpent;
  late final Map<String, BankCategoryInsights> byCategory;
  late final List<BankCategoryInsights> categories;
}

class BankCategoryInsights {
  BankCategoryInsights(this.category, List<Map<String, dynamic>> rows)
    : transactions = List.unmodifiable(rows),
      grossSpent = _spent(rows),
      received = _received(rows) {
    final grouped = <String, List<Map<String, dynamic>>>{};
    for (var i = 0; i < rows.length; i++) {
      final row = rows[i];
      final name = _merchantLabel(row);
      final iban = _merchantIban(row);
      // A blank name and blank IBAN do not identify a single merchant.
      final identity = name == 'Onbekende tegenpartij' && iban.isEmpty
          ? 'unknown:${row['id'] ?? i}'
          : '${name.toLowerCase()}\u0000$iban';
      grouped.putIfAbsent(identity, () => []).add(row);
    }
    merchants =
        grouped.entries
            .map((entry) => BankMerchantInsights(entry.key, entry.value))
            .toList()
          ..sort((a, b) {
            final amount = b.netSpent.compareTo(a.netSpent);
            return amount != 0 ? amount : a.label.compareTo(b.label);
          });
  }

  final String category;
  final List<Map<String, dynamic>> transactions;
  final int grossSpent;
  final int received;
  int get netSpent => grossSpent - received;
  int get count => transactions.length;
  late final List<BankMerchantInsights> merchants;
}

String _merchantLabel(Map<String, dynamic> row) {
  final name = '${row['counterparty'] ?? ''}'.trim().replaceAll(
    RegExp(r'\s+'),
    ' ',
  );
  return name.isEmpty ? 'Onbekende tegenpartij' : name;
}

String _merchantIban(Map<String, dynamic> row) =>
    '${row['counterpartyIban'] ?? ''}'
        .replaceAll(RegExp(r'\s+'), '')
        .toUpperCase();

class BankMerchantInsights {
  BankMerchantInsights(this.key, List<Map<String, dynamic>> rows)
    : transactions = List.unmodifiable(rows),
      label = _merchantLabel(rows.first),
      iban = _merchantIban(rows.first),
      grossSpent = _spent(rows),
      received = _received(rows);

  final String key;
  final String label;
  final String iban;
  final List<Map<String, dynamic>> transactions;
  final int grossSpent;
  final int received;
  int get netSpent => grossSpent - received;
  int get count => transactions.length;
}

class BankMonthTrend {
  const BankMonthTrend({
    required this.month,
    required this.grossSpent,
    required this.received,
    required this.netSpent,
    required this.hasTransactions,
    required this.isCurrentMonth,
    required this.missingReport,
    this.firstDate,
    this.lastDate,
  });

  final String month;
  final int grossSpent;
  final int received;
  final int netSpent;

  /// Whether the month has ANY stored transactions, including internal ones.
  /// False is not proof that the household spent nothing.
  final bool hasTransactions;
  final bool isCurrentMonth;
  final bool missingReport;
  final String? firstDate;
  final String? lastDate;
  String get coverageLabel => missingReport
      ? 'Geen maandgegevens'
      : !hasTransactions
      ? 'Geen boekingen opgeslagen'
      : isCurrentMonth
      ? 'Lopende maand · t/m $lastDate'
      : 'Opgeslagen boekingen: $firstDate t/m $lastDate';
}

/// Trends report observed data without assuming a month is complete. Optional
/// bounds add missing months explicitly instead of silently drawing zero spend.
List<BankMonthTrend> bankTrends(
  List<dynamic> reports, {
  String? category,
  DateTime? now,
  String? fromMonth,
  String? toMonth,
}) {
  String validMonth(dynamic value) {
    final text = '$value';
    if (!RegExp(r'^\d{4}-(0[1-9]|1[0-2])$').hasMatch(text)) {
      throw FormatException('Ongeldige maand', text);
    }
    return text;
  }

  final mapped = <String, Map<String, dynamic>>{};
  for (final report in reports) {
    final row = Map<String, dynamic>.from(report as Map);
    mapped[validMonth(row['month'])] = row;
  }
  final keys = mapped.keys.toList()..sort();
  if (keys.isEmpty && (fromMonth == null || toMonth == null)) return [];
  final first = validMonth(fromMonth ?? keys.first);
  final last = validMonth(toMonth ?? keys.last);
  if (first.compareTo(last) > 0) throw ArgumentError('Ongeldige periode');
  final beginning = DateTime.parse('$first-01');
  final end = DateTime.parse('$last-01');
  final length = (end.year - beginning.year) * 12 + end.month - beginning.month;
  if (length > 1200) throw ArgumentError('Periode is te groot');
  final current = now ?? DateTime.now();
  final currentKey =
      '${current.year}-${current.month.toString().padLeft(2, '0')}';
  return List.generate(length + 1, (index) {
    final date = DateTime(beginning.year, beginning.month + index);
    final key =
        '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}';
    final report = mapped[key];
    final rows = _bankRows(
      report?['transactions'],
    ).where((row) => '${row['date'] ?? ''}'.startsWith('$key-')).toList();
    final insights = BankMonthInsights.fromReport({'transactions': rows});
    final item = category == null ? null : insights.byCategory[category];
    final dates = rows.map((row) => '${row['date']}').toList()..sort();
    return BankMonthTrend(
      month: key,
      grossSpent: category == null
          ? insights.grossSpent
          : item?.grossSpent ?? 0,
      received: category == null ? insights.received : item?.received ?? 0,
      netSpent: category == null ? insights.netSpent : item?.netSpent ?? 0,
      hasTransactions: rows.isNotEmpty,
      isCurrentMonth: key == currentKey,
      missingReport: report == null,
      firstDate: dates.isEmpty ? null : dates.first,
      lastDate: dates.isEmpty ? null : dates.last,
    );
  });
}
