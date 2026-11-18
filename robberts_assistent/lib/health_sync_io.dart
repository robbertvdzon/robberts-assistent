import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:health/health.dart';

import 'api_client.dart';
import 'health_aggregate.dart';
import 'health_sync.dart';

/// Aantal dagen terug dat bij elke sync opnieuw wordt uitgelezen. Garmin vult een dag achteraf
/// nog aan (het horloge synct niet continu), dus een paar dagen overlap is nodig.
const _daysBack = 7;

const _kinds = <HealthDataType, HcKind>{
  HealthDataType.STEPS: HcKind.steps,
  HealthDataType.DISTANCE_DELTA: HcKind.distance,
  HealthDataType.FLIGHTS_CLIMBED: HcKind.floors,
  HealthDataType.TOTAL_CALORIES_BURNED: HcKind.totalKcal,
  HealthDataType.RESTING_HEART_RATE: HcKind.restingHeartRate,
  HealthDataType.SLEEP_SESSION: HcKind.sleepSession,
  HealthDataType.SLEEP_DEEP: HcKind.sleepDeep,
  HealthDataType.SLEEP_LIGHT: HcKind.sleepLight,
  HealthDataType.SLEEP_REM: HcKind.sleepRem,
  HealthDataType.SLEEP_AWAKE: HcKind.sleepAwake,
  HealthDataType.WORKOUT: HcKind.workout,
};

/// Stappen en afstand komen ook van de telefoon zelf, in honderden regels per dag. Health Connect
/// geeft per aanvraag een begrensd aantal regels terug, dus deze twee worden per dag opgehaald.
const _perDay = {HealthDataType.STEPS, HealthDataType.DISTANCE_DELTA};

final _health = Health();
bool _configured = false;

List<HealthDataType> get _types => _kinds.keys.toList();
List<HealthDataAccess> get _read =>
    List.filled(_kinds.length, HealthDataAccess.READ);

Future<bool> _available() async {
  if (!Platform.isAndroid) return false;
  if (!_configured) {
    await _health.configure();
    _configured = true;
  }
  return await _health.getHealthConnectSdkStatus() ==
      HealthConnectSdkStatus.sdkAvailable;
}

Future<HealthSyncStatus> syncHealth(ApiClient api) async {
  try {
    if (!await _available()) return HealthSyncStatus.unsupported;
    if (await _health.hasPermissions(_types, permissions: _read) != true) {
      return HealthSyncStatus.needsPermission;
    }
    return await _readAndSend(api);
  } catch (e) {
    debugPrint('Health Connect-sync mislukt: $e');
    return HealthSyncStatus.failed;
  }
}

Future<HealthSyncStatus> requestHealthAccess(ApiClient api) async {
  try {
    if (!await _available()) return HealthSyncStatus.unsupported;
    final granted = await _health.requestAuthorization(
      _types,
      permissions: _read,
    );
    if (!granted) return HealthSyncStatus.needsPermission;
    return await _readAndSend(api);
  } catch (e) {
    debugPrint('Health Connect-toestemming mislukt: $e');
    return HealthSyncStatus.failed;
  }
}

Future<HealthSyncStatus> _readAndSend(ApiClient api) async {
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final days = [
    for (var i = _daysBack - 1; i >= 0; i--)
      DateTime(today.year, today.month, today.day - i),
  ];
  // Slaap die op de eerste dag eindigt, begon de avond ervoor: lees een dag extra terug.
  final windowStart = DateTime(
    days.first.year,
    days.first.month,
    days.first.day - 1,
  );

  final points = <HealthDataPoint>[];
  final wide = _types.where((t) => !_perDay.contains(t)).toList();
  points.addAll(
    await _health.getHealthDataFromTypes(
      types: wide,
      startTime: windowStart,
      endTime: now,
    ),
  );
  for (final day in days) {
    final next = DateTime(day.year, day.month, day.day + 1);
    points.addAll(
      await _health.getHealthDataFromTypes(
        types: _perDay.toList(),
        startTime: day,
        endTime: next.isAfter(now) ? now : next,
      ),
    );
  }

  final samples = [
    for (final p in points)
      if (_kinds[p.type] != null) _sample(p, _kinds[p.type]!),
  ];
  await api.syncActivityDays(aggregateDays(samples, days));
  await estimateNaps(api, samples);
  return HealthSyncStatus.synced;
}

HcSample _sample(HealthDataPoint p, HcKind kind) {
  final value = p.value;
  final garmin =
      p.sourceName.toLowerCase().contains('garmin') ||
      p.sourceId.toLowerCase().contains('garmin');
  if (value is WorkoutHealthValue) {
    return HcSample(
      kind: kind,
      from: p.dateFrom,
      to: p.dateTo,
      garmin: garmin,
      workoutType: value.workoutActivityType.name,
      workoutKcal: value.totalEnergyBurned,
      workoutDistanceM: value.totalDistance,
    );
  }
  return HcSample(
    kind: kind,
    from: p.dateFrom,
    to: p.dateTo,
    garmin: garmin,
    value: value is NumericHealthValue ? value.numericValue.toDouble() : 0,
  );
}
