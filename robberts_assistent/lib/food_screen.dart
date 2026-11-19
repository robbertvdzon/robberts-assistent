import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'api_client.dart';
import 'checkin_screen.dart';
import 'food_week_screen.dart';
import 'health_sync.dart';
import 'nap_screen.dart';

const _monthNames = [
  'jan', 'feb', 'mrt', 'apr', 'mei', 'jun',
  'jul', 'aug', 'sep', 'okt', 'nov', 'dec', //
];
const _dayNames = ['ma', 'di', 'wo', 'do', 'vr', 'za', 'zo'];

String _time(DateTime t) =>
    '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

String _dateLabel(DateTime d) {
  final today = DateUtils.dateOnly(DateTime.now());
  final day = DateUtils.dateOnly(d);
  if (day == today) return 'Vandaag';
  if (day == today.subtract(const Duration(days: 1))) return 'Gisteren';
  return '${_dayNames[d.weekday - 1]} ${d.day} ${_monthNames[d.month - 1]}';
}

/// Leest de gekozen bestanden in als bijlagen voor de backend.
Future<List<AssistantAttachment>> _toAttachments(List<XFile> files) async {
  final result = <AssistantAttachment>[];
  for (final file in files) {
    final bytes = await file.readAsBytes();
    final png = file.name.toLowerCase().endsWith('.png');
    result.add(
      AssistantAttachment(
        bytes: bytes,
        filename: file.name.isEmpty ? 'foto.jpg' : file.name,
        contentType: file.mimeType ?? (png ? 'image/png' : 'image/jpeg'),
      ),
    );
  }
  return result;
}

/// Dagoverzicht van het eetdagboek: totalen bovenaan, daaronder de dag als tijdlijn: slaap,
/// eetmomenten, check-ins, wandelingen en trainingen (van Garmin) en dutjes, op volgorde van tijd.
/// Zolang er een analyse loopt wordt de dag elke paar seconden opnieuw opgehaald.
class FoodScreen extends StatefulWidget {
  const FoodScreen({
    super.key,
    required this.api,
    this.pollInterval,
    this.healthSync = syncHealth,
    this.healthAccess = requestHealthAccess,
    this.reload,
  });

  final ApiClient api;

  /// Als het scherm als tab blijft bestaan: elke melding hierop haalt de dag opnieuw op.
  final Listenable? reload;

  /// Aanpasbaar voor tests; standaard 3 seconden.
  final Duration? pollInterval;

  /// Leest Garmin-gegevens uit Health Connect; vervangbaar in tests.
  final Future<HealthSyncStatus> Function(ApiClient api) healthSync;
  final Future<HealthSyncStatus> Function(ApiClient api) healthAccess;

  @override
  State<FoodScreen> createState() => _FoodScreenState();
}

