import 'package:flutter/material.dart';

import 'api_client.dart';
import 'app_logo.dart';
import 'section_heading.dart';
import 'solar_screen.dart';
import 'status_pill.dart';

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

  /// De grafiek van de zonnepanelen komt los binnen; zonder blijft de kaart gewoon tekst.
  SolarOverview? _solar;

  @override
  void initState() {
    super.initState();
    _load();
    _loadSolar();
  }

  Future<void> _loadSolar() async {
    try {
      final solar = await widget.api.getSolar();
      if (mounted) setState(() => _solar = solar);
    } catch (_) {
      // De statusregels staan er al; alleen de grafiek ontbreekt dan.
    }
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
        // Een eigen padding schakelt de automatische ruimte voor de navigatiebalk van het toestel
        // uit; zonder deze onderruimte valt de laatste kaart er half achter.
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          24 + MediaQuery.viewPaddingOf(context).bottom,
        ),
        children: [
          Text(
            'Health check',
            style: Theme.of(context).textTheme.headlineLarge,
          ),
          const SizedBox(height: 4),
          _buildHeaderRow(_data!.updatedAt),
          const SizedBox(height: 8),
          if (items.any((item) => item.status != null)) ...[
            _buildSummary(items),
            const SizedBox(height: 12),
          ],
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

  static StatusPillVariant _variant(BriefingStatus status) => switch (status) {
    BriefingStatus.goed => StatusPillVariant.good,
    BriefingStatus.letOp => StatusPillVariant.warning,
    BriefingStatus.niet => StatusPillVariant.critical,
  };

  /// In één oogopslag: is alles goed, en zo niet, welke onderdelen niet.
  Widget _buildSummary(List<BriefingItem> items) {
    final attention = items
        .where((i) => i.status != null && i.status != BriefingStatus.goed)
        .toList();
    final worst = attention.any((i) => i.status == BriefingStatus.niet)
        ? BriefingStatus.niet
        : attention.isEmpty
        ? BriefingStatus.goed
        : BriefingStatus.letOp;
    final (background, foreground, icon) = switch (worst) {
      BriefingStatus.goed => (
        const Color(0xFFE9F6E9),
        const Color(0xFF0A6D0A),
        Icons.check_circle_outline,
      ),
      BriefingStatus.letOp => (
        const Color(0xFFFDF3DF),
        const Color(0xFF8A5C05),
        Icons.error_outline,
      ),
      BriefingStatus.niet => (
        const Color(0xFFFBEAEA),
        const Color(0xFFA52C2C),
        Icons.warning_amber_outlined,
      ),
    };
    final text = attention.isEmpty
        ? 'Alles in orde'
        : '${attention.length == 1 ? 'Eén onderdeel vraagt' : '${attention.length} onderdelen vragen'} '
              'aandacht: ${attention.map((i) => i.heading ?? '').join(', ')}';
    return Container(
      key: const ValueKey('health-summary'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(icon, color: foreground),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(color: foreground, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildItemCard(BriefingItem item) {
    final solar = item.heading == 'Zonnepanelen' ? _solar : null;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: SectionHeading(item.heading ?? '', selectable: true),
                ),
                if (item.status != null)
                  StatusPill(variant: _variant(item.status!)),
              ],
            ),
            const SizedBox(height: 8),
            for (final line in item.text.split('\n'))
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: SelectableText('•  $line'),
              ),
            if (solar != null && solar.days.isNotEmpty) ...[
              const SizedBox(height: 12),
              SolarChart(
                days: solar.days.length > 30
                    ? solar.days.sublist(solar.days.length - 30)
                    : solar.days,
              ),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) =>
                          SolarScreen(api: widget.api, initial: solar),
                    ),
                  ),
                  child: const Text('Meer grafieken'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
