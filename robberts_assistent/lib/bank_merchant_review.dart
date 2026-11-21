import 'package:flutter/material.dart';

import 'api_client.dart';
import 'bank_insights.dart';

/// Review spans all stored months; it never uses the dashboard month filter.
class BankMerchantReview extends StatefulWidget {
  const BankMerchantReview({
    super.key,
    required this.api,
    required this.categories,
    required this.onSaved,
    this.enabled = true,
  });

  final ApiClient api;
  final Map<String, String> categories;
  final Future<void> Function() onSaved;
  final bool enabled;

  @override
  State<BankMerchantReview> createState() => _BankMerchantReviewState();
}

class _BankMerchantReviewState extends State<BankMerchantReview> {
  static const _path = '/api/v1/banking/review-merchants';
  static const _pageSize = 20;
  Map<String, dynamic>? _report;
  final _choices = <String, String>{};
  String _query = '';
  String _sort = 'spent';
  String? _saving;
  String? _error;
  String? _notice;
  bool _loading = false;
  int _page = 0;
  int _version = 0;

  List<Map<String, dynamic>> _rows(dynamic value) => (value as List? ?? [])
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();

  Iterable<MapEntry<String, String>> get _categories => widget
      .categories
      .entries
      .where((e) => !['REVIEW', 'INTERNAL', 'INCOME'].contains(e.key));