class _FoodScreenState extends State<FoodScreen> {
  DateTime _date = DateUtils.dateOnly(DateTime.now());
  NutritionDay? _day;
  List<CheckIn> _checkIns = const [];
  NapDay _naps = const NapDay();
  ActivityDay? _activity;
  HealthSyncStatus? _health;
  String? _error;
  bool _loading = true;
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    _load();
    _syncHealth(widget.healthSync);
    widget.reload?.addListener(_reload);
  }

  void _reload() {
    if (mounted) _load(silent: true);
  }

  @override
  void dispose() {
    widget.reload?.removeListener(_reload);
    _poll?.cancel();
    super.dispose();
  }

  /// Haalt de laatste Garmin-dagen op de telefoon op en toont ze zodra ze bij de backend staan.
  /// Loopt los van het laden van het scherm: het eetdagboek wacht er niet op.
  Future<void> _syncHealth(
    Future<HealthSyncStatus> Function(ApiClient api) run,
  ) async {
    final status = await run(widget.api);
    if (!mounted) return;
    setState(() => _health = status);
    if (status == HealthSyncStatus.synced) _load(silent: true);
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent) setState(() => _loading = true);
    try {
      final day = await widget.api.getNutritionDay(_date);
      // Check-ins zijn bijzaak op dit scherm: lukt het ophalen niet, dan blijft de dag gewoon staan.
      final checkIns = await widget.api
          .getCheckIns(_date)
          .catchError((_) => const <CheckIn>[]);
      final activity = await widget.api
          .getActivityDay(_date)
          .catchError((_) => null);
      final naps = await widget.api
          .getNaps(_date)
          .catchError((_) => const NapDay());
      if (!mounted) return;
      setState(() {
        _day = day;
        _checkIns = checkIns;
        _naps = naps;
        _activity = activity;
        _error = null;
        _loading = false;
      });
      _poll?.cancel();
      if (day.meals.any((m) => m.analysing)) {
        _poll = Timer(
          widget.pollInterval ?? const Duration(seconds: 3),
          () => _load(silent: true),
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'Het eetdagboek kon niet worden geladen.';
        _loading = false;
      });
    }
  }

  void _shiftDay(int days) {
    setState(() => _date = _date.add(Duration(days: days)));
    _load();
  }

  Future<void> _capture() async {
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => MealCaptureScreen(api: widget.api)),
    );
    if (created == true) {
      setState(() => _date = DateUtils.dateOnly(DateTime.now()));
      _load();
    }
  }

  Future<void> _checkIn() async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => CheckInScreen(api: widget.api)),
    );
    if (saved == true) _load(silent: true);
  }

  /// Gewicht invoeren voor de getoonde dag.
  Future<void> _weight() async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _WeightSheet(api: widget.api, date: _date),
    );
    if (saved == true) _load(silent: true);
  }

  /// Dutje starten of afmelden (zonder [nap]), of een bestaand dutje aanpassen.
  Future<void> _nap([Nap? nap]) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => NapScreen(api: widget.api, nap: nap, date: _date),
      ),
    );
    if (changed == true) _load(silent: true);
  }

  /// Alles van deze dag op volgorde van tijd. Slaap staat op het moment van inslapen en komt
  /// daardoor bovenaan.
  List<Widget> _timeline() {
    final entries = <(DateTime, Widget)>[];
    final a = _activity;
    if (a?.sleepStart != null && a?.sleepEnd != null) {
      entries.add((
        a!.sleepStart!,
        _TimelineRow(
          icon: Icons.bedtime_outlined,
          time: _time(a.sleepStart!),
          text:
              'Geslapen tot ${_time(a.sleepEnd!)} · ${napDuration(a.sleepEnd!.difference(a.sleepStart!))}',
        ),
      ));
    }
    for (final w in a?.workouts ?? const <ActivityWorkout>[]) {
      final start = w.start;
      final end = w.end;
      if (start == null || end == null) continue;
      entries.add((
        start,
        _TimelineRow(
          icon: Icons.directions_walk,
          time: _time(start),
          text: [
            '${w.label.replaceFirstMapped(RegExp('^.'), (m) => m[0]!.toUpperCase())} tot ${_time(end)}',
            napDuration(end.difference(start)),
            if (w.kcal != null) '${w.kcal} kcal',
          ].join(' · '),
        ),
      ));
    }
    for (final nap in _naps.naps) {
      entries.add((
        nap.start,
        _TimelineRow(
          icon: Icons.airline_seat_flat_outlined,
          time: _time(nap.start),
          text: describeNap(nap),
          onTap: () => _nap(nap.open ? null : nap),
        ),
      ));
    }
    for (final c in _checkIns) {
      entries.add((
        c.at,
        _TimelineRow(
          icon: Icons.mood_outlined,
          time: _time(c.at),
          text: [describeCheckIn(c), if (c.note.isNotEmpty) c.note].join(' — '),
        ),
      ));
    }
    for (final meal in _day!.meals) {
      entries.add((
        meal.eatenAt,
        _MealCard(api: widget.api, meal: meal, onTap: () => _open(meal)),
      ));
    }
    entries.sort((x, y) => x.$1.compareTo(y.$1));
    return [for (final e in entries) e.$2];
  }

  Future<void> _open(Meal meal) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => MealDetailScreen(api: widget.api, mealId: meal.id),
      ),
    );
    _load(silent: true);
  }

  @override
  Widget build(BuildContext context) {
    final isToday = _date == DateUtils.dateOnly(DateTime.now());
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dagboek'),
        actions: [
          PopupMenuButton<String>(
            tooltip: 'Meer',
            onSelected: (choice) => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => switch (choice) {
                  'week' => FoodWeekScreen(api: widget.api),
                  'verbanden' => InsightsScreen(api: widget.api),
                  _ => FoodMemoryScreen(api: widget.api),
                },
              ),
            ),
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'week', child: Text('Deze week')),
              PopupMenuItem(value: 'verbanden', child: Text('Verbanden')),
              PopupMenuItem(
                value: 'geheugen',
                child: Text('Wat ik van je weet'),
              ),
            ],
          ),
        ],
      ),
      // Tekst is te selecteren en te kopiëren door lang te drukken.
      body: SelectionArea(
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            children: [
              Row(
                children: [
                  IconButton(
                    tooltip: 'Vorige dag',
                    icon: const Icon(Icons.chevron_left),
                    onPressed: () => _shiftDay(-1),
                  ),
                  Expanded(
                    child: Text(
                      _dateLabel(_date),
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  IconButton(
                    tooltip: 'Volgende dag',
                    icon: const Icon(Icons.chevron_right),
                    onPressed: isToday ? null : () => _shiftDay(1),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  _ActionButton(
                    icon: Icons.photo_camera_outlined,
                    label: 'Eten',
                    onTap: _capture,
                  ),
                  _ActionButton(
                    icon: Icons.mood_outlined,
                    label: 'Gevoel',
                    onTap: _checkIn,
                  ),
                  _ActionButton(
                    icon: Icons.airline_seat_flat_outlined,
                    label: 'Dutje',
                    onTap: _nap,
                  ),
                  _ActionButton(
                    icon: Icons.monitor_weight_outlined,
                    label: 'Gewicht',
                    onTap: _weight,
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (_loading && _day == null)
                const Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (_error != null && _day == null)
                Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      Text(_error!, textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      OutlinedButton(
                        onPressed: _load,
                        child: const Text('Opnieuw proberen'),
                      ),
                    ],
                  ),
                )
              else ...[
                _TotalsCard(
                  totals: _day!.totals,
                  activity: _activity,
                  today: isToday,
                  onConnect: _health == HealthSyncStatus.needsPermission
                      ? () => _syncHealth(widget.healthAccess)
                      : null,
                ),
                const SizedBox(height: 12),
                if (_timeline().isEmpty && _activity?.weightKg == null)
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      isToday
                          ? 'Nog niets vastgelegd vandaag. Tik op Eten om een foto te maken.'
                          : 'Niets vastgelegd op deze dag.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                // Een dutje dat nog loopt hoort bovenaan, ook als het op een andere dag begon.
                if (_naps.open != null &&
                    !_naps.naps.any((n) => n.id == _naps.open!.id))
                  _TimelineRow(
                    icon: Icons.airline_seat_flat_outlined,
                    time: _time(_naps.open!.start),
                    text: describeNap(_naps.open!),
                    onTap: _nap,
                  ),
                if (_activity?.weightKg != null)
                  _TimelineRow(
                    icon: Icons.monitor_weight_outlined,
                    time: '',
                    text:
                        'Gewicht ${_activity!.weightKg!.toStringAsFixed(1).replaceAll('.', ',')} kg',
                    onTap: _weight,
                  ),
                ..._timeline(),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _TotalsCard extends StatelessWidget {
  const _TotalsCard({
    required this.totals,
    this.activity,
    this.onConnect,
    this.today = false,
  });

  /// Vandaag is nog niet af: Garmin telt door tot middernacht.
  final bool today;

  final NutritionTotals totals;
  final ActivityDay? activity;

  /// Gezet als Health Connect er is maar de app nog geen leestoestemming heeft.
  final VoidCallback? onConnect;

  /// Stappen, trainingen en slaap in één regel, bv. "9.420 stappen · 45 min fietsen · 6 u 40 geslapen".
  String? get _activityLine {
    final a = activity;
    if (a == null) return null;
    String thousands(int n) => n.toString().replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+$)'),
      (m) => '${m[1]}.',
    );
    final parts = [
      if (a.steps != null) '${thousands(a.steps!)} stappen',
      for (final w in a.workouts) '${w.minutes} min ${w.label}',
      if (a.sleepMinutes != null)
        '${a.sleepMinutes! ~/ 60} u ${(a.sleepMinutes! % 60).toString().padLeft(2, '0')} geslapen',
    ];
    return parts.isEmpty ? null : parts.join(' · ');
  }

  @override
  Widget build(BuildContext context) {
    final muted = TextStyle(
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    );
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Expanded(child: Text('Gegeten', style: muted)),
                Text(
                  '${totals.kcal}',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(width: 4),
                Text('kcal', style: muted),
              ],
            ),
            if (activity?.totalKcal != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Expanded(
                      child: Text(
                        today
                            ? 'Verbruikt tot nu toe (Garmin)'
                            : 'Verbruikt (Garmin)',
                        style: muted,
                      ),
                    ),
                    Text(
                      '${activity!.totalKcal}',
                      style: Theme.of(context).textTheme.headlineMedium
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(width: 4),
                    Text('kcal', style: muted),
                  ],
                ),
              ),
            if (_activityLine != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(_activityLine!, style: muted),
              ),
            if (onConnect != null)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: onConnect,
                  icon: const Icon(Icons.watch_outlined),
                  label: const Text('Garmin koppelen'),
                ),
              ),
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                'Eiwit ${totals.proteinG.round()} g · vezels ${totals.fiberG.round()} g · '
                'groente ${totals.vegetableG} g · fruit ${totals.fruitG} g',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Een van de vier acties bovenaan het dagboek.
class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 3),
      child: OutlinedButton(
        onPressed: onTap,
        style: OutlinedButton.styleFrom(
          padding: const EdgeInsets.symmetric(vertical: 10),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon),
            const SizedBox(height: 4),
            Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
          ],
        ),
      ),
    ),
  );
}

