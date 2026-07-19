import 'package:flutter/material.dart';

import 'api_client.dart';

/// Overzicht van alle externe koppelingen (OpenAI, Telegram, Firestore, Storage, Google, FCM):
/// per koppeling of 'ie geconfigureerd is en of de echte koppeling of de fallback actief is,
/// plus een knop om ze allemaal live te testen.
class CouplingsScreen extends StatefulWidget {
  const CouplingsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<CouplingsScreen> createState() => _CouplingsScreenState();
}

class _CouplingsScreenState extends State<CouplingsScreen> {
  List<Coupling> _couplings = [];
  bool _loading = true;
  bool _testing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final couplings = await widget.api.listCouplings();
      setState(() => _couplings = couplings);
    } catch (e) {
      setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _test() async {
    setState(() => _testing = true);
    try {
      final couplings = await widget.api.testCouplings();
      setState(() => _couplings = couplings);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Test mislukt: $e')));
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Koppelingen'),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
                      child: SizedBox(
                        width: double.infinity,
                        child: FilledButton.icon(
                          onPressed: _testing ? null : _test,
                          icon: _testing
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                )
                              : const Icon(Icons.play_arrow),
                          label: Text(_testing ? 'Testen...' : 'Test alle koppelingen'),
                        ),
                      ),
                    ),
                    Expanded(
                      child: ListView(
                        children: _couplings.map((c) => _CouplingCard(c)).toList(),
                      ),
                    ),
                  ],
                ),
    );
  }
}

class _CouplingCard extends StatelessWidget {
  const _CouplingCard(this.coupling);
  final Coupling coupling;

  @override
  Widget build(BuildContext context) {
    final test = coupling.test;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _statusIcon(),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(coupling.name, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                  const SizedBox(height: 2),
                  Text(coupling.description, style: const TextStyle(color: Colors.black54, fontSize: 13)),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: [
                      _chip(
                        coupling.isReal ? 'echt' : 'fallback',
                        coupling.isReal ? Colors.green : Colors.orange,
                      ),
                      _chip(
                        coupling.configured ? 'geconfigureerd' : 'niet geconfigureerd',
                        coupling.configured ? Colors.blueGrey : Colors.grey,
                      ),
                    ],
                  ),
                  if (test != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        '${test.ok ? '✓' : '✗'} ${test.detail}  ·  ${test.durationMs} ms',
                        style: TextStyle(
                          color: test.ok ? Colors.green.shade700 : Colors.red.shade700,
                          fontSize: 13,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _statusIcon() {
    final test = coupling.test;
    if (test != null) {
      return Icon(
        test.ok ? Icons.check_circle : Icons.error,
        color: test.ok ? Colors.green : Colors.red,
        size: 28,
      );
    }
    return Icon(
      coupling.isReal ? Icons.link : Icons.link_off,
      color: coupling.isReal ? Colors.green : Colors.orange,
      size: 28,
    );
  }

  Widget _chip(String label, Color color) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(label, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      );
}