  Future<void> _load() async {
    final version = ++_version;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final report = await widget.api.getJson(_path);
      if (!mounted || version != _version) return;
      setState(() => _report = report);
    } catch (_) {
      if (mounted && version == _version) {
        setState(
          () => _error =
              'De winkellijst kon niet worden opgehaald. Probeer opnieuw.',
        );
      }
    } finally {
      if (mounted && version == _version) setState(() => _loading = false);
    }
  }

  Future<void> _save(
    Map<String, dynamic> group,
    String category, {
    Map<String, dynamic>? transaction,
  }) async {
    final key = '${group['key']}';
    setState(() {
      _saving = key;
      _error = null;
      _notice = null;
    });
    var saved = false;
    try {
      final result = transaction == null
          ? await widget.api.putJson('$_path/${Uri.encodeComponent(key)}', {
              'category': category,
            })
          : await widget.api.putJson(
              '/api/v1/banking/month/${'${transaction['date']}'.substring(0, 7)}/transactions/${Uri.encodeComponent('${transaction['id']}')}/category',
              {'category': category, 'remember': false},
            );
      saved = true;
      if (!mounted) return;
      setState(() {
        _choices.remove(key);
        final count = transaction == null
            ? result['count'] ?? group['count']
            : 1;
        _notice =
            '$count betaling(en) ingedeeld als ${widget.categories[category] ?? category}.';
      });
      await _load();
      await widget.onSaved();
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = saved
              ? 'De indeling is opgeslagen, maar het overzicht kon niet worden vernieuwd. Vernieuw de lijst.'
              : 'Opslaan is niet gelukt. Je categoriekeuze blijft staan; probeer opnieuw.',
        );
      }
    } finally {
      if (mounted) setState(() => _saving = null);
    }
  }

  Future<void> _classifyOne(
    Map<String, dynamic> group,
    Map<String, dynamic> transaction,
  ) async {
    final category = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Deze betaling indelen'),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
            child: Text(
              '${transaction['date']} · ${bankMoneyCents(bankCents(transaction['amount']))}\n${transaction['description'] ?? ''}\n\nAlleen deze betaling verandert. Er wordt geen regel voor deze tegenpartij gemaakt.',
            ),
          ),
          for (final category in _categories)
            SimpleDialogOption(
              onPressed: () => Navigator.pop(context, category.key),
              child: Text(category.value),
            ),
        ],
      ),
    );
    if (category != null && mounted) {
      await _save(group, category, transaction: transaction);
    }
  }

  @override
  Widget build(BuildContext context) {
    final groups = _rows(_report?['groups']);
    final filtered =
        groups
            .where(
              (g) => '${g['merchant']}'.toLowerCase().contains(
                _query.toLowerCase(),
              ),
            )
            .toList()
          ..sort((a, b) {
            final order = _sort == 'name'
                ? '${a['merchant']}'.toLowerCase().compareTo(
                    '${b['merchant']}'.toLowerCase(),
                  )
                : _sort == 'count'
                ? (b['count'] as num).compareTo(a['count'] as num)
                : bankCents(b['spent']).compareTo(bankCents(a['spent']));
            return order != 0 ? order : '${a['key']}'.compareTo('${b['key']}');
          });
    final pageCount = (filtered.length / _pageSize).ceil();
    final page = pageCount == 0 ? 0 : _page.clamp(0, pageCount - 1);
    final visible = filtered.skip(page * _pageSize).take(_pageSize).toList();
    final interactive = widget.enabled && !_loading && _saving == null;
    return Card(
      child: ExpansionTile(
        key: const ValueKey('bank-merchant-review'),
        maintainState: true,
        leading: const Icon(Icons.storefront_outlined),
        title: const Text('Winkels indelen'),
        subtitle: Text(
          _report == null
              ? 'Kies per winkel een categorie · alle beschikbare maanden'
              : '${groups.length} winkels / tegenpartijen · ${_report!['transactionCount'] ?? 0} nog te beoordelen betalingen',
        ),
        onExpansionChanged: (expanded) {
          if (expanded && _report == null && !_loading) _load();
        },
        childrenPadding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Alle nog te beoordelen betalingen uit de opgeslagen historie, ongeacht de maand bovenaan. Kies per herkenbare winkel één categorie.',
          ),
          if (_report?['from'] != null)
            Text(
              'Beschikbare historie: ${_report!['from']} t/m ${_report!['to']}',
            ),
          const SizedBox(height: 8),
          const Text(
            'Onthoud je een winkel, dan geldt de categorie ook voor bestaande en volgende betalingen bij diezelfde winkel, inclusief retouren. Je handmatige keuzes blijven behouden.',
          ),
          if (_notice != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                _notice!,
                style: TextStyle(color: Theme.of(context).colorScheme.primary),
              ),
            ),
          if (_error != null)
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: interactive ? _load : null,
              icon: const Icon(Icons.refresh),
              label: const Text('Winkellijst vernieuwen'),
            ),
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_report != null) ...[
            TextField(
              // Scroll offsets must not share an ancestor ExpansionTile's
              // boolean PageStorage entry.
              key: const PageStorageKey('bank-merchant-search-scroll'),
              decoration: const InputDecoration(
                labelText: 'Zoek winkel of tegenpartij',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (value) => setState(() {
                _query = value.trim();
                _page = 0;
              }),
            ),
            DropdownButtonFormField<String>(
              initialValue: _sort,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Sorteren'),
              items: const [
                DropdownMenuItem(
                  value: 'spent',
                  child: Text('Hoogste uitgaven eerst'),
                ),
                DropdownMenuItem(
                  value: 'count',
                  child: Text('Meeste betalingen eerst'),
                ),
                DropdownMenuItem(value: 'name', child: Text('Naam A–Z')),
              ],
              onChanged: (value) => setState(() {
                _sort = value ?? 'spent';
                _page = 0;
              }),
            ),
            const SizedBox(height: 12),
            if (groups.isEmpty)
              const Text('Er zijn geen betalingen meer te beoordelen.')
            else if (filtered.isEmpty)
              const Text('Geen winkels gevonden met deze naam.')
            else ...[
              Text(
                '${page * _pageSize + 1}–${page * _pageSize + visible.length} van ${filtered.length} winkels / tegenpartijen',
              ),
              for (final group in visible) _group(group, interactive),
              if (pageCount > 1)
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      tooltip: 'Vorige winkels',
                      onPressed: page > 0
                          ? () => setState(() => _page = page - 1)
                          : null,
                      icon: const Icon(Icons.chevron_left),
                    ),
                    Expanded(
                      child: Text(
                        'Pagina ${page + 1} van $pageCount',
                        textAlign: TextAlign.center,
                      ),
                    ),
                    IconButton(
                      tooltip: 'Volgende winkels',
                      onPressed: page + 1 < pageCount
                          ? () => setState(() => _page = page + 1)
                          : null,
                      icon: const Icon(Icons.chevron_right),
                    ),
                  ],
                ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _group(Map<String, dynamic> group, bool interactive) {
    final key = '${group['key']}';
    final canLearn = group['canLearn'] == true;
    final examples = _rows(group['examples']);
    return Card(
      key: ValueKey('merchant-$key'),
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              '${group['merchant']}',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Text(
              '${group['count']} betalingen · uitgegeven ${bankMoneyCents(bankCents(group['spent']))}',
            ),
            Text('${group['from']} t/m ${group['to']}'),
            if (canLearn) ...[
              DropdownButtonFormField<String>(
                key: ValueKey('category-$key'),
                initialValue: _choices[key],
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Kies categorie'),
                items: _categories
                    .map(
                      (e) =>
                          DropdownMenuItem(value: e.key, child: Text(e.value)),
                    )
                    .toList(),
                onChanged: interactive
                    ? (value) => setState(() {
                        if (value != null) _choices[key] = value;
                      })
                    : null,
              ),
              const SizedBox(height: 8),
              FilledButton.tonal(
                onPressed: interactive && _choices.containsKey(key)
                    ? () => _save(group, _choices[key]!)
                    : null,
                child: Text(
                  _saving == key
                      ? 'Bezig met opslaan…'
                      : 'Indelen en onthouden',
                ),
              ),
            ] else
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  'Deze tegenpartij kan verschillende doelen hebben. Kies hieronder per betaling een categorie; er wordt geen algemene regel gemaakt.',
                ),
              ),
            ExpansionTile(
              key: PageStorageKey('examples-$key'),
              initiallyExpanded: !canLearn,
              tilePadding: EdgeInsets.zero,
              title: Text(
                canLearn
                    ? 'Bekijk ${examples.length} voorbeeldbetaling(en)'
                    : 'Betaling bekijken en indelen',
              ),
              children: [
                for (final transaction in examples)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      '${transaction['date']} · ${bankMoneyCents(bankCents(transaction['amount']))}',
                    ),
                    subtitle: Text('${transaction['description'] ?? ''}'),
                    trailing: canLearn
                        ? null
                        : IconButton(
                            tooltip: 'Deze betaling indelen',
                            onPressed: interactive
                                ? () => _classifyOne(group, transaction)
                                : null,
                            icon: const Icon(Icons.edit_outlined),
                          ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
