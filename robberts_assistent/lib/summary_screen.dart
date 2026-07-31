import 'package:flutter/material.dart';

import 'api_client.dart';
import 'section_heading.dart';
import 'status_pill.dart';

/// 'Upcoming'-scherm: dagelijkse briefing met weerkaart, kite/strandfiets-kans, agenda komende
/// 7 dagen (incl. één-tap reminder-actie per afspraak zonder reminder), AI-weektakensamenvatting
/// en de moestuin-placeholder — alle secties van `GET /api/v1/briefing` behálve de
/// systeemstatus-sectie (die heeft een eigen tab, zie `HealthCheckScreen`). Wordt ook geopend door
/// een tik op de dagelijkse 18:00-FCM-push (zie `FcmService`). Toont de gecachete data direct
/// ("Bijgewerkt om ...") met een reload-knop bovenin die de backend live laat opbouwen
/// (`POST /api/v1/briefing/refresh`), los van de pull-to-refresh die de cache ophaalt.
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
  String? _expandedTileKey;
  final _tileDetailKey = GlobalKey();
  final _runningActions = <BriefingAction>{};

  static const _statusColors = {
    BriefingStatus.goed: Color(0xFF0CA30C),
    BriefingStatus.letOp: Color(0xFFFAB219),
    BriefingStatus.niet: Color(0xFFD03B3B),
  };

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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Verversen mislukt: $e')));
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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Actie mislukt: $e')));
      }
    } finally {
      if (mounted) setState(() => _runningActions.remove(action));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text('Vandaag', style: Theme.of(context).textTheme.headlineLarge),
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
              'Vandaag',
              style: Theme.of(context).textTheme.headlineLarge,
            ),
          ),
          const Expanded(child: Center(child: CircularProgressIndicator())),
        ],
      );
    }
    final sections = _data!.sections
        .where((s) => s.key != 'system-status')
        .toList();
    final tileSections = sections.where(_hasValidTile).take(3).toList();
    final regularSections = sections
        .where((s) => !tileSections.contains(s))
        .toList();
    final expandedSection = tileSections.cast<BriefingSection?>().firstWhere(
      (section) => section?.key == _expandedTileKey,
      orElse: () => null,
    );
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Vandaag', style: Theme.of(context).textTheme.headlineLarge),
          const SizedBox(height: 4),
          _buildHeaderRow(_data!.updatedAt),
          if (tileSections.isNotEmpty) ...[
            const SizedBox(height: 12),
            _buildTileRow(tileSections),
            if (expandedSection != null) ...[
              const SizedBox(height: 8),
              Container(
                key: _tileDetailKey,
                child: _buildSectionCard(expandedSection),
              ),
            ],
          ],
          if (regularSections.isNotEmpty) ...[
            const SizedBox(height: 12),
            ...regularSections.map(_buildSectionCard),
          ],
        ],
      ),
    );
  }

  bool _hasValidTile(BriefingSection section) =>
      section.status != null && (section.tileLabel?.trim().isNotEmpty ?? false);

  Widget _buildTileRow(List<BriefingSection> sections) {
    return Row(
      key: const ValueKey('status-tile-row'),
      children: [
        for (var index = 0; index < sections.length; index++) ...[
          if (index > 0) const SizedBox(width: 8),
          Expanded(child: _buildStatusTile(sections[index])),
        ],
      ],
    );
  }

  Widget _buildStatusTile(BriefingSection section) {
    final status = section.status!;
    final tileLabel = section.tileLabel!;
    final color = _statusColors[status]!;
    return Semantics(
      button: true,
      label: '${status.label}, ${section.title}, $tileLabel',
      onTap: () => _toggleTile(section),
      child: ExcludeSemantics(
        child: Card(
          margin: EdgeInsets.zero,
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            key: ValueKey('status-tile-${section.key}'),
            onTap: () => _toggleTile(section),
            child: Padding(
              padding: const EdgeInsets.all(10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(_sectionIcon(section.key), size: 22),
                  const SizedBox(height: 6),
                  Text(
                    section.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    tileLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Container(
                        key: ValueKey('status-dot-${section.key}'),
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                          color: color,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 5),
                      Expanded(
                        child: Text(
                          status.label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  IconData _sectionIcon(String key) => switch (key) {
    'kite' => Icons.air,
    'beach' => Icons.pedal_bike,
    'waste' => Icons.recycling,
    _ => Icons.info_outline,
  };

  void _toggleTile(BriefingSection section) {
    final opens = _expandedTileKey != section.key;
    setState(() => _expandedTileKey = opens ? section.key : null);
    if (opens) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final detailContext = _tileDetailKey.currentContext;
        if (detailContext != null) {
          Scrollable.ensureVisible(
            detailContext,
            duration: const Duration(milliseconds: 250),
            alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
          );
        }
      });
    }
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
            SectionHeading(section.title),
            const SizedBox(height: 10),
            if (section.items.isEmpty)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final line in section.text.split('\n'))
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: StatusTextLine(line),
                    ),
                ],
              )
            else
              ...section.items.map(
                (item) => _buildItemRow(item, _data!.updatedAt),
              ),
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
          StatusTextLine(item.text),
          if (action != null)
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: _runningActions.contains(action)
                    ? null
                    : () => _runAction(action),
                child: _runningActions.contains(action)
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(action.label),
              ),
            ),
        ],
      ),
    );
  }
}
