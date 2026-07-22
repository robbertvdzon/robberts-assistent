import 'package:flutter/material.dart';

import 'api_client.dart';

/// 'Morgen'-scherm: dagelijkse briefing met weerkaart, kite/strandfiets-kans, agenda komende
/// 7 dagen (incl. één-tap reminder-actie per afspraak zonder reminder), AI-weektakensamenvatting
/// en de moestuin-placeholder. Vult de bestaande "Samenvatting"-tab (geen nieuwe navigatie-ingang)
/// en wordt ook geopend door een tik op de dagelijkse 18:00-FCM-push (zie `FcmService`). Toont de
/// gecachete data direct ("Bijgewerkt om ...") met een reload-knop bovenin die de backend live laat
/// opbouwen (`POST /api/v1/briefing/refresh`), los van de pull-to-refresh die de cache ophaalt.
class SummaryScreen extends StatefulWidget {
  const SummaryScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<SummaryScreen> createState() => _SummaryScreenState();
}

class _SummaryScreenState extends State<SummaryScreen> {
  BriefingData? _data;
  String? _error;
  bool _refreshing = false;
  final _runningActions = <BriefingAction>{};

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

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    try {
      final data = await widget.api.refreshBriefing();
      if (mounted) setState(() => _data = data);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Verversen mislukt: $e')));
      }
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  Future<void> _runAction(BriefingAction action) async {
    setState(() => _runningActions.add(action));
    try {
      await widget.api.runBriefingAction(action);
      await _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Actie mislukt: $e')));
      }
    } finally {
      if (mounted) setState(() => _runningActions.remove(action));
    }
  }

  static const _icons = {
    'weather-map': Icons.map_outlined,
    'kite': Icons.air,
    'beach': Icons.pedal_bike,
    'agenda': Icons.event_outlined,
    'week-tasks': Icons.checklist_outlined,
    'moestuin': Icons.grass,
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
    if (_data == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildHeaderRow(_data!.updatedAt),
          const SizedBox(height: 8),
          ..._data!.sections.map(_buildSectionCard),
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
                child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
              )
            : IconButton(
                tooltip: 'Briefing verversen',
                icon: const Icon(Icons.refresh),
                onPressed: _refresh,
              ),
      ],
    );
  }

  String _formatTime(DateTime at) =>
      '${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}';

  Widget _buildSectionCard(BriefingSection section) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(_icons[section.key] ?? Icons.info_outline),
                const SizedBox(width: 8),
                Expanded(child: Text(section.title, style: const TextStyle(fontWeight: FontWeight.bold))),
              ],
            ),
            const SizedBox(height: 8),
            if (section.items.isEmpty)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final line in section.text.split('\n'))
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Text(line),
                    ),
                ],
              )
            else
              ...section.items.map((item) => _buildItemRow(item, _data!.updatedAt)),
          ],
        ),
      ),
    );
  }

  /// Hangt een cache-bust-query-param aan een relatief `imageUrl` op basis van [updatedAt], zodat
  /// Flutter's `ImageCache` (keyed op URL) na elke nieuwe cache-refresh de afbeelding opnieuw
  /// ophaalt i.p.v. de eerder getoonde versie te hergebruiken.
  String _cacheBustedImageUrl(String imageUrl, DateTime updatedAt) {
    final separator = imageUrl.contains('?') ? '&' : '?';
    return '$imageUrl${separator}v=${updatedAt.millisecondsSinceEpoch ~/ 1000}';
  }

  Widget _buildItemRow(BriefingItem item, DateTime updatedAt) {
    final action = item.action;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (item.imageUrl != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.network(
                '${ApiClient.baseUrl}${_cacheBustedImageUrl(item.imageUrl!, updatedAt)}',
                headers: widget.api.authHeaders(),
                errorBuilder: (context, error, stackTrace) => const Padding(
                  padding: EdgeInsets.all(24),
                  child: Icon(Icons.image_not_supported_outlined),
                ),
              ),
            ),
          Text(item.text),
          if (action != null)
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: _runningActions.contains(action) ? null : () => _runAction(action),
                child: _runningActions.contains(action)
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(action.label),
              ),
            ),
        ],
      ),
    );
  }
}