/// Invoerblad voor het gewicht van één dag, met het vorige gewicht ter vergelijking.
class _WeightSheet extends StatefulWidget {
  const _WeightSheet({required this.api, required this.date});

  final ApiClient api;
  final DateTime date;

  @override
  State<_WeightSheet> createState() => _WeightSheetState();
}

class _WeightSheetState extends State<_WeightSheet> {
  final _controller = TextEditingController();
  String? _previous;
  String? _error;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _loadPrevious();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  /// Het laatst ingevulde gewicht; bijzaak, dus een fout laat het blad gewoon werken.
  Future<void> _loadPrevious() async {
    try {
      final weights = (await widget.api.getWeekOverview()).weights;
      if (!mounted || weights.isEmpty) return;
      final last = weights.last;
      setState(
        () => _previous =
            'Laatst ingevuld: ${last.weightKg.toStringAsFixed(1).replaceAll('.', ',')} kg '
            'op ${last.date.day} ${_monthNames[last.date.month - 1]}',
      );
    } catch (_) {}
  }

  Future<void> _save() async {
    final kg = double.tryParse(_controller.text.trim().replaceAll(',', '.'));
    if (kg == null || kg < 30 || kg > 250) {
      setState(
        () => _error = "Vul je gewicht in kilo's in, bijvoorbeeld 81,4.",
      );
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.api.setWeight(kg, date: widget.date);
      if (mounted) Navigator.of(context).pop(true);
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = 'Opslaan is mislukt.';
      });
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      16,
      16,
      16,
      16 + MediaQuery.of(context).viewInsets.bottom,
    ),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Gewicht ${_dateLabel(widget.date).toLowerCase()}',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        if (_previous != null)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              _previous!,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        const SizedBox(height: 12),
        TextField(
          controller: _controller,
          autofocus: true,
          enabled: !_saving,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(
            border: const OutlineInputBorder(),
            labelText: 'Gewicht (kg)',
            errorText: _error,
          ),
          onSubmitted: (_) => _save(),
        ),
        const SizedBox(height: 12),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: const Text('Opslaan'),
        ),
      ],
    ),
  );
}

