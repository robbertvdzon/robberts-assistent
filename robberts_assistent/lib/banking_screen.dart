import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'api_client.dart';

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
  });
  final String? initialMonth;
  final ValueChanged<String>? onMonthChanged;
  final ApiClient api;

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
  String? _filter;
  bool _busy = true;
  bool _polling = false;
  int _loadVersion = 0;
  String get _monthKey =>
      '${_month.year}-${_month.month.toString().padLeft(2, '0')}';
  String money(dynamic value) =>
      '€ ${num.parse('$value').toStringAsFixed(2).replaceAll('.', ',')}';
  List<Map<String, dynamic>> rows(dynamic value) => (value as List? ?? [])
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();

  @override
  void initState() {
    super.initState();
    final initial = widget.initialMonth;
    if (initial != null &&
        RegExp(r'^\d{4}-(0[1-9]|1[0-2])$').hasMatch(initial)) {
      _month = DateTime.parse('$initial-01');
    }
    _load();
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant BankingScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
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
    if (widget.onMonthChanged != null) {
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
      }
    } catch (_) {
      /* Bij een tijdelijke fout blijft het bestaande overzicht zichtbaar. */
    } finally {
      _polling = false;
    }
  }

  Future<void> _load() async {
    final version = ++_loadVersion;
    setState(() {
      _busy = true;
      _error = null;
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
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Bankzaken laden is niet gelukt. Probeer opnieuw.',
        );
      }
    } finally {
      if (mounted && version == _loadVersion) setState(() => _busy = false);
    }
  }

  Future<void> _action(Future<void> Function() action) async {
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

  String _transactionDetails(Map<String, dynamic> t) {
    const sourceLabels = {
      'RULE': 'Vaste regel',
      'LEARNED': 'Geleerd van jou',
      'AI': 'AI-indeling',
      'USER': 'Jouw keuze',
      'QUESTION': 'Vraag',
    };
    return [
      '${t['date']} · ${bankCategories[t['category']]}',
      '${t['description']}',
      if ('${t['question'] ?? ''}'.isNotEmpty) 'Vraag: ${t['question']}',
      if ('${t['reason'] ?? ''}'.isNotEmpty)
        '${sourceLabels[t['classificationSource']] ?? 'Indeling'}: ${t['reason']}',
    ].join('\n');
  }

  Widget metric(String label, dynamic value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Expanded(child: Text(label)),
        Text(money(value), style: const TextStyle(fontWeight: FontWeight.bold)),
      ],
    ),
  );

  @override
  Widget build(BuildContext context) {
    final report = _report;
    final transactions = rows(report?['transactions']);
    final filtered = transactions
        .where((t) => _filter == null || t['category'] == _filter)
        .toList();
    final configured = _status?['configured'] == true;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Bankzaken'),
        actions: [
          IconButton(
            tooltip: 'Overzicht verversen',
            onPressed: _busy ? null : _load,
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
                        padding: EdgeInsets.all(16),
                        child: Text(
                          'De bankkoppeling wordt voorbereid. Zodra deze is ingesteld, kun je hier je banktoestemming geven en uitgaven bekijken.',
                        ),
                      ),
                    ),
                  if (configured) ...[
                    const Text(
                      'Alleen lezen · betalingen uitvoeren is niet mogelijk',
                    ),
                    ...rows(_status!['accounts']).map(
                      (a) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.account_balance_outlined),
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
                          label: const Text('Rekening koppelen / vernieuwen'),
                        ),
                        FilledButton.icon(
                          onPressed: () => _action(() async {
                            await widget.api.postJson('/api/v1/banking/sync');
                          }),
                          icon: const Icon(Icons.sync),
                          label: const Text('Bankgegevens bijwerken'),
                        ),
                      ],
                    ),
                    if (_status!['lastSync'] != null)
                      Text('Laatst opgehaald: ${_status!['lastSync']}'),
                    ...rows(_status!['availableAccounts'])
                        .where((a) => a['connected'] != true)
                        .map(
                          (a) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text('Nog niet zichtbaar: ${a['iban']}'),
                            subtitle: const Text(
                              'Deze rekening is nog niet door de bank teruggegeven aan de assistent.',
                            ),
                            trailing: OutlinedButton(
                              onPressed: () => _connect('${a['iban']}'),
                              child: const Text('Deze rekening koppelen'),
                            ),
                          ),
                        ),
                    const SizedBox(height: 12),
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
                                    ? ((_job['completed'] as num? ?? 0) /
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
                              child: const Text('Alle transacties rubriceren'),
                            ),
                            const Text(
                              'Tik bij een vraag op de betaling om te antwoorden. Je kunt aangeven of je antwoord als regel onthouden mag worden.',
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
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
                              style: Theme.of(context).textTheme.titleLarge,
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
                    if (report != null) ...[
                      ...((report['warnings'] as List?) ?? []).map(
                        (w) => Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Text('$w'),
                        ),
                      ),
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            children: [
                              metric(
                                'Uitgaven (excl. interne overboekingen)',
                                report['spent'],
                              ),
                              metric(
                                'Ontvangen (incl. terugbetalingen)',
                                report['received'],
                              ),
                              metric(
                                'Overig / extra, na terugbetalingen',
                                report['extra'],
                              ),
                              metric(
                                'Nog te beoordelen uitgaven',
                                report['review'],
                              ),
                            ],
                          ),
                        ),
                      ),
                      const Text(
                        'Overig / extra is voor niet-noodzakelijke aankopen en activiteiten. Deel onbekende betalingen eerst in; die tellen nog niet als extra.',
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 6,
                        children: [
                          ChoiceChip(
                            label: const Text('Alles'),
                            selected: _filter == null,
                            onSelected: (_) => setState(() => _filter = null),
                          ),
                          ...['EXTRA', 'REVIEW'].map(
                            (c) => ChoiceChip(
                              label: Text(bankCategories[c]!),
                              selected: _filter == c,
                              onSelected: (_) => setState(() => _filter = c),
                            ),
                          ),
                        ],
                      ),
                      ExpansionTile(
                        title: const Text('Bedragen per categorie'),
                        children: rows(report['categories'])
                            .map(
                              (c) => ListTile(
                                title: Text('${c['label']}'),
                                subtitle: Text(
                                  'Ontvangen: ${money(c['received'])}',
                                ),
                                trailing: Text(money(c['spent'])),
                              ),
                            )
                            .toList(),
                      ),
                      if (filtered.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(16),
                          child: Text('Geen betalingen in deze selectie.'),
                        ),
                      ...filtered.map(
                        (t) => Card(
                          child: ListTile(
                            title: Text(
                              '${t['counterparty'] == '' ? 'Betaling' : t['counterparty']} · ${money(t['amount'])}',
                            ),
                            subtitle: Text(_transactionDetails(t)),
                            isThreeLine: true,
                            onTap: () => _classify(t),
                            trailing: PopupMenuButton<String>(
                              onSelected: (v) {
                                if (v == 'category') {
                                  _classify(t);
                                } else {
                                  _rule(t);
                                }
                              },
                              itemBuilder: (_) => const [
                                PopupMenuItem(
                                  value: 'category',
                                  child: Text('Categorie aanpassen'),
                                ),
                                PopupMenuItem(
                                  value: 'rule',
                                  child: Text(
                                    'Regel voor soortgelijke betalingen',
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                    ExpansionTile(
                      title: Text('Rubriceringsregels (${_rules.length})'),
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
                                  await widget.api
                                      .putJson('/api/v1/banking/rules', {
                                        'rules': _rules
                                            .where(
                                              (other) => other['id'] != r['id'],
                                            )
                                            .toList(),
                                      });
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
    );
  }
}
