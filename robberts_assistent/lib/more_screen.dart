import 'package:flutter/material.dart';

import 'ai_models_screen.dart';
import 'api_client.dart';
import 'couplings_screen.dart';
import 'garden_screen.dart';
import 'health_check_screen.dart';
import 'jobs_screen.dart';
import 'memory_screen.dart';
import 'nightly_checks_screen.dart';
import 'reference_meals_screen.dart';
import 'updates_screen.dart';
import 'watches_screen.dart';

/// Overzicht van de minder frequent gebruikte schermen.
/// Elk item opent het bestaande, ongewijzigde scherm via een gewone navigatie-push.
class MoreScreen extends StatelessWidget {
  const MoreScreen({super.key, required this.api});

  final ApiClient api;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Meer')),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.yard_outlined),
            title: const Text('Moestuin'),
            onTap: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => GardenScreen(api: api))),
          ),
          ListTile(
            leading: const Icon(Icons.health_and_safety_outlined),
            title: const Text('Health check'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => HealthCheckScreen(api: api)),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.travel_explore_outlined),
            title: const Text('Zoekopdrachten'),
            onTap: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => WatchesScreen(api: api))),
          ),
          ListTile(
            leading: const Icon(Icons.work_outline),
            title: const Text('Vacatures'),
            onTap: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => JobsScreen(api: api))),
          ),
          ListTile(
            leading: const Icon(Icons.hub_outlined),
            title: const Text('Koppelingen'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => CouplingsScreen(api: api)),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.checklist_outlined),
            title: const Text('Nachtchecks'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => NightlyChecksScreen(api: api)),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.psychology_outlined),
            title: const Text('Geheugen'),
            onTap: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => MemoryScreen(api: api))),
          ),
          const Divider(),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Text(
              'Beheer',
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.tune_outlined),
            title: const Text('AI-modellen'),
            subtitle: const Text(
              'Welk model de assistent en het eetdagboek gebruiken',
            ),
            onTap: () => Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => AiModelsScreen(api: api))),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: const Text('Modelgerechten'),
            subtitle: const Text(
              'De foto\'s waarmee nieuw eten wordt vergeleken',
            ),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => ReferenceMealsScreen(api: api)),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.system_update_outlined),
            title: const Text('Updates'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => Scaffold(
                  appBar: AppBar(title: const Text('Updates')),
                  body: const UpdatesScreen(),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