/// Eén regel in de tijdlijn die geen eetmoment is: slaap, een training, een dutje of een check-in.
class _TimelineRow extends StatelessWidget {
  const _TimelineRow({
    required this.icon,
    required this.time,
    required this.text,
    this.onTap,
  });

  final IconData icon;
  final String time;
  final String text;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 20, color: scheme.onSurfaceVariant),
            const SizedBox(width: 10),
            SizedBox(
              width: 48,
              child: Text(
                time,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
            ),
            Expanded(child: Text(text)),
            if (onTap != null)
              Icon(
                Icons.chevron_right,
                size: 20,
                color: scheme.onSurfaceVariant,
              ),
          ],
        ),
      ),
    );
  }
}

class _MealCard extends StatelessWidget {
  const _MealCard({required this.api, required this.meal, required this.onTap});

  final ApiClient api;
  final Meal meal;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final muted = TextStyle(color: scheme.onSurfaceVariant);
    final String subtitle;
    if (meal.analysing) {
      subtitle = 'Bezig met bekijken…';
    } else if (meal.failed) {
      subtitle = 'Mislukt. Tik om het opnieuw te proberen.';
    } else {
      subtitle = meal.items.map((i) => i.name).join(', ');
    }
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              MealPhoto(
                api: api,
                photoId: meal.photoIds.isEmpty ? null : meal.photoIds.first,
                size: 52,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            meal.title.isEmpty ? 'Eetmoment' : meal.title,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(_time(meal.eatenAt), style: muted),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: muted,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (meal.proposal)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          'Nog niet bevestigd',
                          style: TextStyle(
                            color: scheme.primary,
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              if (meal.analysing)
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else if (!meal.failed)
                Text(
                  '${meal.kcal}',
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Miniatuur van een etensfoto; haalt de bytes op met het sessie-token (geen kale netwerk-URL).
class MealPhoto extends StatefulWidget {
  const MealPhoto({
    super.key,
    required this.api,
    required this.photoId,
    required this.size,
  });

  final ApiClient api;
  final String? photoId;
  final double size;

  @override
  State<MealPhoto> createState() => _MealPhotoState();
}

class _MealPhotoState extends State<MealPhoto> {
  static final _cache = <String, Uint8List>{};
  Uint8List? _bytes;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  @override
  void didUpdateWidget(MealPhoto old) {
    super.didUpdateWidget(old);
    if (old.photoId != widget.photoId) _fetch();
  }

  Future<void> _fetch() async {
    final id = widget.photoId;
    if (id == null) return;
    final cached = _cache[id];
    if (cached != null) {
      setState(() => _bytes = cached);
      return;
    }
    try {
      final bytes = await widget.api.fetchMealPhoto(id);
      _cache[id] = bytes;
      if (mounted && widget.photoId == id) setState(() => _bytes = bytes);
    } catch (_) {
      // Een ontbrekende foto mag het overzicht niet breken; het vlak blijft dan leeg.
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: Container(
        width: widget.size,
        height: widget.size,
        color: scheme.outline,
        child: _bytes != null
            ? Image.memory(
                _bytes!,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => const SizedBox.shrink(),
              )
            : Icon(
                widget.photoId == null
                    ? Icons.notes_outlined
                    : Icons.restaurant_outlined,
                color: scheme.onSurfaceVariant,
                size: widget.size * 0.45,
              ),
      ),
    );
  }
}

/// Vastleggen: foto's verzamelen (bord, tweede bord, etiket, recept), eventueel een notitie, en
/// dan pas insturen — zo komt er één voorstel in plaats van één per foto.
class MealCaptureScreen extends StatefulWidget {
  const MealCaptureScreen({
    super.key,
    required this.api,
    this.openCameraOnStart = true,
  });

  final ApiClient api;
  final bool openCameraOnStart;

  @override
  State<MealCaptureScreen> createState() => _MealCaptureScreenState();
}

class _MealCaptureScreenState extends State<MealCaptureScreen> {
  final _picker = ImagePicker();
  final _note = TextEditingController();
  final _photos = <AssistantAttachment>[];
  bool _sending = false;
  int? _hunger;
  int? _energy;

  @override
  void initState() {
    super.initState();
    if (widget.openCameraOnStart) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _fromCamera());
    }
  }

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  Future<void> _fromCamera() async {
    try {
      // 1600 px is scherp genoeg voor herkenning en klein genoeg voor mobiel netwerk.
      final file = await _picker.pickImage(
        source: ImageSource.camera,
        imageQuality: 80,
        maxWidth: 1600,
        maxHeight: 1600,
      );
      if (file != null) await _add([file]);
    } catch (_) {
      _snack('De camera kon niet worden geopend.');
    }
  }

  Future<void> _fromGallery() async {
    try {
      final files = await _picker.pickMultiImage(
        imageQuality: 80,
        maxWidth: 1600,
        maxHeight: 1600,
      );
      if (files.isNotEmpty) await _add(files);
    } catch (_) {
      _snack('De foto\'s konden niet worden geopend.');
    }
  }

  Future<void> _add(List<XFile> files) async {
    final attachments = await _toAttachments(files);
    if (mounted) setState(() => _photos.addAll(attachments));
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _send() async {
    setState(() => _sending = true);
    try {
      final meal = await widget.api.createMeal(
        photos: _photos,
        note: _note.text.trim(),
      );
      if (_hunger != null || _energy != null) {
        // Het gevoel net voor het eten hoort bij dit eetmoment. Mislukt het bewaren, dan is het
        // eetmoment zelf al binnen; dat laten we niet alsnog falen.
        await widget.api
            .recordCheckIn(
              hunger: _hunger,
              energy: _energy,
              source: 'FOTO',
              mealId: meal.id,
            )
            .then<void>((_) {})
            .catchError((_) {});
      }
      if (mounted) Navigator.of(context).pop(true);
    } catch (_) {
      if (!mounted) return;
      setState(() => _sending = false);
      _snack('Versturen is mislukt. Probeer het opnieuw.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final canSend =
        !_sending && (_photos.isNotEmpty || _note.text.trim().isNotEmpty);
    final count = _photos.length;
    return Scaffold(
      appBar: AppBar(title: const Text('Wat eet je?')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Foto van je bord, en als je wilt ook van het etiket, het recept of een tweede bord. '
            'Een link naar een recept kun je in het tekstvak plakken. '
            'Bestek of je hand in beeld helpt bij de portie.',
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 16),
          if (_photos.isNotEmpty)
            SizedBox(
              height: 110,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: _photos.length,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (context, i) => Stack(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: Image.memory(
                        _photos[i].bytes,
                        width: 110,
                        height: 110,
                        fit: BoxFit.cover,
                      ),
                    ),
                    Positioned(
                      top: 0,
                      right: 0,
                      child: IconButton(
                        tooltip: 'Foto weghalen',
                        visualDensity: VisualDensity.compact,
                        style: IconButton.styleFrom(
                          backgroundColor: Colors.black54,
                          foregroundColor: Colors.white,
                        ),
                        icon: const Icon(Icons.close, size: 16),
                        onPressed: _sending
                            ? null
                            : () => setState(() => _photos.removeAt(i)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: _sending ? null : _fromCamera,
                icon: const Icon(Icons.photo_camera_outlined),
                label: Text(count == 0 ? 'Foto maken' : 'Nog een foto'),
              ),
              OutlinedButton.icon(
                onPressed: _sending ? null : _fromGallery,
                icon: const Icon(Icons.photo_library_outlined),
                label: const Text('Uit galerij'),
              ),
            ],
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _note,
            enabled: !_sending,
            minLines: 2,
            maxLines: 5,
            textCapitalization: TextCapitalization.sentences,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Iets erbij zeggen (hoeft niet)',
              hintText:
                  'Bijv. "de helft opgegeten", "60 gram havermout gewogen" of alleen tekst zonder foto',
            ),
          ),
          const SizedBox(height: 20),
          Text(
            'Hoe voelde je je net voor het eten? (hoeft niet)',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 10),
          CheckInPicker(
            hunger: _hunger,
            energy: _energy,
            enabled: !_sending,
            onHunger: (v) => setState(() => _hunger = v),
            onEnergy: (v) => setState(() => _energy = v),
          ),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: canSend ? _send : null,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(
                _sending
                    ? 'Versturen…'
                    : count == 0
                    ? 'Klaar'
                    : 'Klaar · $count foto${count == 1 ? '' : '\'s'}',
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Eén eetmoment: het voorstel van de assistent, met Klopt en Verbeteren. Onderdelen zijn aan te
/// tikken om hoeveelheid, naam of calorieën te wijzigen; verbeteren kan ook in gewone woorden.
class MealDetailScreen extends StatefulWidget {
  const MealDetailScreen({
    super.key,
    required this.api,
    required this.mealId,
    this.pollInterval,
  });

  final ApiClient api;
  final String mealId;
  final Duration? pollInterval;

  @override
  State<MealDetailScreen> createState() => _MealDetailScreenState();
}

class _MealDetailScreenState extends State<MealDetailScreen> {
  final _picker = ImagePicker();
  final _correction = TextEditingController();
  Meal? _meal;

  /// De onderdelen zoals ze nu op het scherm staan; wijkt af van [_meal] zodra er iets is aangepast.
  List<MealItem> _items = const [];
  bool _edited = false;
  bool _busy = false;
  String? _error;
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _poll?.cancel();
    _correction.dispose();
    super.dispose();
  }

  void _apply(Meal meal) {
    setState(() {
      _meal = meal;
      _items = List.of(meal.items);
      _edited = false;
      _busy = false;
      _error = null;
    });
    _poll?.cancel();
    // Blijven ophalen zolang de analyse loopt, en nog even na het bevestigen voor "Onthouden".
    if (meal.analysing) {
      _poll = Timer(widget.pollInterval ?? const Duration(seconds: 3), _load);
    }
  }

  Future<void> _load() async {
    try {
      final meal = await widget.api.getMeal(widget.mealId);
      if (mounted) _apply(meal);
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Dit eetmoment kon niet worden geladen.');
      }
    }
  }

  Future<void> _run(Future<Meal> Function() action, String failure) async {
    setState(() => _busy = true);
    try {
      final meal = await action();
      if (mounted) _apply(meal);
    } catch (_) {
      if (!mounted) return;
      setState(() => _busy = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(failure)));
    }
  }

  Future<void> _confirm() async {
    await _run(
      () =>
          widget.api.confirmMeal(widget.mealId, items: _edited ? _items : null),
      'Opslaan is mislukt.',
    );
    // De leerstap loopt op de achtergrond; even later nog één keer kijken wat hij onthield.
    if (mounted && _meal?.confirmed == true) {
      _poll = Timer(const Duration(seconds: 20), _load);
    }
  }

  Future<void> _sendCorrection() async {
    final text = _correction.text.trim();
    if (text.isEmpty) return;
    _correction.clear();
    FocusScope.of(context).unfocus();
    await _run(
      () => widget.api.correctMeal(widget.mealId, text),
      'Versturen is mislukt.',
    );
  }

  Future<void> _addPhoto() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Foto maken'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Uit galerij kiezen'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    final file = await _picker.pickImage(
      source: source,
      imageQuality: 80,
      maxWidth: 1600,
      maxHeight: 1600,
    );
    if (file == null) return;
    final photos = await _toAttachments([file]);
    await _run(
      () => widget.api.addMealPhotos(widget.mealId, photos: photos),
      'De foto kon niet worden toegevoegd.',
    );
  }

  Future<void> _delete() async {
    final sure = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Eetmoment verwijderen?'),
        content: const Text('De foto\'s en het voorstel worden weggegooid.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuleren'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Verwijderen'),
          ),
        ],
      ),
    );
    if (sure != true) return;
    try {
      await widget.api.deleteMeal(widget.mealId);
      if (mounted) Navigator.of(context).pop();
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Verwijderen is mislukt.')));
    }
  }

  Future<void> _editItem(int? index) async {
    final result = await showModalBottomSheet<_ItemEdit>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _ItemSheet(item: index == null ? null : _items[index]),
    );
    if (result == null) return;
    setState(() {
      if (result.delete) {
        if (index != null) _items.removeAt(index);
      } else if (index == null) {
        _items.add(result.item!);
      } else {
        _items[index] = result.item!;
      }
      _edited = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    final meal = _meal;
    final scheme = Theme.of(context).colorScheme;
    final muted = TextStyle(color: scheme.onSurfaceVariant);
    return Scaffold(
      appBar: AppBar(
        title: Text(
          meal == null
              ? 'Eetmoment'
              : '${meal.title.isEmpty ? 'Eetmoment' : meal.title} · ${_time(meal.eatenAt)}',
        ),
        actions: [
          if (meal != null)
            IconButton(
              tooltip: 'Verwijderen',
              icon: const Icon(Icons.delete_outline),
              onPressed: _busy ? null : _delete,
            ),
        ],
      ),
      // Tekst is te selecteren en te kopiëren door lang te drukken.
      body: SelectionArea(
        child: meal == null
            ? Center(
                child: _error != null
                    ? Text(_error!)
                    : const CircularProgressIndicator(),
              )
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (meal.photoIds.isNotEmpty)
                    SizedBox(
                      height: 120,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: meal.photoIds.length,
                        separatorBuilder: (_, _) => const SizedBox(width: 8),
                        itemBuilder: (_, i) => MealPhoto(
                          api: widget.api,
                          photoId: meal.photoIds[i],
                          size: 120,
                        ),
                      ),
                    ),
                  if (meal.note.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 12),
                      child: Text('"${meal.note}"', style: muted),
                    ),
                  const SizedBox(height: 16),
                  if (meal.analysing) ...[
                    const Center(child: CircularProgressIndicator()),
                    const SizedBox(height: 12),
                    Text(
                      'Ik bekijk wat je eet. Dit duurt meestal een halve minuut; je mag dit scherm sluiten.',
                      textAlign: TextAlign.center,
                      style: muted,
                    ),
                  ] else if (meal.failed) ...[
                    Text(
                      'Het bekijken is mislukt.',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    if (meal.error.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(meal.error, style: muted),
                      ),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: _busy
                          ? null
                          : () => _run(
                              () => widget.api.retryMeal(widget.mealId),
                              'Opnieuw proberen is mislukt.',
                            ),
                      child: const Text('Opnieuw proberen'),
                    ),
                  ] else ...[
                    Text(
                      meal.confirmed && !_edited
                          ? 'Dit staat in je dagboek:'
                          : 'Volgens mij is dit:',
                      style: muted,
                    ),
                    const SizedBox(height: 8),
                    Card(
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        children: [
                          for (var i = 0; i < _items.length; i++)
                            _ItemRow(
                              item: _items[i],
                              onTap: _busy ? null : () => _editItem(i),
                            ),
                          ListTile(
                            dense: true,
                            leading: const Icon(Icons.add),
                            title: const Text('Iets toevoegen'),
                            onTap: _busy ? null : () => _editItem(null),
                          ),
                        ],
                      ),
                    ),
                    if (meal.remark.isNotEmpty && !_edited)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Text(meal.remark, style: muted),
                      ),
                    const SizedBox(height: 12),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.baseline,
                      textBaseline: TextBaseline.alphabetic,
                      children: [
                        Expanded(child: Text('Samen', style: muted)),
                        Text(
                          '${_items.fold<int>(0, (sum, i) => sum + i.kcal)} kcal',
                          style: Theme.of(context).textTheme.headlineSmall
                              ?.copyWith(fontWeight: FontWeight.w700),
                        ),
                      ],
                    ),
                    if (meal.learned.isNotEmpty && !_edited)
                      Container(
                        margin: const EdgeInsets.only(top: 12),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: scheme.primary.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Onthouden',
                              style: TextStyle(fontWeight: FontWeight.w600),
                            ),
                            const SizedBox(height: 2),
                            Text(meal.learned),
                          ],
                        ),
                      ),
                    const SizedBox(height: 16),
                    if (_edited)
                      FilledButton(
                        onPressed: _busy ? null : _confirm,
                        child: const Padding(
                          padding: EdgeInsets.symmetric(vertical: 12),
                          child: Text('Opslaan'),
                        ),
                      )
                    else if (meal.proposal)
                      FilledButton(
                        onPressed: _busy ? null : _confirm,
                        child: const Padding(
                          padding: EdgeInsets.symmetric(vertical: 12),
                          child: Text('Klopt'),
                        ),
                      ),
                    const SizedBox(height: 20),
                    Text('Klopt er iets niet?', style: muted),
                    const SizedBox(height: 8),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _correction,
                            enabled: !_busy,
                            minLines: 1,
                            maxLines: 4,
                            textCapitalization: TextCapitalization.sentences,
                            decoration: const InputDecoration(
                              border: OutlineInputBorder(),
                              hintText:
                                  'Zeg het gewoon: "het was kwark en er zat geen jus bij"',
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton.filled(
                          tooltip: 'Versturen',
                          onPressed: _busy ? null : _sendCorrection,
                          icon: const Icon(Icons.send),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: _busy ? null : _addPhoto,
                      icon: const Icon(Icons.add_a_photo_outlined),
                      label: const Text(
                        'Foto toevoegen (tweede bord, etiket, recept)',
                      ),
                    ),
                  ],
                ],
              ),
      ),
    );
  }
}

