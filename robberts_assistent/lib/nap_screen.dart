import 'package:flutter/material.dart';

import 'api_client.dart';
import 'fcm_service.dart';
import 'nap_alarm.dart';

String _hm(DateTime t) =>
    '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

/// Duur als "25 min" of "1 u 10".
String napDuration(Duration d) {
  final minutes = d.inMinutes;
  if (minutes < 60) return '$minutes min';
  final rest = minutes % 60;
  return rest == 0
      ? '${minutes ~/ 60} u'
      : '${minutes ~/ 60} u ${rest.toString().padLeft(2, '0')}';
}

/// Korte omschrijving van een dutje voor in de tijdlijn.
String describeNap(Nap nap) {
  final end = nap.end;
  if (end == null) return 'Dutje sinds ${_hm(nap.start)}, loopt nog';
  return 'Dutje ${_hm(nap.start)}–${_hm(end)} · ${napDuration(end.difference(nap.start))}'
      '${nap.endEstimated ? ' (eind geschat)' : ''}';
}

const _alarmChoices = <int?>[null, 20, 30, 45, 60];
const _agoChoices = [0, 5, 10, 15, 30];

/// Dutje starten, afmelden, achteraf invullen of aanpassen.
///
/// Zonder [nap] kijkt het scherm of er een dutje loopt: zo ja, dan gaat het over wakker worden;
/// zo nee, dan over starten of achteraf invullen. Met [nap] wordt dat dutje aangepast.
class NapScreen extends StatefulWidget {
  const NapScreen({
    super.key,
    required this.api,
    this.nap,
    this.date,
    this.now,
    this.scheduleAlarm = NapAlarm.schedule,
    this.cancelAlarm = NapAlarm.cancel,
  });

  final ApiClient api;
  final Nap? nap;

  /// De dag waarop een achteraf ingevuld dutje valt; standaard vandaag.
  final DateTime? date;

  /// Vervangbaar in tests.
  final DateTime Function()? now;
  final Future<void> Function(DateTime at) scheduleAlarm;
  final Future<void> Function() cancelAlarm;

  @override
  State<NapScreen> createState() => _NapScreenState();
}

class _NapScreenState extends State<NapScreen> {
  Nap? _nap;
  bool _loading = true;
  bool _busy = false;
  String? _error;

  // Starten.
  int? _alarmMinutes;
  TimeOfDay? _alarmTime;

  // Afmelden.
  int _agoMinutes = 0;
  TimeOfDay? _wakeTime;

  // Achteraf invullen of aanpassen.
  TimeOfDay? _from;
  TimeOfDay? _to;

  DateTime _now() => (widget.now ?? DateTime.now)();

  @override
  void initState() {
    super.initState();
    final nap = widget.nap;
    if (nap != null) {
      _apply(nap);
      _loading = false;
    } else {
      _load();
    }
  }

  void _apply(Nap? nap) {
    _nap = nap;
    if (nap != null && !nap.open) {
      _from = TimeOfDay.fromDateTime(nap.start);
      _to = TimeOfDay.fromDateTime(nap.end!);
    }
  }

  Future<void> _load() async {
    try {
      final day = await widget.api.getNaps(_now());
      if (!mounted) return;
      setState(() {
        _apply(day.open);
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Het dutje kon niet worden geladen.';
        _loading = false;
      });
    }
  }

  void _snack(String text) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _run(Future<void> Function() action, String failure) async {
    setState(() => _busy = true);
    try {
      await action();
      if (mounted) Navigator.of(context).pop(true);
    } catch (_) {
      if (!mounted) return;
      setState(() => _busy = false);
      _snack(failure);
    }
  }

  Future<TimeOfDay?> _pick(TimeOfDay initial) =>
      showTimePicker(context: context, initialTime: initial);

  /// Het laatste moment met dit kloktijdstip dat niet in de toekomst ligt.
  DateTime _latest(TimeOfDay time) {
    final now = _now();
    final today = DateTime(
      now.year,
      now.month,
      now.day,
      time.hour,
      time.minute,
    );
    return today.isAfter(now)
        ? DateTime(now.year, now.month, now.day - 1, time.hour, time.minute)
        : today;
  }

  // -- Starten ------------------------------------------------------------------

