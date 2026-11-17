import 'api_client.dart';
import 'health_sync.dart';

// Web: er is geen Health Connect. Het `health`-pakket wordt hier bewust niet geïmporteerd.
Future<HealthSyncStatus> syncHealth(ApiClient api) async =>
    HealthSyncStatus.unsupported;

Future<HealthSyncStatus> requestHealthAccess(ApiClient api) async =>
    HealthSyncStatus.unsupported;
