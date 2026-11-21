import 'dart:convert';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import 'api_client.dart';
import 'bank_insights.dart';
import 'status_pill.dart';

/// Kiest de Rompslomp-export; `null` als de gebruiker annuleert.
typedef InvoiceFilePicker = Future<Uint8List?> Function();

Future<Uint8List?> _pickInvoiceExport() async {
  final files = await FilePicker.pickFiles(
    dialogTitle: 'Kies de factuurexport van Rompslomp',
    type: FileType.custom,
    allowedExtensions: const ['xlsx', 'xls'],
  );
  if (files.isEmpty) return null;
  return files.first.xFile.readAsBytes();
}

/// Betaalcontrole: facturen uit de Rompslomp-export naast de bijschrijvingen van de bank.
class BankInvoicesPanel extends StatefulWidget {
  const BankInvoicesPanel({super.key, required this.api, this.pickFile});

  final ApiClient api;
  final InvoiceFilePicker? pickFile;

  @override
  State<BankInvoicesPanel> createState() => _BankInvoicesPanelState();
}

class _BankInvoicesPanelState extends State<BankInvoicesPanel> {
  static const _path = '/api/v1/banking/invoices';
  static const _filters = {
    'open': 'Open',
    'likely': 'Waarschijnlijk betaald',
    'paid': 'Betaald',
    'all': 'Alles',
  };
  Map<String, dynamic>? _report;
  String _filter = 'open';
  String? _error;
  String? _notice;
  bool _busy = false;
  int _version = 0;

  @override
  void initState() {
    super.initState();
    _run(() => widget.api.getJson(_path));
  }

  List<Map<String, dynamic>> _rows(dynamic value) => (value as List? ?? [])
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();

