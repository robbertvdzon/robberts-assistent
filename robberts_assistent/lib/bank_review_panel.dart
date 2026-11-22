import 'package:flutter/material.dart';

import 'api_client.dart';
import 'bank_insights.dart';

/// Eén openstaande betaling zoals `GET /api/v1/banking/review` hem levert.
class _ReviewRow {
  _ReviewRow(Map<String, dynamic> json)
    : transaction = Map<String, dynamic>.from(json['transaction'] as Map),
      merchant = '${json['merchant'] ?? ''}',
      merchantKey = '${json['merchantKey'] ?? ''}',
      canLearn = json['canLearn'] == true;

  final Map<String, dynamic> transaction;
  final String merchant;
  final String merchantKey;
  final bool canLearn;

  String get id => '${transaction['id']}';
  String get date => '${transaction['date']}';
  String get description => '${transaction['description'] ?? ''}';
  int get cents => bankCents(transaction['amount']);
  String? get suggestion => transaction['suggestedCategory'] as String?;

  /// Dezelfde tekst waarop de backend een tekstregel laat zoeken.
  late final String text = '${transaction['counterparty'] ?? ''} $description'
      .toLowerCase();
}

const _monthNames = [
  'jan',
  'feb',
  'mrt',
  'apr',
  'mei',
  'jun',
  'jul',
  'aug',
  'sep',
  'okt',
  'nov',
  'dec',
];

String bankShortDate(String iso) {
  final date = DateTime.tryParse(iso);
  if (date == null) return iso;
  final year = date.year == DateTime.now().year ? '' : ' ${date.year}';
  return '${date.day} ${_monthNames[date.month - 1]}$year';
}

String _payments(int count) => count == 1 ? '1 betaling' : '$count betalingen';

/// Alle openstaande betalingen in één doorzoekbare lijst. Je kiest een
/// categorie en het bereik: alleen de selectie, de hele winkel of een tekstregel.
class BankReviewPanel extends StatefulWidget {
  const BankReviewPanel({
    super.key,
    required this.api,
    required this.categories,
    required this.onSaved,
  });

  final ApiClient api;
  final Map<String, String> categories;
  final Future<void> Function() onSaved;

  @override
  State<BankReviewPanel> createState() => _BankReviewPanelState();
}

class _BankReviewPanelState extends State<BankReviewPanel> {
  static const _path = '/api/v1/banking/review';
  List<_ReviewRow>? _rows;
  final _selected = <String>{};
  final _ruleText = TextEditingController();
  String _query = '';
  String _sort = 'date';
  String _scope = 'ONLY';
  String? _category;
  String? _error;
  String? _notice;
  bool _loading = true;
  bool _saving = false;
  int _version = 0;

