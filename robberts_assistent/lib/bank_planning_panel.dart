import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'api_client.dart';

const _months = [
  'Januari',
  'Februari',
  'Maart',
  'April',
  'Mei',
  'Juni',
  'Juli',
  'Augustus',
  'September',
  'Oktober',
  'November',
  'December',
];

/// Money is calculated as whole cents. Text inputs accept a decimal comma or dot,
/// never silently interpret thousands separators or discard fractional cents.
int? bankPlanCents(dynamic value) {
  if (value == null || '$value'.trim().isEmpty) return null;
  final text = '$value'.trim().replaceAll(',', '.');
  final match = RegExp(r'^(\d+)(?:\.(\d{1,2}))?$').firstMatch(text);
  if (match == null) return null;
  final whole = int.tryParse(match[1]!);
  if (whole == null || whole > 10000000) return null;
  final cents = whole * 100 + int.parse((match[2] ?? '').padRight(2, '0'));
  return cents <= 1000000000 ? cents : null;
}

String _money(int cents) {
  final absolute = cents.abs();
  final whole = (absolute ~/ 100).toString().replaceAllMapped(
    RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
    (m) => '${m[1]}.',
  );
  return '${cents < 0 ? '− ' : ''}€ $whole,${(absolute % 100).toString().padLeft(2, '0')}';
}

String _input(dynamic value) {
  final cents = bankPlanCents(value);
  return cents == null
      ? ''
      : '${cents ~/ 100},${(cents % 100).toString().padLeft(2, '0')}';
}

