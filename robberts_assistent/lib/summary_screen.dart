import 'package:flutter/material.dart';

import 'api_client.dart';

class SummaryScreen extends StatefulWidget {
  const SummaryScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<SummaryScreen> createState() => _SummaryScreenState();
}

class _SummaryScreenState extends State<SummaryScreen> {
  List<Map<String, dynamic>>? _items;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final body = await widget.api.getJson('/api/v1/summary');
      final items = (body['items'] as List? ?? []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (mounted) setState(() => _items = items);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    }
  }

  static const _icons = {
    'wind': Icons.air,
    'moestuin': Icons.grass,
    'backups': Icons.backup,
    'openshift': Icons.dns,
    'zonnepanelen': Icons.solar_power,
  };

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          children: [
            const SizedBox(height: 80),
            Center(child: Text(_error!, style: const TextStyle(color: Colors.red))),
          ],
        ),
      );
    }
    if (_items == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: _items!
            .map(
              (item) => Card(
                child: ListTile(
                  leading: Icon(_icons[item['key']] ?? Icons.info_outline),
                  title: Text(item['title']?.toString() ?? ''),
                  subtitle: Text(item['text']?.toString() ?? ''),
                ),
              ),
            )
            .toList(),
      ),
    );
  }
}
