import 'package:flutter/material.dart';
import 'package:notes_editor/notes_editor.dart';

import 'ai_models_screen.dart';
import 'api_client.dart';
import 'banking_screen.dart';
import 'couplings_screen.dart';
import 'garden_screen.dart';
import 'health_check_screen.dart';
import 'jobs_screen.dart';
import 'memory_screen.dart';
import 'nightly_checks_screen.dart';
import 'notifications_screen.dart';
import 'reference_meals_screen.dart';
import 'updates_screen.dart';
import 'watches_screen.dart';

/// Minder vaak gebruikte schermen met stabiele, deelbare bestemmingen.
class MoreScreen extends StatelessWidget {
  const MoreScreen({super.key, required this.api, this.onOpen});
  final ApiClient api;
  final ValueChanged<String>? onOpen;

  static const paths = [
    'meldingen',
    'notities',
    'bankzaken',
    'moestuin',
    'health',
    'zoekopdrachten',
    'vacatures',
    'koppelingen',
    'nachtchecks',
    'geheugen',
    'ai-modellen',
    'modelgerechten',
    'updates',
  ];
  static Widget detail(
    String name,
    ApiClient api, {
    String? month,
    ValueChanged<String>? onMonthChanged,
    Uri? location,
    ValueChanged<Map<String, String>>? onBankLocationChanged,
  }) => switch (name) {
    'meldingen' => NotificationsScreen(api: api),
    // Dezelfde editor als de losse notities-app; uitloggen loopt via de app zelf.
    'notities' => NotesEditorScreen(api: api.notes),
    'bankzaken' => BankingScreen(
      api: api,
      initialMonth: month,
      onMonthChanged: onMonthChanged,
      initialView: location?.queryParameters['view'] ?? 'month',
      initialCategory: location?.queryParameters['category'],
      initialYear: int.tryParse(location?.queryParameters['year'] ?? ''),
      onLocationChanged: onBankLocationChanged,
    ),
    'moestuin' => GardenScreen(api: api),
    'health' => HealthCheckScreen(api: api),
    'zoekopdrachten' => WatchesScreen(api: api),
    'vacatures' => JobsScreen(api: api),
    'koppelingen' => CouplingsScreen(api: api),
    'nachtchecks' => NightlyChecksScreen(api: api),
    'geheugen' => MemoryScreen(api: api),
    'ai-modellen' => AiModelsScreen(api: api),
    'modelgerechten' => ReferenceMealsScreen(api: api),
    _ => Scaffold(
      appBar: AppBar(title: const Text('Updates')),
      body: const UpdatesScreen(),
    ),
  };

  @override
  Widget build(BuildContext context) {
    final entries = <(String, IconData, String, String?)>[
      (
        'meldingen',
        Icons.notifications_outlined,
        'Meldingen',
        'Alle meldingen en hun geschiedenis',
      ),
      (
        'notities',
        Icons.edit_note_outlined,
        'Notities',
        'Dezelfde documenten als in de notities-app',
      ),
      (
        'bankzaken',
        Icons.account_balance_outlined,
        'Bankzaken',
        'Maanduitgaven, categorieën en overig / extra',
      ),
      ('moestuin', Icons.yard_outlined, 'Moestuin', null),
      ('health', Icons.health_and_safety_outlined, 'Health check', null),
      ('zoekopdrachten', Icons.travel_explore_outlined, 'Zoekopdrachten', null),
      ('vacatures', Icons.work_outline, 'Vacatures', null),
      ('koppelingen', Icons.hub_outlined, 'Koppelingen', null),
      ('nachtchecks', Icons.checklist_outlined, 'Nachtchecks', null),
      ('geheugen', Icons.psychology_outlined, 'Geheugen', null),
      (
        'ai-modellen',
        Icons.tune_outlined,
        'AI-modellen',
        'Welk model de assistent en het eetdagboek gebruiken',
      ),
      (
        'modelgerechten',
        Icons.photo_library_outlined,
        'Modelgerechten',
        "De foto's waarmee nieuw eten wordt vergeleken",
      ),
      ('updates', Icons.system_update_outlined, 'Updates', null),
    ];
    return Scaffold(
      appBar: AppBar(title: const Text('Meer')),
      body: ListView(
        children: [
          for (final entry in entries) ...[
            if (entry.$1 == 'ai-modellen') ...[
              const Divider(),
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 8, 16, 4),
                child: Text('Beheer'),
              ),
            ],
            ListTile(
              leading: Icon(entry.$2),
              title: Text(entry.$3),
              subtitle: entry.$4 == null ? null : Text(entry.$4!),
              onTap: () {
                final path = '/meer/${entry.$1}';
                if (onOpen != null) {
                  onOpen!(path);
                } else {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      settings: RouteSettings(name: path),
                      builder: (_) => detail(entry.$1, api),
                    ),
                  );
                }
              },
            ),
          ],
        ],
      ),
    );
  }
}
