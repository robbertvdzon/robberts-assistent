import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

/// Status van deze app t.o.v. de laatste GitHub Release (`notities-latest`).
class AppUpdateInfo {
  const AppUpdateInfo({
    required this.installedVersionCode,
    this.latestVersionCode,
    this.downloadUrl,
    this.apkFileName,
    this.error,
  });

  /// `-1` als de app niet geïnstalleerd is op dit toestel (kan in de praktijk niet gebeuren —
  /// als dit draait, is de app per definitie geïnstalleerd — maar zo blijft de vorm gelijk aan
  /// robberts_assistent's `UpdateChecker`).
  final int installedVersionCode;
  final int? latestVersionCode;
  final String? downloadUrl;
  final String? apkFileName;
  final String? error;

  bool get updateAvailable =>
      error == null && latestVersionCode != null && latestVersionCode! > installedVersionCode;
}

/// Gegooid als [UpdateChecker.update] niet mag installeren — de UI moet dan naar
/// systeeminstellingen sturen (al gedaan via `requestInstallPermission`) en het opnieuw laten
/// proberen na terugkeer.
class UpdatePermissionRequiredException implements Exception {}

/// Checkt en installeert een nieuwe versie van deze app via de vaste GitHub Release-tag
/// `notities-latest`. Zelfde recept als robberts_assistent's `UpdateChecker`, hier vereenvoudigd
/// tot alleen zelf-checken (geen "update alle drie de apps"-scherm nodig in déze app).
class UpdateChecker {
  static const _channel = MethodChannel('nl.vdzon.notities/updater');
  static const _repo = 'robbertvdzon/robberts-assistent';
  static const _packageName = 'nl.vdzon.notities';
  static const _tag = 'notities-latest';

  Future<AppUpdateInfo> checkSelf() async {
    final installed = await _channel.invokeMethod<int>(
          'installedVersionCode',
          {'packageName': _packageName},
        ) ??
        -1;
    try {
      final response = await http
          .get(
            Uri.parse('https://api.github.com/repos/$_repo/releases/tags/$_tag'),
            headers: {'Accept': 'application/vnd.github+json'},
          )
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        return AppUpdateInfo(installedVersionCode: installed, error: 'GitHub gaf HTTP ${response.statusCode} terug.');
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
        installedVersionCode: installed,
        latestVersionCode: latest,
        downloadUrl: apkAsset?['browser_download_url'] as String?,
        apkFileName: apkAsset?['name'] as String?,
        error: latest == null ? 'Kon geen versienummer uit de release-tekst halen.' : null,
      );
    } catch (e) {
      return AppUpdateInfo(installedVersionCode: installed, error: 'Kon niet ophalen: $e');
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
      'fileName': info.apkFileName ?? '$_packageName.apk',
    });
  }
}
