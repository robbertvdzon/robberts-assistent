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
  'INTERNAL': 'Interne overboeking',
  'INCOME': 'Inkomsten',
};

class BankingScreen extends StatefulWidget {
  const BankingScreen({super.key, required this.api});
  final ApiClient api;

  @override
  State<BankingScreen> createState() => _BankingScreenState();
}

class _BankingScreenState extends State<BankingScreen> {
  DateTime _month = DateTime(DateTime.now().year, DateTime.now().month);
  Map<String, dynamic>? _status;
  Map<String, dynamic>? _report;
  List<Map<String, dynamic>> _rules = [];
  String? _error;
  String? _filter;
  bool _busy = true;
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
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final status = await widget.api.getJson('/api/v1/banking');
      Map<String, dynamic>? report;
      List<Map<String, dynamic>> rules = [];
      if (status['configured'] == true) {
        report = await widget.api.getJson(
          '/api/v1/banking/month?month=$_monthKey',
        );
        rules = rows(
          (await widget.api.getJson('/api/v1/banking/rules'))['rules'],
        );
      }
      if (!mounted) return;
      setState(() {
        _status = status;
        _report = report;
        _rules = rules;
      });
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Bankzaken laden is niet gelukt. Probeer opnieuw.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
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

  Future<void> _connect() => _action(() async {
    final response = await widget.api.postJson('/api/v1/banking/connect');
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
    final result = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(transaction['counterparty'] as String? ?? 'Categorie'),
        children: bankCategories.entries
            .map(
              (e) => SimpleDialogOption(
                onPressed: () => Navigator.pop(context, e.key),
                child: Text(e.value),
              ),
            )
            .toList(),
      ),
    );
    if (result == null || !mounted) return;
    await _action(() async {
      await widget.api.putJson(
        '/api/v1/banking/month/$_monthKey/transactions/${transaction['id']}/category',
        {'category': result},
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
                          onPressed: _connect,
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
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        IconButton(
                          tooltip: 'Vorige maand',
                          onPressed: () {
                            setState(() {
                              _month = DateTime(_month.year, _month.month - 1);
                              _report = null;
                            });
                            _load();
                          },
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
                              : () {
                                  setState(() {
                                    _month = DateTime(
                                      _month.year,
                                      _month.month + 1,
                                    );
                                    _report = null;
                                  });
                                  _load();
                                },
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
                            subtitle: Text(
                              '${t['date']} · ${bankCategories[t['category']]}\n${t['description']}',
                            ),
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
                              title: Text('${r['contains']} ${r['iban']}'),
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
