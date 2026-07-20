import 'package:flutter/material.dart';

import 'api_client.dart';

/// Overzicht van alle nightly checks (bv. OpenShift-gezondheid, later ook tuin-water/kite/
/// zonnepanelen/agenda-reminders): laatste status + tijdstip, en een knop om een check meteen
/// opnieuw te draaien (buiten zijn eigen cron-schema om). Tikken op een check opent de historie.
class NightlyChecksScreen extends StatefulWidget {
  const NightlyChecksScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<NightlyChecksScreen> createState() => _NightlyChecksScreenState();
}

class _NightlyChecksScreenState extends State<NightlyChecksScreen> {
  List<NightlyCheck> _checks = [];
  bool _loading = true;
  String? _error;
  String? _runningId;

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
      final checks = await widget.api.listNightlyChecks();
      setState(() => _checks = checks);
    } catch (e) {
      setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _runNow(NightlyCheck check) async {
    setState(() => _runningId = check.id);
    try {
      final run = await widget.api.runNightlyCheck(check.id);
      setState(() {
        _checks = _checks
            .map((c) => c.id == check.id
                ? NightlyCheck(
                    id: c.id,
                    name: c.name,
                    description: c.description,
                    cronSchedule: c.cronSchedule,
                    lastRun: run,
                  )
                : c)
            .toList();
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Draaien mislukt: $e')));
      }
    } finally {
      if (mounted) setState(() => _runningId = null);
    }
  }

  void _openHistory(NightlyCheck check) {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => NightlyCheckHistoryScreen(api: widget.api, check: check)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Nachtchecks'),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : _checks.isEmpty
                  ? const Center(child: Text('Nog geen nightly checks geregistreerd.'))
                  : ListView(
                      children: _checks
                          .map(
                            (c) => _NightlyCheckCard(
                              check: c,
                              running: _runningId == c.id,
                              onRunNow: () => _runNow(c),
                              onOpenHistory: () => _openHistory(c),
                            ),
                          )
                          .toList(),
                    ),
    );
  }
}

class _NightlyCheckCard extends StatelessWidget {
  const _NightlyCheckCard({
    required this.check,
    required this.running,
    required this.onRunNow,
    required this.onOpenHistory,
  });

  final NightlyCheck check;
  final bool running;
  final VoidCallback onRunNow;
  final VoidCallback onOpenHistory;

  @override
  Widget build(BuildContext context) {
    final lastRun = check.lastRun;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: InkWell(
        onTap: onOpenHistory,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _statusIcon(lastRun),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(check.name, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                    const SizedBox(height: 2),
                    Text(check.description, style: const TextStyle(color: Colors.black54, fontSize: 13)),
                    const SizedBox(height: 8),
                    if (lastRun != null)
                      Text(
                        '${lastRun.summary}  ·  ${_formatTime(lastRun.ranAt)}',
                        style: TextStyle(
                          color: lastRun.ok ? Colors.green.shade700 : Colors.red.shade700,
                          fontSize: 13,
                        ),
                      )
                    else
                      const Text('Nog niet gedraaid.', style: TextStyle(color: Colors.black54, fontSize: 13)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              running
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : IconButton(tooltip: 'Nu draaien', icon: const Icon(Icons.play_arrow), onPressed: onRunNow),
            ],
          ),
        ),
      ),
    );
  }

  Widget _statusIcon(CheckRun? lastRun) {
    if (lastRun == null) return const Icon(Icons.help_outline, color: Colors.grey, size: 28);
    return Icon(
      lastRun.ok ? Icons.check_circle : Icons.error,
      color: lastRun.ok ? Colors.green : Colors.red,
      size: 28,
    );
  }

  String _formatTime(DateTime t) =>
      '${t.day.toString().padLeft(2, '0')}-${t.month.toString().padLeft(2, '0')} '
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
}

/// Historie van één check: alle eerdere uitvoeringen, nieuwste eerst.
class NightlyCheckHistoryScreen extends StatefulWidget {
  const NightlyCheckHistoryScreen({super.key, required this.api, required this.check});

  final ApiClient api;
  final NightlyCheck check;

  @override
  State<NightlyCheckHistoryScreen> createState() => _NightlyCheckHistoryScreenState();
}

class _NightlyCheckHistoryScreenState extends State<NightlyCheckHistoryScreen> {
  List<CheckRun> _runs = [];
  bool _loading = true;
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
      final runs = await widget.api.nightlyCheckHistory(widget.check.id);
      setState(() => _runs = runs);
    } catch (e) {
      setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.check.name),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : _runs.isEmpty
                  ? const Center(child: Text('Nog geen historie.'))
                  : ListView.separated(
                      itemCount: _runs.length,
                      separatorBuilder: (context, index) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final run = _runs[index];
                        return ListTile(
                          leading: Icon(
                            run.ok ? Icons.check_circle : Icons.error,
                            color: run.ok ? Colors.green : Colors.red,
                          ),
                          title: Text(run.summary),
                          subtitle: run.detail != null ? Text(run.detail!) : null,
                          trailing: Text(_formatDateTime(run.ranAt), textAlign: TextAlign.end),
                        );
                      },
                    ),
    );
  }

  String _formatDateTime(DateTime t) =>
      '${t.year}-${t.month.toString().padLeft(2, '0')}-${t.day.toString().padLeft(2, '0')}\n'
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
}