class _ItemRow extends StatelessWidget {
  const _ItemRow({required this.item, required this.onTap});

  final MealItem item;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final String? tag = item.weighed
        ? 'gewogen'
        : item.basis == 'ETIKET'
        ? 'etiket'
        : item.basis == 'RECEPT'
        ? 'recept'
        : item.uncertain
        ? 'twijfel'
        : null;
    return ListTile(
      onTap: onTap,
      title: Text(item.name),
      subtitle: Row(
        children: [
          Text('${item.grams} g'),
          if (tag != null) ...[
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                color:
                    (item.uncertain && !item.weighed
                            ? const Color(0xFFB7791F)
                            : scheme.primary)
                        .withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(99),
              ),
              child: Text(
                tag,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: item.uncertain && !item.weighed
                      ? const Color(0xFF8A5A10)
                      : scheme.primary,
                ),
              ),
            ),
          ],
        ],
      ),
      trailing: Text(
        '${item.kcal}',
        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
      ),
    );
  }
}

class _ItemEdit {
  const _ItemEdit.save(MealItem this.item) : delete = false;
  const _ItemEdit.remove() : item = null, delete = true;

  final MealItem? item;
  final bool delete;
}

/// Onderdeel wijzigen: hoeveelheid (snel via ½ × enz. of exact in grammen, eventueel gewogen),
/// naam en calorieën. Bij een andere hoeveelheid schalen alle voedingswaarden mee.
class _ItemSheet extends StatefulWidget {
  const _ItemSheet({required this.item});

