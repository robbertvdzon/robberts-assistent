/// Eén meetwaarde uit Health Connect, los van het `health`-pakket zodat het optellen per dag
/// zonder telefoon te testen is.
class HcSample {
  const HcSample({
    required this.kind,
    required this.from,
    required this.to,
    this.value = 0,
    this.garmin = true,
    this.workoutType = '',
    this.workoutKcal,
    this.workoutDistanceM,
  });

  final HcKind kind;
  final DateTime from;
  final DateTime to;
  final double value;

  /// Of de waarde van Garmin Connect komt. De telefoon telt zelf ook stappen; die tellen niet mee.
  final bool garmin;
  final String workoutType;
  final int? workoutKcal;
  final int? workoutDistanceM;
}

enum HcKind {
  steps,
  distance,
  floors,
  totalKcal,
  restingHeartRate,
  sleepSession,
  sleepDeep,
  sleepLight,
  sleepRem,
  sleepAwake,
  workout,
}

String _date(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

bool _sameDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;

/// Maakt van losse meetwaarden één record per dag, klaar om naar de backend te sturen
/// (`POST /api/v1/activity/days`). Alleen Garmin-waarden tellen mee. Een meetwaarde hoort bij de
/// dag waarop hij begint; slaap hoort bij de dag waarop Robbert wakker werd.
List<Map<String, dynamic>> aggregateDays(
  List<HcSample> samples,
  List<DateTime> days,
) {
  final garmin = samples.where((s) => s.garmin).toList();
  return [for (final day in days) _aggregateDay(garmin, day)];
}

Map<String, dynamic> _aggregateDay(List<HcSample> samples, DateTime day) {
  Iterable<HcSample> startingToday(HcKind kind) =>
      samples.where((s) => s.kind == kind && _sameDay(s.from, day));
  int? sum(HcKind kind) {
    final values = startingToday(kind).toList();
    if (values.isEmpty) return null;
    return values.fold<double>(0, (total, s) => total + s.value).round();
  }

  final resting = startingToday(HcKind.restingHeartRate).toList()
    ..sort((a, b) => a.from.compareTo(b.from));

  // De langste slaapsessie die op deze dag eindigt; een dutje overdag wint het niet van de nacht.
  final sessions =
      samples
          .where((s) => s.kind == HcKind.sleepSession && _sameDay(s.to, day))
          .toList()
        ..sort(
          (a, b) => b.to.difference(b.from).compareTo(a.to.difference(a.from)),
        );
  Map<String, dynamic>? sleep;
  if (sessions.isNotEmpty) {
    final session = sessions.first;
    int stage(HcKind kind) => samples
        .where(
          (s) =>
              s.kind == kind &&
              !s.from.isBefore(session.from) &&
              !s.to.isAfter(session.to),
        )
        .fold<int>(0, (total, s) => total + s.to.difference(s.from).inMinutes);
    sleep = {
      'start': session.from.toUtc().toIso8601String(),
      'end': session.to.toUtc().toIso8601String(),
      'minutes': session.to.difference(session.from).inMinutes,
      'deepMinutes': stage(HcKind.sleepDeep),
      'lightMinutes': stage(HcKind.sleepLight),
      'remMinutes': stage(HcKind.sleepRem),
      'awakeMinutes': stage(HcKind.sleepAwake),
    };
  }

  final workouts = startingToday(HcKind.workout).toList()
    ..sort((a, b) => a.from.compareTo(b.from));

  return {
    'date': _date(day),
    'steps': sum(HcKind.steps),
    'totalKcal': sum(HcKind.totalKcal),
    'distanceM': sum(HcKind.distance),
    'floors': sum(HcKind.floors),
    'restingHeartRate': resting.isEmpty ? null : resting.last.value.round(),
    'sleep': sleep,
    'workouts': [
      for (final w in workouts)
        {
          'start': w.from.toUtc().toIso8601String(),
          'end': w.to.toUtc().toIso8601String(),
          'type': w.workoutType,
          'kcal': w.workoutKcal,
          'distanceM': w.workoutDistanceM,
        },
    ],
  };
}
