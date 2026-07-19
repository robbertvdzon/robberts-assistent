import 'package:flutter/material.dart';

import 'alarm_scheduler.dart';
import 'api_client.dart';

/// Beheer van reminders (push-melding op tijd) en alarms (wekker op de telefoon). Beide eenmalig of
/// herhalend. Alarms worden na een wijziging opnieuw lokaal ingepland via [AlarmScheduler].
class SchedulesScreen extends StatefulWidget {
  const SchedulesScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<SchedulesScreen> createState() => _SchedulesScreenState();
}

class _SchedulesScreenState extends State<SchedulesScreen> {
  List<Reminder> _reminders = [];
  List<Alarm> _alarms = [];
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
      final reminders = await widget.api.listReminders();
      final alarms = await widget.api.listAlarms();
      setState(() {
        _reminders = reminders.where((r) => r.active).toList();
        _alarms = alarms.where((a) => a.active).toList();
      });
    } catch (e) {
      setState(() => _error = 'Laden mislukt: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resyncAlarms() => AlarmScheduler.sync(widget.api);

  Future<void> _add(bool isAlarm) async {
    final result = await showDialog<_NewItem>(
      context: context,
      builder: (_) => _AddDialog(isAlarm: isAlarm),
    );
    if (result == null) return;
    try {
      if (isAlarm) {
        await widget.api.createAlarm(message: result.message, at: result.at, recurrence: result.recurrence);
        await _resyncAlarms();
      } else {
        await widget.api.createReminder(message: result.message, at: result.at, recurrence: result.recurrence);
      }
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Opslaan mislukt: $e')));
    }
  }

  Future<void> _deleteReminder(String id) async {
    await widget.api.deleteReminder(id);
    await _load();
  }

  Future<void> _deleteAlarm(String id) async {
    await widget.api.deleteAlarm(id);
    await _resyncAlarms();
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Reminders & alarms'),
          bottom: const TabBar(tabs: [Tab(text: 'Reminders'), Tab(text: 'Alarms')]),
          actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
                : TabBarView(
                    children: [
                      _remindersTab(),
                      _alarmsTab(),
                    ],
                  ),
        floatingActionButton: Builder(
          builder: (context) => FloatingActionButton(
            onPressed: () => _add(DefaultTabController.of(context).index == 1),
            child: const Icon(Icons.add),
          ),
        ),
      ),
    );
  }

  Widget _remindersTab() {
    if (_reminders.isEmpty) return const _Empty('Nog geen reminders.\nTik op + om er een te maken.');
    return ListView(
      children: _reminders
          .map((r) => ListTile(
                leading: const Icon(Icons.notifications_active, color: Colors.deepPurple),
                title: Text(r.message),
                subtitle: Text(_subtitle(r.dueAt, r.recurrence)),
                trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _deleteReminder(r.id)),
              ))
          .toList(),
    );
  }

  Future<void> _requestExact() async {
    await AlarmScheduler.requestExactPermission();
    await _resyncAlarms();
    if (mounted) setState(() {});
  }

  Widget _alarmsTab() {
    final needPerm = AlarmScheduler.exactAllowed == false;
    if (_alarms.isEmpty && !needPerm) {
      return const _Empty('Nog geen alarms.\nTik op + om er een te maken.');
    }
    return ListView(
      children: [
        if (needPerm)
          Card(
            color: Colors.amber.shade100,
            margin: const EdgeInsets.all(12),
            child: ListTile(
              leading: const Icon(Icons.warning_amber),
              title: const Text('Exacte alarms staan uit'),
              subtitle: const Text('Alarms gaan nu mogelijk enkele minuten te laat af. '
                  'Sta "wekker/exact alarm" toe voor precieze timing.'),
              trailing: TextButton(onPressed: _requestExact, child: const Text('Sta toe')),
            ),
          ),
        ..._alarms.map((a) => ListTile(
              leading: const Icon(Icons.alarm, color: Colors.deepPurple),
              title: Text(a.message),
              subtitle: Text(_subtitle(a.time, a.recurrence)),
              trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _deleteAlarm(a.id)),
            )),
      ],
    );
  }

  String _subtitle(DateTime when, Recurrence? recurrence) {
    final d = when;
    final date = '${d.day}-${d.month}-${d.year} ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
    return recurrence == null ? date : '$date · ${recurrence.label}';
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(text, textAlign: TextAlign.center, style: const TextStyle(color: Colors.black54)),
        ),
      );
}

/// Resultaat van het toevoeg-dialoog.
class _NewItem {
  final String message;
  final DateTime at;
  final Recurrence? recurrence;
  _NewItem(this.message, this.at, this.recurrence);
}

class _AddDialog extends StatefulWidget {
  const _AddDialog({required this.isAlarm});
  final bool isAlarm;
  @override
  State<_AddDialog> createState() => _AddDialogState();
}

class _AddDialogState extends State<_AddDialog> {
  final _controller = TextEditingController();
  DateTime _date = DateTime.now().add(const Duration(minutes: 5));
  String _recurrence = 'Eenmalig';

  static const _options = <String, Recurrence?>{
    'Eenmalig': null,
    'Elke dag': Recurrence('DAYS', 1),
    'Elke week': Recurrence('WEEKS', 1),
    'Elke maand': Recurrence('MONTHS', 1),
    'Elke 3 maanden': Recurrence('MONTHS', 3),
    'Elk jaar': Recurrence('YEARS', 1),
  };

  Future<void> _pickDate() async {
    final d = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 366 * 3)),
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(_date));
    if (t == null) return;
    setState(() => _date = DateTime(d.year, d.month, d.day, t.hour, t.minute));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final d = _date;
    final dateLabel = '${d.day}-${d.month}-${d.year} ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
    return AlertDialog(
      title: Text(widget.isAlarm ? 'Nieuw alarm' : 'Nieuwe reminder'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _controller,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Omschrijving'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _pickDate,
              icon: const Icon(Icons.schedule),
              label: Text(dateLabel),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _recurrence,
              decoration: const InputDecoration(labelText: 'Herhaling'),
              items: _options.keys.map((k) => DropdownMenuItem(value: k, child: Text(k))).toList(),
              onChanged: (v) => setState(() => _recurrence = v ?? 'Eenmalig'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuleren')),
        FilledButton(
          onPressed: () {
            final msg = _controller.text.trim();
            if (msg.isEmpty) return;
            Navigator.pop(context, _NewItem(msg, _date, _options[_recurrence]));
          },
          child: const Text('Opslaan'),
        ),
      ],
    );
  }
}