  final MealItem? item;

  @override
  State<_ItemSheet> createState() => _ItemSheetState();
}

class _ItemSheetState extends State<_ItemSheet> {
  late final _name = TextEditingController(text: widget.item?.name ?? '');
  late final _grams = TextEditingController(
    text: widget.item == null ? '' : '${widget.item!.grams}',
  );
  late final _kcal = TextEditingController(
    text: widget.item == null ? '' : '${widget.item!.kcal}',
  );
  late bool _weighed = widget.item?.weighed ?? false;

  @override
  void dispose() {
    _name.dispose();
    _grams.dispose();
    _kcal.dispose();
    super.dispose();
  }

  /// Calorieën volgen de grammen zolang het om hetzelfde product gaat.
  void _gramsChanged(String value) {
    final original = widget.item;
    final grams = int.tryParse(value);
    if (original == null || grams == null || original.grams == 0) return;
    setState(() {
      _kcal.text = '${(original.kcal * grams / original.grams).round()}';
    });
  }

  void _scale(double factor) {
    final original = widget.item;
    if (original == null) return;
    final grams = (original.grams * factor).round();
    _grams.text = '$grams';
    _gramsChanged('$grams');
  }

  void _save() {
    final name = _name.text.trim();
    final grams = int.tryParse(_grams.text.trim()) ?? 0;
    final kcal = int.tryParse(_kcal.text.trim()) ?? 0;
    if (name.isEmpty) return;
    final original = widget.item;
    final MealItem item;
    if (original == null) {
      item = MealItem(
        name: name,
        grams: grams,
        kcal: kcal,
        basis: _weighed ? 'GEWOGEN' : 'GESCHAT',
      );
    } else {
      item = original
          .withGrams(grams, weighed: _weighed)
          .copyWith(name: name, kcal: kcal);
    }
    Navigator.pop(context, _ItemEdit.save(item));
  }

