import 'package:flutter/foundation.dart';
import 'package:quick_actions/quick_actions.dart';

/// De twee snelkoppelingen van de app (ingedrukt houden op het app-icoon; ze zijn ook los op het
/// startscherm te zetten). Zo is een foto of een check-in één tik vanaf het startscherm.
enum AppShortcut {
  foodPhoto('food_photo', 'Foto van eten'),
  checkIn('check_in', 'Hoe voel je je?');

  const AppShortcut(this.type, this.label);

  final String type;
  final String label;
}

class AppShortcuts {
  /// De snelkoppeling waarmee de app is geopend, of die net is aangetikt. `HomeScreen` luistert
  /// hierop, opent het bijbehorende scherm en zet de waarde terug op `null`.
  static final pending = ValueNotifier<AppShortcut?>(null);
  static bool _done = false;
  static String? _lastType;
  static DateTime? _lastAt;

  /// Verwerkt een aangetikte snelkoppeling. Android meldt dezelfde tik bij een koude start soms
  /// twee keer; binnen twee seconden telt een herhaling niet mee.
  @visibleForTesting
  static void handle(String type, {DateTime? now}) {
    final at = now ?? DateTime.now();
    final last = _lastAt;
    if (type == _lastType &&
        last != null &&
        at.difference(last) < const Duration(seconds: 2)) {
      return;
    }
    _lastType = type;
    _lastAt = at;
    for (final shortcut in AppShortcut.values) {
      if (shortcut.type == type) pending.value = shortcut;
    }
  }

  /// Meldt de snelkoppelingen aan bij Android. Doet niets op web en mag nooit de app-start breken.
  static Future<void> setup() async {
    if (_done || kIsWeb) return;
    _done = true;
    try {
      const actions = QuickActions();
      await actions.initialize(handle);
      await actions.setShortcutItems([
        for (final s in AppShortcut.values)
          ShortcutItem(type: s.type, localizedTitle: s.label),
      ]);
    } catch (e) {
      debugPrint('Snelkoppelingen instellen mislukt: $e');
    }
  }
}