  DateTime? _alarmAt(DateTime start) {
    final time = _alarmTime;
    if (time != null) {
      final sameDay = DateTime(
        start.year,
        start.month,
        start.day,
        time.hour,
        time.minute,
      );
      return sameDay.isAfter(start)
          ? sameDay
          : DateTime(
              start.year,
              start.month,
              start.day + 1,
              time.hour,
              time.minute,
            );
    }
    final minutes = _alarmMinutes;
    return minutes == null ? null : start.add(Duration(minutes: minutes));
  }

  Future<void> _start() => _run(() async {
    final start = _now();
    final alarmAt = _alarmAt(start);
    final nap = await widget.api.createNap(start: start, alarmAt: alarmAt);
    if (alarmAt != null) await widget.scheduleAlarm(alarmAt);
    await FcmService.showNapNotification(nap.start);
  }, 'Starten is mislukt.');

  // -- Afmelden -----------------------------------------------------------------

  DateTime _wakeAt() {
    final time = _wakeTime;
    return time != null
        ? _latest(time)
        : _now().subtract(Duration(minutes: _agoMinutes));
  }

  Future<void> _wake() async {
    final nap = _nap!;
    final end = _wakeAt();
    if (!end.isAfter(nap.start)) {
      _snack('Dat is voor het begin van je dutje (${_hm(nap.start)}).');
      return;
    }
    await _run(() async {
      await widget.api.updateNap(nap.id, end: end, clearAlarm: true);
      await widget.cancelAlarm();
      await FcmService.cancelNapNotification();
    }, 'Opslaan is mislukt.');
  }

  // -- Achteraf invullen en aanpassen ---------------------------------------------

  ({DateTime start, DateTime end})? _range() {
    final from = _from;
    final to = _to;
    if (from == null || to == null) return null;
    final base = widget.nap?.start ?? widget.date ?? _now();
    final start = DateTime(
      base.year,
      base.month,
      base.day,
      from.hour,
      from.minute,
    );
    var end = DateTime(base.year, base.month, base.day, to.hour, to.minute);
    // Een dutje over middernacht heen eindigt de volgende dag.
    if (!end.isAfter(start)) end = end.add(const Duration(days: 1));
    return (start: start, end: end);
  }

  Future<void> _saveRange() async {
    final range = _range();
    if (range == null) {
      _snack('Vul een begin en een eind in.');
      return;
    }
    if (range.end.isAfter(_now().add(const Duration(minutes: 1)))) {
      _snack('Het eind kan niet in de toekomst liggen.');
      return;
    }
    final nap = widget.nap;
    await _run(() async {
      if (nap == null) {
        await widget.api.createNap(start: range.start, end: range.end);
      } else {
        await widget.api.updateNap(nap.id, start: range.start, end: range.end);
      }
    }, 'Opslaan is mislukt.');
  }

