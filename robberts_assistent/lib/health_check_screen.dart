import 'package:flutter/material.dart';

import 'api_client.dart';

/// 'Health check'-scherm: toont uitsluitend de systeemstatus-sectie (`key == 'system-status'`)
/// van `GET /api/v1/briefing` — per onderdeel (zonnepanelen, backups, OpenShift, robotmaaier,
/// Software Factory) een duidelijke kop met daaronder de ruwe, niet-AI-samengevatte statusregel(s)
/// die de backend al berekent (`SystemStatusSectionProvider`'s `BriefingItem.heading`/`text`), in
/// bullet-vorm. Alle tekst is selecteerbaar (`SelectableText`) zodat Robbert statusregels kan
/// kopiëren. Hergebruikt dezelfde databron als `SummaryScreen` (geen apart backend-endpoint).
class HealthCheckScreen extends StatefulWidget {
  const HealthCheckScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<HealthCheckScreen> createState() => _HealthCheckScreenState();
}

class _HealthCheckScreenState extends State<HealthCheckScreen> {
  BriefingData? _data;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final data = await widget.api.getBriefing();
      if (mounted) setState(() => _data = data);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    }
  }

  BriefingSection? _systemStatusSection() {
    final data = _data;
    if (data == null) return null;
    for (final section in data.sections) {
      if (section.key == 'system-status') return section;
    }
    return null;
  }

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
    if (_data == null) {
      return const Center(child: CircularProgressIndicator());
    }
    final section = _systemStatusSection();
    final items = section?.items ?? [];
    if (items.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          children: const [
            SizedBox(height: 80),
            Center(child: Text('Geen systeemstatus beschikbaar.')),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: items.map(_buildItemCard).toList(),
      ),
    );
  }

  Widget _buildItemCard(BriefingItem item) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SelectableText(
              item.heading ?? '',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 8),
            for (final line in item.text.split('\n'))
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: SelectableText('•  $line'),
              ),
          ],
        ),
      ),
    );
  }
}
