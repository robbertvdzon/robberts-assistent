import 'package:flutter/material.dart';

import 'api_client.dart';

/// 'Morgen'-scherm: dagelijkse briefing met kite/strandfiets-kans, agenda komende 7 dagen
/// (incl. één-tap reminder-actie per afspraak zonder reminder), AI-weektakensamenvatting en de
/// moestuin-placeholder. Vult de bestaande "Samenvatting"-tab (geen nieuwe navigatie-ingang) en
/// wordt ook geopend door een tik op de dagelijkse 18:00-FCM-push (zie `FcmService`).
class SummaryScreen extends StatefulWidget {
  const SummaryScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<SummaryScreen> createState() => _SummaryScreenState();
}

class _SummaryScreenState extends State<SummaryScreen> {
  List<BriefingSection>? _sections;
  String? _error;
  final _runningActions = <BriefingAction>{};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final sections = await widget.api.getBriefing();
      if (mounted) setState(() => _sections = sections);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
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
    if (_sections == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: _sections!.map(_buildSectionCard).toList(),
      ),
    );
  }

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
              ...section.items.map(_buildItemRow),
          ],
        ),
      ),
    );
  }

  Widget _buildItemRow(BriefingItem item) {
    final action = item.action;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
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
