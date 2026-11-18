import 'api_client.dart';
import 'health_aggregate.dart';
import 'health_sync_stub.dart'
    if (dart.library.io) 'health_sync_io.dart'
    as impl;

/// Uitkomst van een poging om Garmin-gegevens uit Health Connect naar de backend te brengen.
enum HealthSyncStatus {
  /// Dit apparaat heeft geen Health Connect (web, of geen Android).
  unsupported,

  /// Health Connect is er, maar de app heeft nog geen toestemming om te lezen.
  needsPermission,

  /// De laatste dagen zijn uitgelezen en naar de backend gestuurd.
  synced,

  /// Uitlezen of versturen is mislukt.
  failed,
}

/// Leest de laatste dagen uit Health Connect en stuurt ze naar de backend. Vraagt nooit zelf om
/// toestemming: ontbreekt die, dan is de uitkomst [HealthSyncStatus.needsPermission].
Future<HealthSyncStatus> syncHealth(ApiClient api) async {
  final status = await impl.syncHealth(api);
  // Zonder Health Connect kan de backend het eind van een dutje nog steeds schatten uit wat er
  // in de app is vastgelegd.
  if (status != HealthSyncStatus.synced) await estimateNaps(api);
  return status;
}

/// Laat de backend het eind schatten van dutjes waarbij Robbert zich niet heeft afgemeld. Uit
/// [samples] (de stappen die net uit Health Connect zijn gelezen) komt per dutje de eerste minuut
/// waarin hij weer liep. Mag nooit iets breken.
Future<void> estimateNaps(
  ApiClient api, [
  List<HcSample> samples = const [],
]) async {
  try {
    for (final nap in await api.getPendingNaps()) {
      await api.estimateNap(
        nap.id,
        movementAt: firstMovementAfter(samples, nap.start),
      );
    }
  } catch (_) {
    // Volgende sync opnieuw.
  }
}

/// Toont het toestemmingsscherm van Health Connect en synct daarna als het is toegestaan.
Future<HealthSyncStatus> requestHealthAccess(ApiClient api) =>
    impl.requestHealthAccess(api);