  Future<void> _delete() async {
    final nap = _nap!;
    final sure = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Dutje verwijderen?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Verwijderen'),
          ),
        ],
      ),
    );
    if (sure != true) return;
    await _run(() async {
      await widget.api.deleteNap(nap.id);
      if (nap.open) {
        await widget.cancelAlarm();
        await FcmService.cancelNapNotification();
      }
    }, 'Verwijderen is mislukt.');
  }

  // -- Opbouw -------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final nap = _nap;
    return Scaffold(
      appBar: AppBar(title: const Text('Dutje')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!))
          : ListView(
              padding: const EdgeInsets.all(16),
              children: nap == null
                  ? _startView(context)
                  : nap.open
                  ? _wakeView(context, nap)
                  : _editView(context, nap),
            ),
    );
  }

  TextStyle _muted(BuildContext context) =>
      TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant);

  Widget _bigButton(String label, VoidCallback? onPressed) => FilledButton(
    onPressed: _busy ? null : onPressed,
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Text(label),
    ),
  );

  List<Widget> _startView(BuildContext context) => [
    Text('Nu gaan slapen', style: Theme.of(context).textTheme.titleMedium),
    const SizedBox(height: 12),
    Text('Wekker', style: _muted(context)),
    const SizedBox(height: 6),
    Wrap(
      spacing: 8,
      children: [
        for (final minutes in _alarmChoices)
          ChoiceChip(
            label: Text(
              minutes == null
                  ? 'Geen'
                  : minutes == 60
                  ? '1 uur'
                  : '$minutes min',
            ),
            selected: _alarmTime == null && _alarmMinutes == minutes,
            onSelected: _busy
                ? null
                : (_) => setState(() {
                    _alarmMinutes = minutes;
                    _alarmTime = null;
                  }),
          ),
        ChoiceChip(
          label: Text(
            _alarmTime == null
                ? 'Eigen tijd'
                : 'Om ${_alarmTime!.format(context)}',
          ),
          selected: _alarmTime != null,
          onSelected: _busy
              ? null
              : (_) async {
                  final time = await _pick(
                    TimeOfDay.fromDateTime(
                      _now().add(const Duration(minutes: 30)),
                    ),
                  );
                  if (time != null) setState(() => _alarmTime = time);
                },
        ),
      ],
    ),
    const SizedBox(height: 16),
    _bigButton('Start dutje', _start),
    const SizedBox(height: 28),
    const Divider(),
    const SizedBox(height: 12),
    Text('Achteraf invullen', style: Theme.of(context).textTheme.titleMedium),
    const SizedBox(height: 12),
    _rangeRow(context),
    const SizedBox(height: 16),
    OutlinedButton(
      onPressed: _busy ? null : _saveRange,
      child: const Text('Dutje opslaan'),
    ),
  ];

  List<Widget> _wakeView(BuildContext context, Nap nap) => [
    Text(
      'Dutje sinds ${_hm(nap.start)}',
      style: Theme.of(context).textTheme.titleLarge,
    ),
    if (nap.alarmAt != null)
      Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Text('Wekker om ${_hm(nap.alarmAt!)}', style: _muted(context)),
      ),
    const SizedBox(height: 20),
    Text('Wanneer werd je wakker?', style: _muted(context)),
    const SizedBox(height: 6),
    Wrap(
      spacing: 8,
      children: [
        for (final minutes in _agoChoices)
          ChoiceChip(
            label: Text(minutes == 0 ? 'Nu' : '$minutes min geleden'),
            selected: _wakeTime == null && _agoMinutes == minutes,
            onSelected: _busy
                ? null
                : (_) => setState(() {
                    _agoMinutes = minutes;
                    _wakeTime = null;
                  }),
          ),
        ChoiceChip(
          label: Text(
            _wakeTime == null
                ? 'Eigen tijd'
                : 'Om ${_wakeTime!.format(context)}',
          ),
          selected: _wakeTime != null,
          onSelected: _busy
              ? null
              : (_) async {
                  final time = await _pick(TimeOfDay.fromDateTime(_now()));
                  if (time != null) setState(() => _wakeTime = time);
                },
        ),
      ],
    ),
    const SizedBox(height: 16),
    _bigButton('Ik ben wakker', _wake),
    const SizedBox(height: 12),
    TextButton(
      onPressed: _busy ? null : _delete,
      child: const Text('Dutje verwijderen'),
    ),
  ];

  List<Widget> _editView(BuildContext context, Nap nap) => [
    if (nap.endEstimated)
      Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Text(
          'Het eind is geschat omdat je je niet had afgemeld. Pas het aan als het niet klopt.',
          style: _muted(context),
        ),
      ),
    _rangeRow(context),
    const SizedBox(height: 16),
    _bigButton('Opslaan', _saveRange),
    const SizedBox(height: 12),
    TextButton(
      onPressed: _busy ? null : _delete,
      child: const Text('Dutje verwijderen'),
    ),
  ];

  Widget _rangeRow(BuildContext context) => Row(
    children: [
      Expanded(
        child: _timeButton(
          context,
          'Begin',
          _from,
          (time) => setState(() => _from = time),
        ),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: _timeButton(
          context,
          'Eind',
          _to,
          (time) => setState(() => _to = time),
        ),
      ),
    ],
  );

  Widget _timeButton(
    BuildContext context,
    String label,
    TimeOfDay? value,
    ValueChanged<TimeOfDay> onPicked,
  ) => OutlinedButton(
    onPressed: _busy
        ? null
        : () async {
            final time = await _pick(value ?? TimeOfDay.fromDateTime(_now()));
            if (time != null) onPicked(time);
          },
    child: Text(value == null ? label : '$label ${value.format(context)}'),
  );
}
