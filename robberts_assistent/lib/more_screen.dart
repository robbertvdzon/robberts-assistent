import 'package:flutter/material.dart';

import 'api_client.dart';
import 'couplings_screen.dart';
import 'nightly_checks_screen.dart';
import 'updates_screen.dart';

/// Overzicht van de minder frequent gebruikte schermen: Koppelingen, Nachtchecks en Updates.
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
            leading: const Icon(Icons.system_update_outlined),
            title: const Text('Updates'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const UpdatesScreen()),
            ),
          ),
        ],
      ),
    );
  }
}