  Future<void> _run(
    Future<Map<String, dynamic>> Function() action, {
    String failure = 'De facturen konden niet worden opgehaald.',
    String? badRequest,
    String? notice,
  }) async {
    badRequest ??= failure;
    final version = ++_version;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      final report = await action();
      if (!mounted || version != _version) return;
      setState(() {
        _report = report;
        _notice = notice;
      });
    } catch (e) {
      if (mounted && version == _version) {
        // De backend legt bij een verkeerd bestand zelf uit wat er verwacht wordt.
        setState(
          () => _error = '$e'.contains('HTTP 400') ? badRequest : failure,
        );
      }
    } finally {
      if (mounted && version == _version) setState(() => _busy = false);
    }
  }

  Future<void> _import() async {
    Uint8List? bytes;
    try {
      bytes = await (widget.pickFile ?? _pickInvoiceExport)();
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Het bestand kon niet worden geopend.');
      }
      return;
    }
    if (bytes == null || !mounted) return;
    final content = base64Encode(bytes);
    await _run(
      () => widget.api.postJson('$_path/import', {'content': content}),
      failure: 'Het inlezen is niet gelukt. Probeer opnieuw.',
      badRequest:
          'Dit is geen bruikbare factuurexport. Kies in Rompslomp Facturen → Exporteer → Download als .xls.',
      notice: 'Export ingelezen.',
    );
  }

  Future<void> _decide(
    Map<String, dynamic> invoice,
    String action, [
    String transactionId = '',
  ]) => _run(
    () => widget.api.putJson('$_path/${invoice['number']}', {
      'action': action,
      'transactionId': transactionId,
    }),
    failure: 'De keuze kon niet worden opgeslagen.',
  );

  bool _shown(Map<String, dynamic> invoice) => switch (_filter) {
    'open' => invoice['status'] == 'OPEN' || invoice['status'] == 'CHECK',
    'likely' => invoice['status'] == 'LIKELY',
    'paid' => invoice['status'] == 'PAID',
    _ => true,
  };

  String _date(dynamic value) {
    final parts = '$value'.split('-');
    return parts.length == 3 ? '${parts[2]}-${parts[1]}-${parts[0]}' : '$value';
  }

  Widget _total(String label, dynamic total, {bool alert = false}) {
    final map = Map<String, dynamic>.from(total as Map? ?? {});
    final count = map['count'] as num? ?? 0;
    return SizedBox(
      width: 190,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: Theme.of(context).textTheme.labelLarge),
              const SizedBox(height: 4),
              Text(
                bankMoney(num.parse('${map['amount'] ?? 0}')),
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: alert && count > 0
                      ? Theme.of(context).colorScheme.error
                      : null,
                ),
              ),
              Text('$count ${count == 1 ? 'factuur' : 'facturen'}'),
            ],
          ),
        ),
      ),
    );
  }

  Widget _pill(Map<String, dynamic> invoice) {
    final overdue = invoice['overdue'] == true;
    return switch (invoice['status']) {
      'PAID' => const StatusPill(
        variant: StatusPillVariant.good,
        label: 'betaald',
      ),
      'LIKELY' => const StatusPill(
        variant: StatusPillVariant.neutral,
        label: 'waarschijnlijk betaald',
      ),
      'CHECK' => const StatusPill(
        variant: StatusPillVariant.warning,
        label: 'bedrag wijkt af',
      ),
      _ => StatusPill(
        variant: overdue
            ? StatusPillVariant.critical
            : StatusPillVariant.warning,
        label: overdue ? 'te laat' : 'open',
      ),
    };
  }

  Widget _payment(
    Map<String, dynamic> invoice,
    Map<String, dynamic> payment, {
    required bool candidate,
  }) {
    final id = '${payment['transactionId']}';
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Wrap(
        spacing: 8,
        runSpacing: 4,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          Text(
            '${candidate ? 'Mogelijk' : 'Bijschrijving'}: ${_date(payment['date'])} · '
            '${bankMoney(num.parse('${payment['amount']}'))} · ${payment['counterparty']}'
            '${'${payment['description']}'.isEmpty ? '' : ' · ${payment['description']}'}',
          ),
          if (candidate)
            TextButton(
              onPressed: _busy ? null : () => _decide(invoice, 'PAID', id),
              child: const Text('Dit is de betaling'),
            ),
          if (!candidate && invoice['manual'] != true)
            TextButton(
              onPressed: _busy ? null : () => _decide(invoice, 'REJECT', id),
              child: const Text('Dit is niet de betaling'),
            ),
        ],
      ),
    );
  }

  Widget _invoice(Map<String, dynamic> invoice) {
    final status = invoice['status'];
    final due = '${invoice['dueDate']}';
    return Card(
      key: ValueKey('factuur-${invoice['number']}'),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 12,
              runSpacing: 6,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  '${invoice['customer']}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text(
                  bankMoney(num.parse('${invoice['amount']}')),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                _pill(invoice),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              '${invoice['number']} · ${_date(invoice['date'])}'
              '${due.isEmpty ? '' : ' · vervalt ${_date(due)}'}',
            ),
            if ('${invoice['description']}'.isNotEmpty)
              Text('${invoice['description']}'),
            const SizedBox(height: 4),
            Text(
              '${invoice['reason']}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            for (final p in _rows(invoice['payments']))
              _payment(invoice, p, candidate: false),
            for (final p in _rows(invoice['candidates']))
              _payment(invoice, p, candidate: true),
            Wrap(
              spacing: 8,
              children: [
                if (status != 'PAID')
                  TextButton(
                    onPressed: _busy ? null : () => _decide(invoice, 'PAID'),
                    child: const Text('Markeer als betaald'),
                  ),
                if (invoice['manual'] == true)
                  TextButton(
                    onPressed: _busy ? null : () => _decide(invoice, 'RESET'),
                    child: const Text('Eigen keuze wissen'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final report = _report;
    final invoices = _rows(report?['invoices']);
    final shown = invoices.where(_shown).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: 12,
          runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            FilledButton.icon(
              onPressed: _busy ? null : _import,
              icon: const Icon(Icons.upload_file),
              label: const Text('Rompslomp-export inlezen'),
            ),
            if (report?['importedAt'] != null)
              Text(
                'Laatst ingelezen: ${_date('${report!['importedAt']}'.substring(0, 10))}',
              ),
            if (_busy)
              const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
          ],
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
        if (_notice != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_notice!),
          ),
        if (report != null) ...[
          for (final warning in (report['warnings'] as List? ?? []))
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                '$warning',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          if (invoices.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _total('Open', report['open']),
                _total('Waarvan te laat', report['overdue'], alert: true),
                _total('Waarschijnlijk betaald', report['likely']),
                _total('Betaald', report['paid']),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final entry in _filters.entries)
                  ChoiceChip(
                    label: Text(entry.value),
                    selected: _filter == entry.key,
                    onSelected: (_) => setState(() => _filter = entry.key),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            if (shown.isEmpty)
              const Padding(
                padding: EdgeInsets.all(16),
                child: Text('Geen facturen in deze selectie.'),
              ),
            for (final invoice in shown) _invoice(invoice),
          ],
        ],
      ],
    );
  }
}
