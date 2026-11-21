import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api_client.dart';
import 'bank_dashboard.dart';
import 'bank_insights.dart';
import 'bank_planning_panel.dart';

const bankCategories = <String, String>{
  'REVIEW': 'Nog te beoordelen',
  'GROCERIES': 'Boodschappen',
  'HOUSING': 'Wonen en vaste lasten',
  'INSURANCE': 'Verzekeringen',
  'TRANSPORT': 'Vervoer',
  'HEALTH': 'Zorg',
  'TAX': 'Belastingen',
  'BUSINESS': 'Zakelijk',
  'PENSION': 'Pensioen en sparen',
  'CHARITY': 'Goede doelen',
  'EXTRA': 'Overig / extra',
  'HOLIDAY': 'Vakantie',
  'BANK_FEES': 'Bankkosten',
  'CREDIT_CARD': 'Creditcard, nog zonder details',
  'INTERNAL': 'Interne overboeking',
  'INCOME': 'Inkomsten',
};

class BankingScreen extends StatefulWidget {
  const BankingScreen({
    super.key,
    required this.api,
    this.initialMonth,
    this.onMonthChanged,
    this.initialView = 'month',
    this.initialCategory,
    this.initialYear,
    this.onLocationChanged,
  });
  final String? initialMonth;
  final ValueChanged<String>? onMonthChanged;
  final ApiClient api;
  final String initialView;
  final String? initialCategory;
  final int? initialYear;
  final ValueChanged<Map<String, String>>? onLocationChanged;

  @override
  State<BankingScreen> createState() => _BankingScreenState();
}

class _BankingScreenState extends State<BankingScreen> {
  DateTime _month = DateTime(DateTime.now().year, DateTime.now().month);
  Map<String, dynamic>? _status;
  Map<String, dynamic>? _report;
  List<Map<String, dynamic>> _rules = [];
  Map<String, dynamic> _job = {};
  Timer? _pollTimer;
  String? _error;
  String _view = 'month';
  String? _category;
  late int _planYear;
  Map<String, dynamic> _history = {};
  Map<String, dynamic> _budgets = {};
  String? _insightError;
  bool _busy = true;
  bool _polling = false;
  bool _planDirty = false;
  int _loadVersion = 0;
  int _insightsVersion = 0;
  String get _monthKey =>
      '${_month.year}-${_month.month.toString().padLeft(2, '0')}';
  String money(dynamic value) => bankMoney(num.parse('$value'));
  List<Map<String, dynamic>> rows(dynamic value) => (value as List? ?? [])
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();

  @override
  void initState() {
    super.initState();
    _readLocation();
    final initial = widget.initialMonth;
    if (initial != null &&
        RegExp(r'^\d{4}-(0[1-9]|1[0-2])$').hasMatch(initial)) {
      _month = DateTime.parse('$initial-01');
    }
    _load();
  }

  void _readLocation() {
    _view = ['month', 'detail', 'plan'].contains(widget.initialView)
        ? widget.initialView
        : 'month';
    _category = bankCategories.containsKey(widget.initialCategory)
        ? widget.initialCategory
        : null;
    final year = widget.initialYear ?? DateTime.now().year + 1;
    _planYear = year >= 1900 && year <= 2200 ? year : DateTime.now().year + 1;
  }