List<Map<String, dynamic>> _items(Map<String, dynamic> plan) =>
    (plan['items'] as List? ?? [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
Map<String, dynamic> _copy(Map<String, dynamic> plan) => {
  ...plan,
  'budgets': Map<String, dynamic>.from(plan['budgets'] as Map? ?? {}),
  'items': _items(plan),
};

class BankPlanMonth {
  const BankPlanMonth(
    this.month,
    this.plannedIncome,
    this.plannedExpenses,
    this.income,
    this.expenses,
    this.reserve,
  );
  final int month, plannedIncome, plannedExpenses;
  final int? income, expenses, reserve;
}

List<BankPlanMonth> bankPlanProjection(Map<String, dynamic> plan) {
  final baseIncome = bankPlanCents(plan['monthlyIncome']);
  final baseExpenses = bankPlanCents(plan['monthlyExpenses']);
  final complete = baseIncome != null && baseExpenses != null;
  var reserve = bankPlanCents(plan['openingReserve']) ?? 0;
  final items = _items(plan);
  return List.generate(12, (index) {
    final monthItems = items.where((item) => item['month'] == index + 1);
    int sum(String kind) => monthItems
        .where((item) => item['kind'] == kind)
        .fold(0, (total, item) => total + (bankPlanCents(item['amount']) ?? 0));
    final plus = sum('INCOME'), minus = sum('EXPENSE');
    final income = baseIncome == null ? null : baseIncome + plus;
    final expenses = baseExpenses == null ? null : baseExpenses + minus;
    if (complete) reserve += income! - expenses!;
    return BankPlanMonth(
      index + 1,
      plus,
      minus,
      income,
      expenses,
      complete ? reserve : null,
    );
  });
}

class BankPlanningPanel extends StatefulWidget {
  const BankPlanningPanel({
    super.key,
    required this.api,
    required this.initialYear,
    required this.categories,
    this.onYearChanged,
    this.onDirtyChanged,
  });
  final ApiClient api;
  final int initialYear;
  final Map<String, String> categories;
  final ValueChanged<int>? onYearChanged;
  final ValueChanged<bool>? onDirtyChanged;
  @override
  State<BankPlanningPanel> createState() => _BankPlanningPanelState();
}

class _BankPlanningPanelState extends State<BankPlanningPanel> {
  late int _year;
  Map<String, dynamic>? _plan;
  bool _loading = true, _saving = false, _dirty = false;
  String? _error;
  int _version = 0;
  Color get _teal => Theme.of(context).colorScheme.primary;
  Color get _muted => Theme.of(context).colorScheme.onSurfaceVariant;
  @override
  void initState() {
    super.initState();
    _year = widget.initialYear;
    _load();
  }

  @override
  void didUpdateWidget(covariant BankPlanningPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.initialYear != oldWidget.initialYear &&
        widget.initialYear != _year) {
      // Browser navigation must not silently erase an unsaved edit.
      if (_dirty) {
        widget.onYearChanged?.call(_year);
        return;
      }
      _year = widget.initialYear;
      _load();
    }
  }

  Future<void> _load() async {
    final version = ++_version, year = _year;
    setState(() {
      _loading = true;
      _error = null;
      _plan = null;
    });
    try {
      final data = await widget.api.getJson(
        '/api/v1/banking/planning?year=$year',
      );
      if (!mounted || version != _version) return;
      setState(() {
        _plan = {...data, 'year': year};
        _dirty = false;
      });
    } catch (_) {
      if (mounted && version == _version) {
        setState(
          () => _error = 'Jaarplanning laden is niet gelukt. Probeer opnieuw.',
        );
      }
    } finally {
      if (mounted && version == _version) setState(() => _loading = false);
    }
  }

  Future<void> _save([Map<String, dynamic>? changed]) async {
    if (_saving || _plan == null) return;
    final version = _version, year = _year;
    setState(() {
      if (changed != null) _plan = changed;
      _dirty = true;
      _saving = true;
      _error = null;
    });
    widget.onDirtyChanged?.call(true);
    final sent = _copy(_plan!)..remove('inheritedFromYear');
    try {
      final saved = await widget.api.putJson(
        '/api/v1/banking/planning?year=$year',
        sent,
      );
      if (!mounted || version != _version) return;
      setState(() {
        _plan = saved.containsKey('year') ? saved : sent;
        _dirty = false;
      });
      widget.onDirtyChanged?.call(false);
    } catch (_) {
      if (mounted && version == _version) {
        setState(
          () => _error =
              'Niet opgeslagen. Je wijzigingen staan nog op dit scherm; probeer opnieuw op te slaan.',
        );
      }
    } finally {
      if (mounted && version == _version) setState(() => _saving = false);
    }
  }

  Future<void> _yearChange(int delta) async {
    if (_saving || _loading) return;
    if (_dirty) {
      final discard = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Niet-opgeslagen wijzigingen'),
          content: const Text(
            'Wil je deze wijzigingen weggooien en een ander jaar openen?',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Blijven'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Weggooien'),
            ),
          ],
        ),
      );
      if (discard != true || !mounted) return;
      _dirty = false;
      widget.onDirtyChanged?.call(false);
    }
    setState(() => _year += delta);
    widget.onYearChanged?.call(_year);
    await _load();
  }

  String? _validMoney(String? text, {bool optional = false}) {
    if (optional && (text ?? '').trim().isEmpty) return null;
    return bankPlanCents(text) == null
        ? 'Vul 0 t/m 10.000.000 in, met maximaal 2 decimalen.'
        : null;
  }

  Widget _moneyField(
    String label,
    TextEditingController controller, {
    bool optional = false,
  }) => TextFormField(
    controller: controller,
    keyboardType: const TextInputType.numberWithOptions(decimal: true),
    decoration: InputDecoration(
      labelText: label,
      prefixText: '€ ',
      errorMaxLines: 3,
      helperMaxLines: 2,
      helperText: optional
          ? 'Leeg laten als nog onbekend'
          : 'Bedrag zonder duizendtallenpunten',
    ),
    validator: (value) => _validMoney(value, optional: optional),
  );
  Future<void> _editBasis() async {
    final plan = _copy(_plan!);
    final income = TextEditingController(text: _input(plan['monthlyIncome']));
    final expenses = TextEditingController(
      text: _input(plan['monthlyExpenses']),
    );
    final reserve = TextEditingController(
      text: _input(plan['openingReserve'] ?? 0),
    );
    final form = GlobalKey<FormState>();
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Maandbasis en beginbuffer'),
        content: SizedBox(
          width: 440,
          child: Form(
            key: form,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text(
                    'Gebruik je verwachte gewone inkomsten en uitgaven. Vakanties, jaarlijkse rekeningen en bijzondere inkomsten voeg je hieronder apart toe; tel die hier niet nogmaals mee.',
                  ),
                  const SizedBox(height: 16),
                  _moneyField(
                    'Gewone inkomsten per maand',
                    income,
                    optional: true,
                  ),
                  const SizedBox(height: 16),
                  _moneyField(
                    'Gewone uitgaven per maand',
                    expenses,
                    optional: true,
                  ),
                  const SizedBox(height: 16),
                  _moneyField('Beschikbare buffer op 1 januari', reserve),
                ],
              ),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () {
              if (form.currentState!.validate()) Navigator.pop(context, true);
            },
            child: const Text('Opslaan'),
          ),
        ],
      ),
    );
    if (accepted == true && mounted) {
      plan['monthlyIncome'] = bankPlanCents(income.text) == null
          ? null
          : bankPlanCents(income.text)! / 100;
      plan['monthlyExpenses'] = bankPlanCents(expenses.text) == null
          ? null
          : bankPlanCents(expenses.text)! / 100;
      plan['openingReserve'] = bankPlanCents(reserve.text)! / 100;
      await _save(plan);
    }
    // Dialog route animations can still reference its controllers after pop.
    await Future<void>.delayed(const Duration(milliseconds: 300));
    income.dispose();
    expenses.dispose();
    reserve.dispose();
  }

  Future<void> _editItem([Map<String, dynamic>? existing]) async {
    final title = TextEditingController(text: existing?['title'] ?? '');
    final amount = TextEditingController(text: _input(existing?['amount']));
    var month = (existing?['month'] as num?)?.toInt() ?? 1;
    var kind = existing?['kind'] as String? ?? 'EXPENSE';
    var certainty = existing?['certainty'] as String? ?? 'ESTIMATE';
    var annual = existing?['annual'] == true;
    final form = GlobalKey<FormState>();
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: Text(
            existing == null ? 'Jaarbedrag toevoegen' : 'Jaarbedrag aanpassen',
          ),
          content: SizedBox(
            width: 440,
            child: Form(
              key: form,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    TextFormField(
                      controller: title,
                      maxLength: 120,
                      decoration: const InputDecoration(
                        labelText: 'Waarvoor?',
                        hintText: 'Bijvoorbeeld wintersport of vakantiegeld',
                      ),
                      validator: (v) => (v ?? '').trim().isEmpty
                          ? 'Geef een omschrijving.'
                          : null,
                    ),
                    _moneyField('Bedrag', amount),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      isExpanded: true,
                      initialValue: kind,
                      decoration: const InputDecoration(labelText: 'Soort'),
                      items: const [
                        DropdownMenuItem(
                          value: 'EXPENSE',
                          child: Text('Uitgave'),
                        ),
                        DropdownMenuItem(
                          value: 'INCOME',
                          child: Text('Inkomst'),
                        ),
                      ],
                      onChanged: (v) => update(() => kind = v!),
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<int>(
                      isExpanded: true,
                      initialValue: month,
                      decoration: const InputDecoration(
                        labelText: 'Verwachte betaalmaand',
                      ),
                      items: List.generate(
                        12,
                        (i) => DropdownMenuItem(
                          value: i + 1,
                          child: Text(_months[i]),
                        ),
                      ),
                      onChanged: (v) => update(() => month = v!),
                    ),
                    const SizedBox(height: 16),
                    DropdownButtonFormField<String>(
                      isExpanded: true,
                      initialValue: certainty,
                      decoration: const InputDecoration(
                        labelText: 'Hoe zeker is het bedrag?',
                      ),
                      items: const [
                        DropdownMenuItem(
                          value: 'ESTIMATE',
                          child: Text('Schatting'),
                        ),
                        DropdownMenuItem(
                          value: 'PLANNED',
                          child: Text(
                            'Zelf gepland',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                      onChanged: (v) => update(() => certainty = v!),
                    ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Komt ieder jaar terug'),
                      subtitle: const Text(
                        'Volgend jaar overnemen als te controleren schatting',
                      ),
                      value: annual,
                      onChanged: (v) => update(() => annual = v),
                    ),
                    const Text(
                      'Een aanbetaling en restant voeg je toe als twee bedragen in hun eigen betaalmaand.',
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Annuleren'),
            ),
            FilledButton(
              onPressed: () {
                if (form.currentState!.validate()) Navigator.pop(context, true);
              },
              child: const Text('Opslaan'),
            ),
          ],
        ),
      ),
    );
    if (accepted == true && mounted) {
      final plan = _copy(_plan!), items = _items(_plan!);
      final item = <String, dynamic>{
        'id':
            existing?['id'] ?? 'plan-${DateTime.now().microsecondsSinceEpoch}',
        'title': title.text.trim(),
        'amount': bankPlanCents(amount.text)! / 100,
        'month': month,
        'kind': kind,
        'certainty': certainty,
        'annual': annual,
      };
      final index = items.indexWhere((i) => i['id'] == item['id']);
      if (index < 0) {
        items.add(item);
      } else {
        items[index] = item;
      }
      plan['items'] = items;
      await _save(plan);
    }
    await Future<void>.delayed(const Duration(milliseconds: 300));
    title.dispose();
    amount.dispose();
  }

  Future<void> _removeItem(Map<String, dynamic> item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Jaarbedrag verwijderen?'),
        content: Text(
          '${item['title']} verdwijnt uit de planning voor $_year.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Verwijderen'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      final plan = _copy(_plan!);
      plan['items'] = _items(plan).where((i) => i['id'] != item['id']).toList();
      await _save(plan);
    }
  }

  Future<void> _editBudget([String? existing]) async {
    final available = widget.categories.entries
        .where((e) => !['INTERNAL', 'INCOME', 'REVIEW'].contains(e.key))
        .toList();
    if (available.isEmpty) return;
    var category =
        existing ??
        (available.any((e) => e.key == 'EXTRA')
            ? 'EXTRA'
            : available.first.key);
    final budgets = Map<String, dynamic>.from(_plan!['budgets'] as Map? ?? {});
    final amount = TextEditingController(text: _input(budgets[category]));
    final form = GlobalKey<FormState>();
    final accepted = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Maandbudget instellen'),
          content: SizedBox(
            width: 400,
            child: Form(
              key: form,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    DropdownButtonFormField<String>(
                      isExpanded: true,
                      initialValue: category,
                      decoration: const InputDecoration(labelText: 'Categorie'),
                      items: available
                          .map(
                            (e) => DropdownMenuItem(
                              value: e.key,
                              child: Text(
                                e.value,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          )
                          .toList(),
                      onChanged: existing != null
                          ? null
                          : (v) => update(() {
                              category = v!;
                              amount.text = _input(budgets[category]);
                            }),
                    ),
                    const SizedBox(height: 16),
                    _moneyField('Maximaal per maand', amount),
                    const SizedBox(height: 12),
                    const Text(
                      'Een budget is een doel. Het wordt niet extra bij je gewone uitgaven opgeteld.',
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            if (existing != null)
              TextButton(
                onPressed: () => Navigator.pop(context, 'delete'),
                child: const Text('Verwijderen'),
              ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Annuleren'),
            ),
            FilledButton(
              onPressed: () {
                if (form.currentState!.validate()) {
                  Navigator.pop(context, 'save');
                }
              },
              child: const Text('Opslaan'),
            ),
          ],
        ),
      ),
    );
    if (accepted != null && mounted) {
      if (accepted == 'delete') {
        budgets.remove(category);
      } else {
        budgets[category] = bankPlanCents(amount.text)! / 100;
      }
      final plan = _copy(_plan!)..['budgets'] = budgets;
      await _save(plan);
    }
    await Future<void>.delayed(const Duration(milliseconds: 300));
    amount.dispose();
  }

  Widget _panel(String title, Widget child, {Widget? action}) => Container(
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surface,
      border: Border.all(color: Theme.of(context).colorScheme.outline),
      borderRadius: BorderRadius.circular(18),
    ),
    child: Material(
      color: Colors.transparent,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 16,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                title,
                style: Theme.of(
                  context,
                ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              ?action,
            ],
          ),
          const SizedBox(height: 16),
          child,
        ],
      ),
    ),
  );
  Widget _metric(String label, String value, String note) => SizedBox(
    width: 250,
    child: _panel(
      label,
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            value,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.w600,
              color: _teal,
            ),
          ),
          const SizedBox(height: 5),
          Text(note, style: TextStyle(color: _muted, fontSize: 12)),
        ],
      ),
    ),
  );
  Widget _chart(List<BankPlanMonth> projection) => LayoutBuilder(
    builder: (context, constraints) {
      final columns = (constraints.maxWidth / 150).floor().clamp(1, 6);
      final width = (constraints.maxWidth - (columns - 1) * 8) / columns;
      final highest = projection.fold<int>(
        1,
        (m, row) =>
            math.max(m, math.max(row.plannedExpenses, row.plannedIncome)),
      );
      return Wrap(
        spacing: 8,
        runSpacing: 16,
        children: projection
            .map(
              (row) => SizedBox(
                width: width,
                child: Semantics(
                  label:
                      '${_months[row.month - 1]}: geplande uitgaven ${_money(row.plannedExpenses)}, geplande inkomsten ${_money(row.plannedIncome)}',
                  child: Column(
                    children: [
                      SizedBox(
                        height: 110,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Container(
                              width: math.min(width / 3, 22),
                              height: 110 * row.plannedExpenses / highest,
                              decoration: BoxDecoration(
                                color: _teal,
                                borderRadius: const BorderRadius.vertical(
                                  top: Radius.circular(4),
                                ),
                              ),
                            ),
                            const SizedBox(width: 3),
                            Container(
                              width: math.min(width / 3, 22),
                              height: 110 * row.plannedIncome / highest,
                              decoration: BoxDecoration(
                                color: Theme.of(context).colorScheme.tertiary,
                                borderRadius: const BorderRadius.vertical(
                                  top: Radius.circular(4),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _months[row.month - 1].substring(0, 3),
                        style: TextStyle(color: _muted),
                      ),
                      Text(
                        'Uit: ${_money(row.plannedExpenses)}',
                        style: TextStyle(color: _muted, fontSize: 12),
                        textAlign: TextAlign.center,
                      ),
                      Text(
                        'In: ${_money(row.plannedIncome)}',
                        style: TextStyle(color: _muted, fontSize: 12),
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                ),
              ),
            )
            .toList(),
      );
    },
  );
  Widget _monthRows(List<BankPlanMonth> projection) => Column(
    children: projection
        .map(
          (row) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final parts = [
                  Text(
                    _months[row.month - 1],
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  Text(
                    'In: ${row.income == null ? 'basis onbekend' : _money(row.income!)}',
                  ),
                  Text(
                    'Uit: ${row.expenses == null ? 'basis onbekend' : _money(row.expenses!)}',
                  ),
                  Text(
                    'Buffer: ${row.reserve == null ? 'onbekend' : _money(row.reserve!)}',
                    style: TextStyle(
                      color: (row.reserve ?? 0) < 0
                          ? Theme.of(context).colorScheme.error
                          : _teal,
                    ),
                  ),
                ];
                return constraints.maxWidth < 650
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: parts,
                      )
                    : Row(
                        children: parts.map((p) => Expanded(child: p)).toList(),
                      );
              },
            ),
          ),
        )
        .toList(),
  );
  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.all(32),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_plan == null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(_error ?? 'Geen jaarplanning beschikbaar.'),
          TextButton(onPressed: _load, child: const Text('Opnieuw proberen')),
        ],
      );
    }
    final plan = _plan!,
        items = _items(plan)
          ..sort((a, b) => (a['month'] as num).compareTo(b['month'] as num));
    final budgets = Map<String, dynamic>.from(plan['budgets'] as Map? ?? {});
    final projection = bankPlanProjection(plan);
    final plannedExpenses = projection.fold(
      0,
      (sum, p) => sum + p.plannedExpenses,
    );
    final plannedIncome = projection.fold(0, (sum, p) => sum + p.plannedIncome);
    final complete = projection.first.reserve != null;
    final negative = projection.where((p) => (p.reserve ?? 0) < 0).firstOrNull;
    final disabled = _saving;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 16,
          runSpacing: 10,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              'Een jaar vooruit',
              style: Theme.of(
                context,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w600),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: 'Vorig planningsjaar',
                  onPressed: disabled || _year <= 1900
                      ? null
                      : () => _yearChange(-1),
                  icon: const Icon(Icons.chevron_left),
                ),
                Text('$_year', style: Theme.of(context).textTheme.titleLarge),
                IconButton(
                  tooltip: 'Volgend planningsjaar',
                  onPressed: disabled || _year >= 2200
                      ? null
                      : () => _yearChange(1),
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
            Text(
              _saving
                  ? 'Opslaan…'
                  : _dirty
                  ? 'Niet opgeslagen'
                  : 'Planning en schattingen',
              style: TextStyle(
                color: _dirty ? Theme.of(context).colorScheme.error : _muted,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          'Jaarlijkse rekeningen en vakanties op hun betaalmoment. Dit is jouw scenario, geen voorspelling van je banksaldo.',
          style: TextStyle(color: _muted),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
                TextButton(
                  onPressed: disabled ? null : () => _save(),
                  child: const Text('Opnieuw opslaan'),
                ),
              ],
            ),
          ),
        if (plan['inheritedFromYear'] != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(
              'Jaarlijkse bedragen overgenomen uit ${plan['inheritedFromYear']} als schatting. Controleer bedrag en betaalmaand voor $_year.',
            ),
          ),
        const SizedBox(height: 20),
        Wrap(
          spacing: 14,
          runSpacing: 14,
          children: [
            _metric(
              'Geplande jaaruitgaven',
              _money(plannedExpenses),
              '${items.where((i) => i['kind'] == 'EXPENSE').length} bedragen buiten de gewone maandbasis',
            ),
            _metric(
              'Gemiddeld hiervoor reserveren',
              _money((plannedExpenses / 12).round()),
              'Jaaruitgaven ÷ 12; tijdig voldoende buffer nodig',
            ),
            _metric(
              'Bijzondere inkomsten',
              _money(plannedIncome),
              'Alleen bedragen die je zelf hebt ingevoerd',
            ),
          ],
        ),
        const SizedBox(height: 20),
        _panel(
          'Betaalmomenten door het jaar',
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Alleen de extra jaarbedragen hieronder; gewone maandlasten staan in de maandbasis.',
                style: TextStyle(color: _muted),
              ),
              const SizedBox(height: 14),
              if (items.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 20),
                  child: Text(
                    'Voeg bijvoorbeeld wintersport, zomervakantie, jaarlijkse heffingen of verwacht vakantiegeld toe.',
                  ),
                )
              else
                _chart(projection),
              const SizedBox(height: 12),
              Wrap(
                spacing: 20,
                runSpacing: 8,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.square, size: 12, color: _teal),
                      const SizedBox(width: 5),
                      const Text('Uitgaven'),
                    ],
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.square,
                        size: 12,
                        color: Theme.of(context).colorScheme.tertiary,
                      ),
                      const SizedBox(width: 5),
                      const Text('Inkomsten'),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        _panel(
          'Jaarbedragen',
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (items.isEmpty) const Text('Nog geen jaarbedragen ingevuld.'),
              ...items.map(
                (item) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: InkWell(
                          onTap: disabled ? null : () => _editItem(item),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${item['title']}',
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              Text(
                                '${_months[(item['month'] as num).toInt() - 1]} · ${item['kind'] == 'INCOME' ? 'inkomst' : 'uitgave'} · ${_money(bankPlanCents(item['amount']) ?? 0)}',
                              ),
                              Text(
                                '${item['certainty'] == 'PLANNED' ? 'Zelf gepland' : 'Schatting'}${item['annual'] == true ? ' · jaarlijks' : ' · eenmalig'}',
                                style: TextStyle(color: _muted, fontSize: 12),
                              ),
                            ],
                          ),
                        ),
                      ),
                      IconButton(
                        tooltip: '${item['title']} aanpassen',
                        onPressed: disabled ? null : () => _editItem(item),
                        icon: const Icon(Icons.edit_outlined),
                      ),
                      IconButton(
                        tooltip: '${item['title']} verwijderen',
                        onPressed: disabled ? null : () => _removeItem(item),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          action: OutlinedButton.icon(
            onPressed: disabled || items.length >= 100
                ? null
                : () => _editItem(),
            icon: const Icon(Icons.add),
            label: const Text('Jaarbedrag toevoegen'),
          ),
        ),
        const SizedBox(height: 20),
        _panel(
          'Maandbudgetten',
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (budgets.isEmpty)
                const Text(
                  'Stel bijvoorbeeld een doel in voor boodschappen of overig / extra.',
                ),
              ...budgets.entries.map(
                (e) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(widget.categories[e.key] ?? e.key),
                  subtitle: Text(
                    '${_money(bankPlanCents(e.value) ?? 0)} per maand',
                  ),
                  trailing: const Icon(Icons.edit_outlined),
                  onTap: disabled ? null : () => _editBudget(e.key),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Budgetten zijn doelen, geen extra uitgaven boven op de maandbasis.',
                style: TextStyle(color: _muted),
              ),
            ],
          ),
          action: OutlinedButton.icon(
            onPressed: disabled ? null : () => _editBudget(),
            icon: const Icon(Icons.add),
            label: const Text('Budget instellen'),
          ),
        ),
        const SizedBox(height: 20),
        _panel(
          'Maandbasis en beschikbare buffer',
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Gewone inkomsten: ${bankPlanCents(plan['monthlyIncome']) == null ? 'nog onbekend' : '${_money(bankPlanCents(plan['monthlyIncome'])!)} per maand'}',
              ),
              Text(
                'Gewone uitgaven: ${bankPlanCents(plan['monthlyExpenses']) == null ? 'nog onbekend' : '${_money(bankPlanCents(plan['monthlyExpenses'])!)} per maand'}',
              ),
              Text(
                'Buffer op 1 januari: ${_money(bankPlanCents(plan['openingReserve']) ?? 0)}',
              ),
              const SizedBox(height: 12),
              Text(
                'Zet hier alleen gewone maandbedragen. De jaarbedragen hierboven worden erbij opgeteld; reserveren telt niet nogmaals als uitgave.',
                style: TextStyle(color: _muted),
              ),
              if (!complete)
                const Padding(
                  padding: EdgeInsets.only(top: 14),
                  child: Text(
                    'Vul inkomsten én uitgaven per maand in om het verloop van je buffer te berekenen.',
                  ),
                ),
              if (negative != null)
                Padding(
                  padding: const EdgeInsets.only(top: 14),
                  child: Text(
                    'Vanaf ${_months[negative.month - 1].toLowerCase()} is de berekende buffer negatief. Alleen het jaargemiddelde reserveren is dus niet voldoende: er moet eerder geld beschikbaar zijn.',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              if (complete) ...[
                const Divider(height: 32),
                Text(
                  'Verwacht verloop inclusief jaarbedragen',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                _monthRows(projection),
              ],
            ],
          ),
          action: OutlinedButton.icon(
            onPressed: disabled ? null : _editBasis,
            icon: const Icon(Icons.edit_outlined),
            label: const Text('Maandbasis instellen'),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          'Schattingen, waaronder belastingteruggaaf, tellen alleen mee als je ze invoert. Pas ze aan zodra bedrag of betaalmoment bekend is.',
          style: TextStyle(color: _muted, fontSize: 12),
        ),
      ],
    );
  }
}