  Iterable<MapEntry<String, String>> get _categories => widget
      .categories
      .entries
      .where((e) => !['REVIEW', 'INTERNAL', 'INCOME'].contains(e.key));

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _ruleText.dispose();
    super.dispose();
  }

  Future<void> _load({bool quiet = false}) async {
    final version = ++_version;
    if (!quiet) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final result = await widget.api.getJson(_path);
      if (!mounted || version != _version) return;
      final rows = (result['rows'] as List? ?? [])
          .map((e) => _ReviewRow(Map<String, dynamic>.from(e as Map)))
          .toList();
      setState(() {
        _rows = rows;
        _selected.removeWhere((id) => rows.every((row) => row.id != id));
      });
    } catch (_) {
      if (mounted && version == _version && !quiet) {
        setState(
          () =>
              _error = 'De openstaande betalingen konden niet worden geladen.',
        );
      }
    } finally {
      if (mounted && version == _version && !quiet) {
        setState(() => _loading = false);
      }
    }
  }

  List<_ReviewRow> get _all => _rows ?? const [];
  List<_ReviewRow> get _chosen =>
      _all.where((row) => _selected.contains(row.id)).toList();

  List<_ReviewRow> get _visible {
    final needle = _query.trim().toLowerCase();
    final rows = needle.isEmpty
        ? [..._all]
        : _all
              .where(
                (row) =>
                    row.text.contains(needle) ||
                    row.merchant.toLowerCase().contains(needle),
              )
              .toList();
    if (_sort == 'merchant') {
      rows.sort((a, b) {
        final byName = a.merchant.toLowerCase().compareTo(
          b.merchant.toLowerCase(),
        );
        return byName != 0 ? byName : b.date.compareTo(a.date);
      });
    } else if (_sort == 'amount') {
      rows.sort((a, b) => a.cents.compareTo(b.cents));
    }
    return rows;
  }

  /// Winkelregel kan alleen als de selectie één herkenbare winkel is.
  bool get _merchantPossible {
    final chosen = _chosen;
    return chosen.isNotEmpty &&
        chosen.every((row) => row.canLearn) &&
        chosen.map((row) => row.merchantKey).toSet().length == 1;
  }

  List<_ReviewRow> _affected(String scope) {
    final chosen = _chosen;
    if (chosen.isEmpty) return const [];
    switch (scope) {
      case 'MERCHANT':
        if (!_merchantPossible) return const [];
        final key = chosen.first.merchantKey;
        return _all
            .where((row) => row.canLearn && row.merchantKey == key)
            .toList();
      case 'TEXT':
        final needle = _ruleText.text.trim().toLowerCase();
        if (needle.length < 3) return const [];
        return _all.where((row) => row.text.contains(needle)).toList();
      default:
        return chosen;
    }
  }

  int _spent(Iterable<_ReviewRow> rows) =>
      rows.fold(0, (sum, row) => sum - row.cents);

  void _toggle(_ReviewRow row, [bool? value]) {
    setState(() {
      final wasEmpty = _selected.isEmpty;
      if (value ?? !_selected.contains(row.id)) {
        _selected.add(row.id);
      } else {
        _selected.remove(row.id);
      }
      _notice = null;
      _error = null;
      if (wasEmpty && _selected.length == 1) {
        _ruleText.text = row.merchant;
        _scope = _merchantPossible ? 'MERCHANT' : 'ONLY';
        final suggestion = row.suggestion;
        if (suggestion != null &&
            _categories.any((entry) => entry.key == suggestion)) {
          _category = suggestion;
        }
      } else if (_scope == 'MERCHANT' && !_merchantPossible) {
        _scope = 'ONLY';
      }
    });
  }

  void _selectVisible() {
    final visible = _visible;
    if (visible.isEmpty) return;
    setState(() {
      final wasEmpty = _selected.isEmpty;
      _selected.addAll(visible.map((row) => row.id));
      _notice = null;
      if (wasEmpty) {
        _ruleText.text = _query.trim().length >= 3
            ? _query.trim()
            : visible.first.merchant;
      }
      _scope = _selected.length == 1 && _merchantPossible ? 'MERCHANT' : 'ONLY';
    });
  }

  Future<void> _apply() async {
    final category = _category;
    final affected = _affected(_scope);
    final chosen = _chosen;
    if (category == null || affected.isEmpty || chosen.isEmpty || _saving) {
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
      _notice = null;
    });
    try {
      final result = await widget.api.postJson('$_path/classify', {
        'category': category,
        'scope': _scope,
        'text': _scope == 'TEXT' ? _ruleText.text.trim() : '',
        'transactions': [
          for (final row in chosen)
            {'month': row.date.substring(0, 7), 'id': row.id},
        ],
      });
      if (!mounted) return;
      final done = affected.map((row) => row.id).toSet();
      final count = (result['count'] as num?)?.toInt() ?? done.length;
      setState(() {
        _rows = _all.where((row) => !done.contains(row.id)).toList();
        _selected.clear();
        _notice =
            '${_payments(count)} ingedeeld als ${widget.categories[category] ?? category}'
            '${_scope == 'ONLY' ? '' : '; nieuwe betalingen gaan vanzelf mee'}.';
      });
      // De lijst blijft staan; op de achtergrond halen we de echte stand op.
      await _load(quiet: true);
      await widget.onSaved();
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Indelen is niet gelukt. Je keuze blijft staan; probeer opnieuw.',
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (_loading && _rows == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_rows == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error ?? 'Laden is niet gelukt.'),
            TextButton(onPressed: _load, child: const Text('Opnieuw proberen')),
          ],
        ),
      );
    }
    final visible = _visible;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: 12,
          runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            SizedBox(
              width: 320,
              child: TextField(
                key: const ValueKey('review-search'),
                decoration: const InputDecoration(
                  prefixIcon: Icon(Icons.search),
                  hintText: 'Zoek op winkel of omschrijving',
                  isDense: true,
                ),
                onChanged: (value) => setState(() => _query = value),
              ),
            ),
            DropdownButton<String>(
              value: _sort,
              items: const [
                DropdownMenuItem(value: 'date', child: Text('Nieuwste eerst')),
                DropdownMenuItem(value: 'merchant', child: Text('Per winkel')),
                DropdownMenuItem(
                  value: 'amount',
                  child: Text('Hoogste bedrag eerst'),
                ),
              ],
              onChanged: (value) {
                if (value != null) setState(() => _sort = value);
              },
            ),
            TextButton(
              key: const ValueKey('review-select-visible'),
              onPressed: visible.isEmpty ? null : _selectVisible,
              child: Text('Selecteer alle ${visible.length}'),
            ),
            if (_selected.isNotEmpty)
              TextButton(
                onPressed: () => setState(_selected.clear),
                child: const Text('Selectie wissen'),
              ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Text(
            _all.isEmpty
                ? 'Alles is ingedeeld.'
                : '${_payments(visible.length)} nog in te delen · ${bankMoneyCents(_spent(visible))}'
                      '${visible.length == _all.length ? ' · alle maanden' : ' · van ${_all.length} in totaal'}',
            style: theme.textTheme.bodySmall,
          ),
        ),
        if (_notice != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              children: [
                Icon(Icons.check, size: 18, color: theme.colorScheme.primary),
                const SizedBox(width: 6),
                Expanded(child: Text(_notice!)),
              ],
            ),
          ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              _error!,
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ),
        Expanded(
          child: Card(
            margin: EdgeInsets.zero,
            clipBehavior: Clip.antiAlias,
            child: visible.isEmpty
                ? Center(
                    child: Text(
                      _all.isEmpty
                          ? 'Er staat niets meer open.'
                          : 'Geen openstaande betalingen met deze zoektekst.',
                    ),
                  )
                : ListView.separated(
                    itemCount: visible.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) => _rowTile(visible[index]),
                  ),
          ),
        ),
        if (_selected.isNotEmpty) _actionPanel(context),
      ],
    );
  }

  Widget _rowTile(_ReviewRow row) {
    final theme = Theme.of(context);
    final selected = _selected.contains(row.id);
    final suggestion = widget.categories[row.suggestion];
    return InkWell(
      key: ValueKey('review-row-${row.id}'),
      onTap: () => _toggle(row),
      child: Container(
        color: selected
            ? theme.colorScheme.primary.withValues(alpha: 0.08)
            : null,
        padding: const EdgeInsets.fromLTRB(4, 6, 16, 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Checkbox(
              value: selected,
              onChanged: (value) => _toggle(row, value),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    row.merchant,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    row.description,
                    style: theme.textTheme.bodySmall,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (suggestion != null)
                    Text(
                      'Voorstel: $suggestion',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.primary,
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  bankMoneyCents(-row.cents),
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                Text(bankShortDate(row.date), style: theme.textTheme.bodySmall),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _actionPanel(BuildContext context) {
    final theme = Theme.of(context);
    final chosen = _chosen;
    final merchantRows = _affected('MERCHANT');
    final textRows = _affected('TEXT');
    final affected = _affected(_scope);
    final sameMerchant =
        chosen.map((row) => row.merchantKey).toSet().length == 1;
    return Container(
      margin: const EdgeInsets.only(top: 12),
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.55,
      ),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        border: Border.all(color: theme.colorScheme.primary, width: 2),
        borderRadius: BorderRadius.circular(12),
      ),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Wrap(
              spacing: 12,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  '${chosen.length == 1 ? chosen.first.merchant : '${chosen.length} geselecteerd'} indelen als',
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                DropdownButton<String>(
                  key: const ValueKey('review-category'),
                  value: _category,
                  hint: const Text('Kies categorie'),
                  items: _categories
                      .map(
                        (e) => DropdownMenuItem(
                          value: e.key,
                          child: Text(e.value),
                        ),
                      )
                      .toList(),
                  onChanged: _saving
                      ? null
                      : (value) => setState(() => _category = value),
                ),
              ],
            ),
            _scopeOption(
              scope: 'ONLY',
              title: chosen.length == 1
                  ? 'Alleen deze betaling'
                  : 'Alleen deze ${chosen.length} betalingen',
              subtitle: bankMoneyCents(_spent(chosen)),
            ),
            _scopeOption(
              scope: 'MERCHANT',
              enabled: _merchantPossible,
              title: 'Deze winkel, ook in de toekomst',
              subtitle: _merchantPossible
                  ? '${_payments(merchantRows.length)} bij ${chosen.first.merchant} · ${bankMoneyCents(_spent(merchantRows))}'
                  : sameMerchant
                  ? 'Kan niet bij een betaalverzoek of overboeking; gebruik een tekstregel.'
                  : 'Kies betalingen van één winkel.',
            ),
            _scopeOption(
              scope: 'TEXT',
              title: 'Alles waar deze tekst in staat, ook in de toekomst',
              subtitle: _ruleText.text.trim().length < 3
                  ? 'Gebruik minimaal drie tekens.'
                  : '${_payments(textRows.length)} bij ${textRows.map((row) => row.merchantKey).toSet().length} winkel(s) · ${bankMoneyCents(_spent(textRows))}',
              extra: _scope == 'TEXT'
                  ? Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: TextField(
                        key: const ValueKey('review-rule-text'),
                        controller: _ruleText,
                        maxLength: 200,
                        decoration: const InputDecoration(
                          labelText: 'Naam of omschrijving bevat',
                          isDense: true,
                          counterText: '',
                        ),
                        onChanged: (_) => setState(() {}),
                      ),
                    )
                  : null,
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton(
                key: const ValueKey('review-apply'),
                onPressed: _category == null || affected.isEmpty || _saving
                    ? null
                    : _apply,
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(
                        _category == null
                            ? 'Kies eerst een categorie'
                            : '${_payments(affected.length)} indelen',
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _scopeOption({
    required String scope,
    required String title,
    required String subtitle,
    bool enabled = true,
    Widget? extra,
  }) {
    final theme = Theme.of(context);
    final active = _scope == scope;
    final color = enabled ? null : theme.disabledColor;
    return InkWell(
      key: ValueKey('review-scope-$scope'),
      borderRadius: BorderRadius.circular(8),
      onTap: enabled && !_saving ? () => setState(() => _scope = scope) : null,
      child: Container(
        decoration: BoxDecoration(
          color: active
              ? theme.colorScheme.primary.withValues(alpha: 0.08)
              : null,
          borderRadius: BorderRadius.circular(8),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              active
                  ? Icons.radio_button_checked
                  : Icons.radio_button_unchecked,
              size: 20,
              color: active ? theme.colorScheme.primary : color,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(fontWeight: FontWeight.w600, color: color),
                  ),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodySmall?.copyWith(color: color),
                  ),
                  ?extra,
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