  @override
  Widget build(BuildContext context) {
    final isNew = widget.item == null;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        24 +
            MediaQuery.of(context).viewInsets.bottom +
            MediaQuery.of(context).viewPadding.bottom,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              isNew ? 'Iets toevoegen' : 'Aanpassen',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _name,
              autofocus: isNew,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                labelText: 'Wat is het?',
              ),
            ),
            const SizedBox(height: 12),
            if (!isNew) ...[
              Wrap(
                spacing: 8,
                children: [
                  for (final (label, factor) in const [
                    ('½ ×', 0.5),
                    ('¾ ×', 0.75),
                    ('1¼ ×', 1.25),
                    ('1½ ×', 1.5),
                    ('2 ×', 2.0),
                  ])
                    ActionChip(
                      label: Text(label),
                      onPressed: () => _scale(factor),
                    ),
                ],
              ),
              const SizedBox(height: 12),
            ],
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _grams,
                    keyboardType: TextInputType.number,
                    onChanged: _gramsChanged,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      labelText: 'Gram',
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _kcal,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      labelText: 'kcal',
                    ),
                  ),
                ),
              ],
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Gewogen'),
              subtitle: const Text(
                'Het gewicht is gemeten; dit wordt een referentie.',
              ),
              value: _weighed,
              onChanged: (v) => setState(() => _weighed = v),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (!isNew)
                  TextButton.icon(
                    onPressed: () =>
                        Navigator.pop(context, const _ItemEdit.remove()),
                    icon: const Icon(Icons.delete_outline),
                    label: const Text('Weghalen'),
                  ),
                const Spacer(),
                FilledButton(onPressed: _save, child: const Text('Opslaan')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Wat de assistent over je eten heeft geleerd: leesbaar en aan te passen.
class FoodMemoryScreen extends StatefulWidget {
  const FoodMemoryScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<FoodMemoryScreen> createState() => _FoodMemoryScreenState();
}

class _FoodMemoryScreenState extends State<FoodMemoryScreen> {
  final _text = TextEditingController();
  bool _loading = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final text = await widget.api.getFoodMemory();
      if (!mounted) return;
      setState(() {
        _text.text = text;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Dit kon niet worden geladen.';
        _loading = false;
      });
    }
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await widget.api.saveFoodMemory(_text.text);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Opgeslagen')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Opslaan is mislukt.')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Wat ik van je weet'),
        actions: [
          TextButton(
            onPressed: _loading || _saving || _error != null ? null : _save,
            child: const Text('Opslaan'),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(child: Text(_error!))
          : Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Vuistregels en vaste gerechten die ik uit je verbeteringen heb gehaald. '
                    'Ze gaan mee bij elke nieuwe foto. Je mag ze aanpassen of weghalen.',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: TextField(
                      controller: _text,
                      expands: true,
                      maxLines: null,
                      textAlignVertical: TextAlignVertical.top,
                      decoration: const InputDecoration(
                        border: OutlineInputBorder(),
                        hintText:
                            'Nog niets. Dit vult zich vanzelf als je voorstellen bevestigt of verbetert.',
                      ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
