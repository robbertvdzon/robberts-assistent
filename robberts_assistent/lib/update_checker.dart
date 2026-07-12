import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

/// Status van één van de drie apps t.o.v. de laatste GitHub Release.
class AppUpdateInfo {
  const AppUpdateInfo({
    required this.label,
    required this.packageName,
    required this.releaseTag,
    required this.installedVersionCode,
    this.latestVersionCode,
    this.downloadUrl,
    this.apkFileName,
    this.error,
  });

  final String label;
  final String packageName;
  final String releaseTag;

  /// `-1` als de app niet geïnstalleerd is op dit toestel.
  final int installedVersionCode;
  final int? latestVersionCode;
  final String? downloadUrl;
  final String? apkFileName;
  final String? error;

  bool get isInstalled => installedVersionCode >= 0;
  bool get updateAvailable =>
      error == null && latestVersionCode != null && latestVersionCode! > installedVersionCode;
}

/// Gegooid als [UpdateChecker.update] niet mag installeren — de UI moet dan naar
/// systeeminstellingen sturen (al gedaan via `requestInstallPermission`) en het opnieuw laten
/// proberen na terugkeer.
class UpdatePermissionRequiredException implements Exception {}

/// Checkt en installeert updates voor alle drie de apps (wind, robberts_assistent, notities) via
/// hun vaste GitHub Release-tags. Vergelijkt de `versionCode` die al op het toestel staat (native,
/// via MethodChannel — Flutter kan dat niet zelf opvragen voor andere packages) met het build-
/// nummer in de release-tekst (elke workflow schrijft "build ${{ github.run_number }}" in de
/// release-body, en run_number wordt sinds kort ook als --build-number/versionCode gebruikt, dus
/// die twee zijn per definitie gelijk voor een gegeven release).
class UpdateChecker {
  static const _channel = MethodChannel('nl.vdzon.robberts_assistent/updater');
  static const _repo = 'robbertvdzon/robberts-assistent';
  static const _apps = [
    (label: 'Wind', packageName: 'nl.vdzon.wind', tag: 'wind-latest'),
    (label: "Robbert's assistent", packageName: 'nl.vdzon.robberts_assistent', tag: 'robberts-assistent-latest'),
    (label: 'Notities', packageName: 'nl.vdzon.notities', tag: 'notities-latest'),
  ];

  Future<List<AppUpdateInfo>> checkAll() => Future.wait(_apps.map(_checkOne));

  Future<AppUpdateInfo> _checkOne(({String label, String packageName, String tag}) app) async {
    final installed = await _channel.invokeMethod<int>(
          'installedVersionCode',
          {'packageName': app.packageName},
        ) ??
        -1;
    try {
      final response = await http
          .get(
            Uri.parse('https://api.github.com/repos/$_repo/releases/tags/${app.tag}'),
            headers: {'Accept': 'application/vnd.github+json'},
          )
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        return AppUpdateInfo(
          label: app.label,
          packageName: app.packageName,
          releaseTag: app.tag,
          installedVersionCode: installed,
          error: 'GitHub gaf HTTP ${response.statusCode} terug.',
        );
      }
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final body = json['body'] as String? ?? '';
      final latest = int.tryParse(RegExp(r'build (\d+)').firstMatch(body)?.group(1) ?? '');
      final assets = (json['assets'] as List<dynamic>? ?? []).cast<Map<String, dynamic>>();
      final apkAsset = assets.cast<Map<String, dynamic>?>().firstWhere(
            (a) => (a?['name'] as String?)?.endsWith('.apk') ?? false,
            orElse: () => null,
          );
      return AppUpdateInfo(
        label: app.label,
        packageName: app.packageName,
        releaseTag: app.tag,
        installedVersionCode: installed,
        latestVersionCode: latest,
        downloadUrl: apkAsset?['browser_download_url'] as String?,
        apkFileName: apkAsset?['name'] as String?,
        error: latest == null ? 'Kon geen versienummer uit de release-tekst halen.' : null,
      );
    } catch (e) {
      return AppUpdateInfo(
        label: app.label,
        packageName: app.packageName,
        releaseTag: app.tag,
        installedVersionCode: installed,
        error: 'Kon niet ophalen: $e',
      );
    }
  }

  /// Downloadt en installeert [info]. Gooit [UpdatePermissionRequiredException] als de gebruiker
  /// eerst "installeren van deze app toestaan" moet aanzetten (systeemscherm wordt al geopend).
  Future<void> update(AppUpdateInfo info) async {
    final canInstall = await _channel.invokeMethod<bool>('canInstallPackages') ?? false;
    if (!canInstall) {
      await _channel.invokeMethod('requestInstallPermission');
      throw UpdatePermissionRequiredException();
    }
    await _channel.invokeMethod('downloadAndInstall', {
      'url': info.downloadUrl,
      'fileName': info.apkFileName ?? '${info.packageName}.apk',
    });
  }
}
