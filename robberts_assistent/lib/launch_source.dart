import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart' show ValueNotifier;
import 'package:flutter/services.dart';

import 'update_checker.dart';

/// Ruwe gegevens van één app-start, zoals de native laag ze aanlevert (zie `LaunchSource.kt`).
/// Bewust ruw: wat Google Assistent/Gemini precies meestuurt is nog niet bekend en wordt uit de
/// backend-logregels (`grep APP_LAUNCH`) afgeleid.
class AppLaunchInfo {
  const AppLaunchInfo({
    required this.source,
    required this.platform,
    this.referrer,
    this.action,
    this.categories = const [],
    this.extras = const {},
    this.appVersion,
  });

  final String source;
  final String platform;
  final String? referrer;
  final String? action;
  final List<String> categories;
  final Map<String, String> extras;
  final String? appVersion;

  /// De enige bron waar de app zelf iets mee doet: direct in praatmodus openen.
  bool get isAssistant => source == 'ASSISTANT';

  factory AppLaunchInfo.fromMap(
    Map<dynamic, dynamic> map, {
    required String platform,
    String? appVersion,
  }) {
    return AppLaunchInfo(
      source: map['source']?.toString() ?? 'UNKNOWN',
      platform: platform,
      referrer: map['referrer']?.toString(),
      action: map['action']?.toString(),
      categories: (map['categories'] as List?)?.map((c) => '$c').toList() ?? const [],
      extras: (map['extras'] as Map?)?.map((k, v) => MapEntry('$k', '$v')) ?? const {},
      appVersion: appVersion,
    );
  }

  Map<String, dynamic> toJson() => {
    'source': source,
    'platform': platform,
    if (referrer != null) 'referrer': referrer,
    if (action != null) 'action': action,
    'categories': categories,
    'extras': extras,
    if (appVersion != null) 'appVersion': appVersion,
  };
}

/// Leest de launch-bron uit de native laag (MethodChannel `nl.vdzon.robberts_assistent/launch`)
/// en biedt de laatst ontvangen launch aan via [lastLaunch], in dezelfde stijl als
/// `FcmService.deepLinkTarget`: `HomeScreen` luistert, handelt af en zet de waarde weer op `null`.
class LaunchSourceService {
  static const channel = MethodChannel('nl.vdzon.robberts_assistent/launch');

  /// Niet-`null` zodra er een app-start te melden is.
  static final lastLaunch = ValueNotifier<AppLaunchInfo?>(null);

  static bool _done = false;

  /// Idempotent; veilig meerdere keren aan te roepen. Faalt nooit hard: kan de native laag niets
  /// leveren (web, of een oudere APK zonder dit channel), dan blijft [lastLaunch] simpelweg leeg.
  static Future<void> setup() async {
    if (_done) return;
    _done = true;
    if (kIsWeb) {
      // Web heeft geen MethodChannel; één launch met alleen het platform houdt dat veld
      // betekenisvol zonder native code.
      lastLaunch.value = const AppLaunchInfo(source: 'UNKNOWN', platform: 'web');
      return;
    }
    // Push: een tweede start komt via onNewIntent binnen terwijl de app al draait.
    channel.setMethodCallHandler((call) async {
      if (call.method != 'launchInfo') return null;
      final arguments = call.arguments;
      if (arguments is Map) {
        lastLaunch.value = AppLaunchInfo.fromMap(
          arguments,
          platform: 'android',
          appVersion: await _appVersion(),
        );
      }
      return null;
    });
    // Pull: de koude start, waarbij Flutter nog niet luisterde.
    try {
      final map = await channel.invokeMapMethod<String, dynamic>('launchInfo');
      if (map != null) {
        lastLaunch.value = AppLaunchInfo.fromMap(
          map,
          platform: 'android',
          appVersion: await _appVersion(),
        );
      }
    } catch (_) {
      // Geen native laag beschikbaar — geen launch te melden, de app werkt gewoon door.
    }
  }

  /// Hergebruikt de bestaande versielogica (dezelfde updater-channel als `UpdateChecker`).
  static Future<String?> _appVersion() async {
    try {
      final code = await const MethodChannel('nl.vdzon.robberts_assistent/updater')
          .invokeMethod<int>('installedVersionCode', {
            'packageName': UpdateChecker.selfPackageName,
          });
      return (code == null || code < 0) ? null : '$code';
    } catch (_) {
      return null;
    }
  }
}