  void _navigate({String? view, String? category, int? year}) {
    setState(() {
      if (view != null) _view = view;
      if (category != null) _category = category;
      if (year != null) _planYear = year;
    });
    widget.onLocationChanged?.call({
      'month': _monthKey,
      'view': _view,
      if (_category != null) 'category': _category!,
      'year': '$_planYear',
    });
    if (view == 'month' || view == 'detail') _loadInsights();
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant BankingScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_planDirty &&
        (widget.initialView != 'plan' ||
            widget.initialYear != _planYear ||
            widget.initialMonth != _monthKey)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          widget.onLocationChanged?.call({
            'month': _monthKey,
            'view': 'plan',
            if (_category != null) 'category': _category!,
            'year': '$_planYear',
          });
        }
      });
      return;
    }
    _readLocation();
    if (widget.initialMonth != oldWidget.initialMonth &&
        widget.initialMonth != null &&
        RegExp(r'^\d{4}-(0[1-9]|1[0-2])$').hasMatch(widget.initialMonth!)) {
      _month = DateTime.parse('${widget.initialMonth}-01');
      _report = null;
      _load();
    }
  }

  void _changeMonth(int delta) {
    final next = DateTime(_month.year, _month.month + delta);
    final key = '${next.year}-${next.month.toString().padLeft(2, '0')}';
    if (widget.onLocationChanged != null) {
      widget.onLocationChanged!({
        'month': key,
        'view': _view,
        if (_category != null) 'category': _category!,
        'year': '$_planYear',
      });
    } else if (widget.onMonthChanged != null) {
      widget.onMonthChanged!(key);
    } else {
      setState(() {
        _month = next;
        _report = null;
      });
      _load();
    }
  }

  void _schedulePoll() {
    _pollTimer?.cancel();
    if (_job['status'] == 'RUNNING') {
      _pollTimer = Timer.periodic(const Duration(seconds: 4), (_) => _poll());
    }
  }

  Future<void> _poll() async {
    if (_polling || _busy || _status?['configured'] != true) return;
    _polling = true;
    final month = _monthKey;
    try {
      final job = await widget.api.getJson('/api/v1/banking/categorization');
      if (job['updatedAt'] == _job['updatedAt'] &&
          job['status'] == _job['status']) {
        return;
      }
      final report = await widget.api.getJson(
        '/api/v1/banking/month?month=$_monthKey',
      );
      if (mounted && month == _monthKey) {
        setState(() {
          _job = job;
          _report = report;
        });
        _schedulePoll();
        _loadInsights();
      }
    } catch (_) {
      /* Bij een tijdelijke fout blijft het bestaande overzicht zichtbaar. */
    } finally {
      _polling = false;
    }
  }

  Future<void> _load() async {
    if (_planDirty) return;
    final version = ++_loadVersion;
    setState(() {
      _busy = true;
      _error = null;
      _history = {};
      _budgets = {};
    });
    try {
      final status = await widget.api.getJson('/api/v1/banking');
      Map<String, dynamic>? report;
      List<Map<String, dynamic>> rules = [];
      Map<String, dynamic> job = {};
      if (status['configured'] == true) {
        job = await widget.api.getJson('/api/v1/banking/categorization');
        report = await widget.api.getJson(
          '/api/v1/banking/month?month=$_monthKey',
        );
        rules = rows(
          (await widget.api.getJson('/api/v1/banking/rules'))['rules'],
        );
      }
      if (!mounted || version != _loadVersion) return;
      setState(() {
        _status = status;
        _report = report;
        _rules = rules;
        _job = job;
      });
      _schedulePoll();
      if (status['configured'] == true) _loadInsights();
    } catch (_) {
      if (mounted && version == _loadVersion) {
        setState(
          () => _error = 'Bankzaken laden is niet gelukt. Probeer opnieuw.',
        );
      }
    } finally {
      if (mounted && version == _loadVersion) setState(() => _busy = false);
    }
  }

  Future<void> _loadInsights() async {
    final month = _monthKey;
    final version = _loadVersion;
    final insightVersion = ++_insightsVersion;
    try {
      final result = await Future.wait([
        widget.api.getJson('/api/v1/banking/history?end=$month'),
        widget.api.getJson('/api/v1/banking/planning?year=${_month.year}'),
      ]);
      if (!mounted ||
          month != _monthKey ||
          version != _loadVersion ||
          insightVersion != _insightsVersion) {
        return;
      }
      setState(() {
        _history = result[0];
        _budgets = Map<String, dynamic>.from(
          result[1]['budgets'] as Map? ?? {},
        );
        _insightError = null;
      });
    } catch (_) {
      if (mounted &&
          month == _monthKey &&
          version == _loadVersion &&
          insightVersion == _insightsVersion) {
        setState(() {
          _history = {};
          _budgets = {};
          _insightError = 'Historie en budgetten konden niet worden geladen.';
        });
      }
    }
  }

  Future<void> _action(Future<void> Function() action) async {
    if (_planDirty) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
      if (mounted) await _load();
    } catch (_) {
      if (mounted) {
        setState(() {
          _error =
              'Dit is niet gelukt. Probeer opnieuw; vernieuw zo nodig je banktoestemming.';
          _busy = false;
        });
      }
    }
  }

  Future<void> _connect([String? iban]) => _action(() async {
    final response = await widget.api.postJson('/api/v1/banking/connect', {
      if (iban != null) 'iban': iban,
    });
    final uri = Uri.parse(response['url'] as String);
    if (uri.scheme != 'https' ||
        !(uri.host == 'enablebanking.com' ||
            uri.host.endsWith('.enablebanking.com'))) {
      throw StateError('Ongeldige banklink');
    }
    if (!await launchUrl(
      uri,
      mode: LaunchMode.externalApplication,
      webOnlyWindowName: '_self',
    )) {
      throw StateError('Banklink kon niet worden geopend');
    }
  });

  Future<void> _classify(Map<String, dynamic> transaction) async {
    var remember = false;
    var matchText = (transaction['counterparty'] as String? ?? '').trim();
    String? validation;
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => SimpleDialog(
          title: Text(transaction['counterparty'] as String? ?? 'Categorie'),
          children: [
            if ('${transaction['question'] ?? ''}'.isNotEmpty)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text('${transaction['question']}'),
              ),
            CheckboxListTile(
              title: const Text('Onthouden voor soortgelijke betalingen'),
              value: remember,
              onChanged: (v) => update(() => remember = v ?? false),
            ),
            if (remember)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Column(
                  children: [
                    TextFormField(
                      initialValue: matchText,
                      decoration: const InputDecoration(
                        labelText: 'Naam of omschrijving bevat',
                      ),
                      onChanged: (v) => update(() => matchText = v),
                    ),
                    const Text(
                      'Gebruik bij een gemengde winkel een specifiek aankoopdoel, anders worden ook andere aankopen zo ingedeeld. Als een tegenrekening bekend is, geldt de regel alleen op die rekening.',
                    ),
                    Text(
                      '${rows(_report?['transactions']).where((t) => t['counterpartyIban'] == transaction['counterpartyIban'] && '${t['counterparty']} ${t['description']}'.toLowerCase().contains(matchText.toLowerCase())).length} betalingen in deze maand passen bij deze regel.',
                    ),
                    if (validation != null) Text(validation!),
                  ],
                ),
              ),
            ...bankCategories.entries.map(
              (e) => SimpleDialogOption(
                onPressed: () {
                  if (remember && matchText.trim().length < 3) {
                    update(() => validation = 'Gebruik minimaal drie tekens.');
                    return;
                  }
                  Navigator.pop(context, <String, dynamic>{
                    'category': e.key,
                    'remember': remember,
                    'matchText': matchText.trim(),
                  });
                },
                child: Text(e.value),
              ),
            ),
          ],
        ),
      ),
    );
    if (result == null || !mounted) return;
    final transactionMonth = '${transaction['date']}'.substring(0, 7);
    await _action(() async {
      await widget.api.putJson(
        '/api/v1/banking/month/$transactionMonth/transactions/${transaction['id']}/category',
        result,
      );
    });
  }

  Future<void> _rule(Map<String, dynamic> transaction) async {
    var contains = '';
    String category = transaction['category'] as String? ?? 'REVIEW';
    final iban = transaction['counterpartyIban'] as String? ?? '';
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Rubriceringsregel'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  iban.isEmpty
                      ? 'Regel voor naam of omschrijving.'
                      : 'Regel voor rekening $iban, eventueel met een deel van de naam of omschrijving.',
                ),
                TextField(
                  onChanged: (value) => contains = value,
                  decoration: const InputDecoration(
                    labelText: 'Naam of omschrijving bevat',
                  ),
                  maxLength: 200,
                ),
                DropdownButton<String>(
                  isExpanded: true,
                  value: category,
                  items: bankCategories.entries
                      .map(
                        (e) => DropdownMenuItem(
                          value: e.key,
                          child: Text(e.value),
                        ),
                      )
                      .toList(),
                  onChanged: (v) {
                    if (v != null) update(() => category = v);
                  },
                ),
                const Text(
                  'Specifieke regels staan bovenaan. Eerder handmatig ingedeelde betalingen behouden hun categorie.',
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Annuleren'),
            ),
            FilledButton(
              onPressed: () {
                if (contains.trim().length < 3 && iban.isEmpty) return;
                Navigator.pop(context, <String, dynamic>{
                  'id': DateTime.now().microsecondsSinceEpoch.toString(),
                  'contains': contains.trim(),
                  'iban': iban,
                  'category': category,
                });
              },
              child: const Text('Opslaan'),
            ),
          ],
        ),
      ),
    );
    if (result == null || !mounted) return;
    await _action(() async {
      await widget.api.putJson('/api/v1/banking/rules', {
        'rules': [result, ..._rules],
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final configured = _status?['configured'] == true;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Bankzaken'),
        actions: [
          IconButton(
            tooltip: 'Overzicht verversen',
            onPressed: _busy || _planDirty ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: _busy
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 1200),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (_error != null) ...[
                            Text(
                              _error!,
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.error,
                              ),
                            ),
                            TextButton(
                              onPressed: _load,
                              child: const Text('Opnieuw proberen'),
                            ),
                          ],
                          if (_status != null && !configured)
                            const Card(
                              child: Padding(
                                padding: EdgeInsets.all(24),
                                child: Text(
                                  'De bankkoppeling wordt voorbereid. Zodra deze is ingesteld, kun je hier je banktoestemming geven en uitgaven bekijken.',
                                ),
                              ),
                            ),
                          if (configured) ...[
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                for (final entry in {
                                  'month': 'Maandoverzicht',
                                  'detail': 'Categorie uitpluizen',
                                  'plan': 'Jaarplanning & budget',
                                }.entries)
                                  ChoiceChip(
                                    label: Text(entry.value),
                                    selected: _view == entry.key,
                                    onSelected: _planDirty
                                        ? null
                                        : (_) => _navigate(view: entry.key),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 20),
                            if (_view != 'plan') ...[
                              Row(
                                children: [
                                  IconButton(
                                    tooltip: 'Vorige maand',
                                    onPressed: () => _changeMonth(-1),
                                    icon: const Icon(Icons.chevron_left),
                                  ),
                                  Expanded(
                                    child: Center(
                                      child: Text(
                                        _monthKey,
                                        style: Theme.of(
                                          context,
                                        ).textTheme.titleLarge,
                                      ),
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: 'Volgende maand',
                                    onPressed:
                                        _month.year == DateTime.now().year &&
                                            _month.month == DateTime.now().month
                                        ? null
                                        : () => _changeMonth(1),
                                    icon: const Icon(Icons.chevron_right),
                                  ),
                                ],
                              ),
                              if (_report != null)
                                BankDashboard(
                                  report: _report!,
                                  history: _history,
                                  budgets: _budgets,
                                  categories: bankCategories,
                                  accounts: rows(_status?['accounts']),
                                  detail: _view == 'detail',
                                  selectedCategory: _category,
                                  onCategory: (c) =>
                                      _navigate(view: 'detail', category: c),
                                  onClassify: _classify,
                                  onRule: _rule,
                                ),
                              if (_insightError != null)
                                TextButton(
                                  onPressed: _loadInsights,
                                  child: Text(
                                    '$_insightError Opnieuw proberen',
                                  ),
                                ),
                            ] else
                              BankPlanningPanel(
                                api: widget.api,
                                initialYear: _planYear,
                                categories: bankCategories,
                                onDirtyChanged: (dirty) {
                                  if (mounted) {
                                    setState(() => _planDirty = dirty);
                                  }
                                },
                                onYearChanged: (y) => _navigate(year: y),
                              ),
                            const SizedBox(height: 20),
                            Card(
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text(
                                      'Rubricering: vaste regels → AI → jouw antwoord bij twijfel',
                                    ),
                                    if (_job['status'] == 'RUNNING') ...[
                                      LinearProgressIndicator(
                                        value: (_job['total'] as num? ?? 0) > 0
                                            ? ((_job['completed'] as num? ??
                                                          0) /
                                                      (_job['total'] as num))
                                                  .clamp(0.0, 1.0)
                                            : null,
                                      ),
                                      Text(
                                        '${_job['completed'] ?? 0} van ${_job['total'] ?? 0} overige betalingen beoordeeld',
                                      ),
                                    ],
                                    if (_job['message'] != null)
                                      Text('${_job['message']}'),
                                    FilledButton.tonal(
                                      onPressed: _job['status'] == 'RUNNING'
                                          ? null
                                          : () => _action(() async {
                                              await widget.api.postJson(
                                                '/api/v1/banking/categorize',
                                              );
                                            }),
                                      child: const Text(
                                        'Alle transacties rubriceren',
                                      ),
                                    ),
                                    const Text(
                                      'Tik bij een vraag op de betaling om te antwoorden. Je kunt aangeven of je antwoord als regel onthouden mag worden.',
                                    ),
                                  ],
                                ),
                              ),
                            ),

                            ExpansionTile(
                              title: Text(
                                'Rekeningen en gegevens (${rows(_status?['accounts']).length})',
                              ),
                              children: [
                                const Text(
                                  'Alleen lezen · betalingen uitvoeren is niet mogelijk',
                                ),
                                ...rows(_status!['accounts']).map(
                                  (a) => ListTile(
                                    contentPadding: EdgeInsets.zero,
                                    leading: const Icon(
                                      Icons.account_balance_outlined,
                                    ),
                                    title: Text('${a['name']}'),
                                    subtitle: Text(
                                      '${a['iban']}\nToestemming tot ${a['validUntil']}',
                                    ),
                                  ),
                                ),
                                Wrap(
                                  spacing: 8,
                                  children: [
                                    OutlinedButton.icon(
                                      onPressed: () => _connect(),
                                      icon: const Icon(Icons.link),
                                      label: const Text(
                                        'Rekening koppelen / vernieuwen',
                                      ),
                                    ),
                                    FilledButton.icon(
                                      onPressed: () => _action(() async {
                                        await widget.api.postJson(
                                          '/api/v1/banking/sync',
                                        );
                                      }),
                                      icon: const Icon(Icons.sync),
                                      label: const Text(
                                        'Bankgegevens bijwerken',
                                      ),
                                    ),
                                  ],
                                ),
                                if (_status!['lastSync'] != null)
                                  Text(
                                    'Laatst opgehaald: ${_status!['lastSync']}',
                                  ),
                                ...rows(_status!['availableAccounts'])
                                    .where((a) => a['connected'] != true)
                                    .map(
                                      (a) => ListTile(
                                        contentPadding: EdgeInsets.zero,
                                        title: Text(
                                          'Nog niet zichtbaar: ${a['iban']}',
                                        ),
                                        subtitle: const Text(
                                          'Deze rekening is nog niet door de bank teruggegeven aan de assistent.',
                                        ),
                                        trailing: OutlinedButton(
                                          onPressed: () =>
                                              _connect('${a['iban']}'),
                                          child: const Text(
                                            'Deze rekening koppelen',
                                          ),
                                        ),
                                      ),
                                    ),

                                if (_report != null)
                                  ...((_report!['warnings'] as List?) ?? [])
                                      .map(
                                        (w) => Padding(
                                          padding: const EdgeInsets.symmetric(
                                            vertical: 4,
                                          ),
                                          child: Text('$w'),
                                        ),
                                      ),
                              ],
                            ),
                            ExpansionTile(
                              title: Text(
                                'Rubriceringsregels (${_rules.length})',
                              ),
                              children: _rules
                                  .map(
                                    (r) => ListTile(
                                      title: Text(
                                        '${r['label'] ?? ''} ${r['contains']} ${r['iban']}',
                                      ),
                                      subtitle: Text(
                                        bankCategories[r['category']] ?? '',
                                      ),
                                      trailing: IconButton(
                                        tooltip: 'Regel verwijderen',
                                        icon: const Icon(Icons.delete_outline),
                                        onPressed: () => _action(() async {
                                          await widget.api.putJson(
                                            '/api/v1/banking/rules',
                                            {
                                              'rules': _rules
                                                  .where(
                                                    (other) =>
                                                        other['id'] != r['id'],
                                                  )
                                                  .toList(),
                                            },
                                          );
                                        }),
                                      ),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
