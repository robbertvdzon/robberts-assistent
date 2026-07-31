import 'package:flutter/material.dart';

import 'api_client.dart';
import 'app_logo.dart';
import 'section_heading.dart';

/// 'Health check'-scherm: toont uitsluitend de systeemstatus-sectie (`key == 'system-status'`)
/// van `GET /api/v1/briefing/health` — per onderdeel (zonnepanelen, backups, OpenShift,
/// robotmaaier, Software Factory) een duidelijke kop met daaronder de ruwe, niet-AI-samengevatte
/// statusregel(s) die de backend al berekent (`SystemStatusSectionProvider`'s
/// `BriefingItem.heading`/`text`), in bullet-vorm. Alle tekst is selecteerbaar (`SelectableText`)
/// zodat Robbert statusregels kan kopiëren. Sinds SF-1275 heeft dit scherm een eigen cache/
/// `updatedAt`, los van `SummaryScreen`'s Upcoming-briefing: verversen hier raakt de Upcoming-tab
/// niet en andersom (zie `ApiClient.getHealthCheck`/`refreshHealthCheck`).
class HealthCheckScreen extends StatefulWidget {
  const HealthCheckScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<HealthCheckScreen> createState() => _HealthCheckScreenState();
}

class _HealthCheckScreenState extends State<HealthCheckScreen> {
  BriefingData? _data;
  String? _error;
  bool _refreshing = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final data = await widget.api.getHealthCheck();
      if (mounted) setState(() => _data = data);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    }
  }

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    try {
      final data = await widget.api.refreshHealthCheck();
      if (mounted) setState(() => _data = data);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Verversen mislukt: $e')));
      }
    } finally {
      if (mounted) setState(() => _refreshing = false);
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
    return Scaffold(
      appBar: AppBar(title: const AppHeaderTitle()),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_error != null) {
      return RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Health check',
              style: Theme.of(context).textTheme.headlineLarge,
            ),
            const SizedBox(height: 32),
            Center(
              child: Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          ],
        ),
      );
    }
    if (_data == null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              'Health check',
              style: Theme.of(context).textTheme.headlineLarge,
            ),
          ),
          const Expanded(child: Center(child: CircularProgressIndicator())),
        ],
      );
    }
    final section = _systemStatusSection();
    final items = section?.items ?? [];
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Health check',
            style: Theme.of(context).textTheme.headlineLarge,
          ),
          const SizedBox(height: 4),
          _buildHeaderRow(_data!.updatedAt),
          const SizedBox(height: 12),
          if (items.isEmpty)
            const Padding(
              padding: EdgeInsets.only(top: 64),
              child: Center(child: Text('Geen systeemstatus beschikbaar.')),
            )
          else
            ...items.map(_buildItemCard),
        ],
      ),
    );
  }

  Widget _buildHeaderRow(DateTime updatedAt) {
    return Row(
      children: [
        Expanded(
          child: Text(
            'Bijgewerkt om ${_formatTime(updatedAt)}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
        _refreshing
            ? const Padding(
                padding: EdgeInsets.all(8),
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              )
            : IconButton(
                tooltip: 'Systeemstatus verversen',
                icon: const Icon(Icons.refresh),
                onPressed: _refresh,
              ),
      ],
    );
  }

  String _formatTime(DateTime at) =>
      '${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}';

  Widget _buildItemCard(BriefingItem item) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SectionHeading(item.heading ?? '', selectable: true),
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
