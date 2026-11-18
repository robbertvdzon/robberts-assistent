import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/health_aggregate.dart';

void main() {
  final day = DateTime(2026, 11, 17);
  final before = DateTime(2026, 11, 16);

  HcSample at(
    HcKind kind,
    int hour,
    double value, {
    bool garmin = true,
    int minutes = 1,
    DateTime? on,
  }) {
    final from = DateTime(
      (on ?? day).year,
      (on ?? day).month,
      (on ?? day).day,
      hour,
    );
    return HcSample(
      kind: kind,
      from: from,
      to: from.add(Duration(minutes: minutes)),
      value: value,
      garmin: garmin,
    );
  }

  test('telt alleen Garmin-stappen, niet die van de telefoon zelf', () {
    final result = aggregateDays(
      [
        at(HcKind.steps, 8, 3000),
        at(HcKind.steps, 12, 1609),
        at(HcKind.steps, 8, 5014, garmin: false),
        at(HcKind.steps, 9, 9999, on: before),
      ],
      [day],
    ).single;

    expect(result['date'], '2026-11-17');
    expect(result['steps'], 4609);
  });

  test('verbruik is de som van de blokken die op die dag beginnen', () {
    final result = aggregateDays(
      [
        at(HcKind.totalKcal, 0, 1519),
        at(HcKind.totalKcal, 19, 654),
        at(HcKind.totalKcal, 20, 312),
        at(HcKind.totalKcal, 0, 2120, on: before),
      ],
      [before, day],
    );

    expect(result[0]['totalKcal'], 2120);
    expect(result[1]['totalKcal'], 2485);
  });

  test(
    'slaap hoort bij de dag van wakker worden, met de fases binnen de sessie',
    () {
      final start = DateTime(2026, 11, 16, 23, 8);
      final end = DateTime(2026, 11, 17, 5, 48);
      HcSample stage(HcKind kind, DateTime from, int minutes) => HcSample(
        kind: kind,
        from: from,
        to: from.add(Duration(minutes: minutes)),
      );
      final samples = [
        HcSample(kind: HcKind.sleepSession, from: start, to: end),
        // Een dutje op dezelfde dag is korter en wint het niet van de nacht.
        HcSample(
          kind: HcKind.sleepSession,
          from: DateTime(2026, 11, 17, 13),
          to: DateTime(2026, 11, 17, 13, 30),
        ),
        stage(HcKind.sleepLight, start, 10),
        stage(HcKind.sleepDeep, DateTime(2026, 11, 16, 23, 18), 16),
        stage(HcKind.sleepDeep, DateTime(2026, 11, 17, 3, 33), 37),
        stage(HcKind.sleepAwake, DateTime(2026, 11, 17, 4, 47), 43),
        // Fase van een andere nacht telt niet mee.
        stage(HcKind.sleepDeep, DateTime(2026, 11, 15, 23, 7), 8),
      ];
      final result = aggregateDays(samples, [before, day]);

      expect(result[0]['sleep'], isNull);
      final sleep = result[1]['sleep'] as Map<String, dynamic>;
      expect(sleep['minutes'], 400);
      expect(sleep['deepMinutes'], 53);
      expect(sleep['lightMinutes'], 10);
      expect(sleep['awakeMinutes'], 43);
      expect(sleep['remMinutes'], 0);
      expect(sleep['end'], end.toUtc().toIso8601String());
    },
  );

  test('trainingen, rusthartslag en lege dagen', () {
    final result = aggregateDays(
      [
        HcSample(
          kind: HcKind.workout,
          from: DateTime(2026, 11, 17, 19),
          to: DateTime(2026, 11, 17, 20, 5),
          workoutType: 'BIKING',
          workoutKcal: 654,
        ),
        at(HcKind.restingHeartRate, 5, 53),
      ],
      [before, day],
    );

    expect(result[0]['steps'], isNull);
    expect(result[0]['totalKcal'], isNull);
    expect(result[0]['workouts'], isEmpty);
    expect(result[1]['restingHeartRate'], 53);
    final workout = (result[1]['workouts'] as List).single as Map;
    expect(workout['type'], 'BIKING');
    expect(workout['kcal'], 654);
    expect(workout['distanceM'], isNull);
  });

  test(
    'eerste beweging na een dutje: eerste echte stappen, van horloge of telefoon',
    () {
      final start = DateTime(2026, 11, 18, 13, 10);
      HcSample steps(
        int hour,
        int minute,
        double value, {
        bool garmin = true,
      }) => HcSample(
        kind: HcKind.steps,
        from: DateTime(2026, 11, 18, hour, minute),
        to: DateTime(2026, 11, 18, hour, minute + 1),
        value: value,
        garmin: garmin,
      );
      final samples = [
        steps(13, 5, 40), // voor het dutje
        steps(13, 11, 30), // nog aan het gaan liggen
        steps(13, 25, 2, garmin: false), // de telefoon die even bewoog
        steps(13, 52, 25, garmin: false),
        steps(13, 55, 30),
        HcSample(
          kind: HcKind.distance,
          from: DateTime(2026, 11, 18, 13, 20),
          to: DateTime(2026, 11, 18, 13, 21),
          value: 500,
        ),
      ];
      expect(
        firstMovementAfter(samples, start),
        DateTime(2026, 11, 18, 13, 52),
      );
      expect(firstMovementAfter(const [], start), isNull);
    },
  );
}
