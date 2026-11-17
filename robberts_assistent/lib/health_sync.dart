import 'api_client.dart';
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
Future<HealthSyncStatus> syncHealth(ApiClient api) => impl.syncHealth(api);

/// Toont het toestemmingsscherm van Health Connect en synct daarna als het is toegestaan.
Future<HealthSyncStatus> requestHealthAccess(ApiClient api) =>
    impl.requestHealthAccess(api);
