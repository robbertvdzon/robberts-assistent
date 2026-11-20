import 'package:flutter/material.dart';

import 'ai_models_screen.dart';
import 'api_client.dart';
import 'couplings_screen.dart';
import 'health_check_screen.dart';
import 'memory_screen.dart';
import 'nightly_checks_screen.dart';
import 'reference_meals_screen.dart';
import 'solar_screen.dart';

/// Of de webversie op het beheeradres is geopend (`/admin` of `#/admin`).
bool isAdminUri(Uri uri) =>
    uri.path.startsWith('/admin') || uri.fragment.startsWith('/admin');

/// Beheerpagina voor de laptopbrowser (`/admin`): de beheerschermen naast elkaar bereikbaar via
/// een menu links, op een breed scherm. Het zijn dezelfde schermen als onder Meer in de app, dus
/// wat hier kan, kan daar ook.
class AdminScreen extends StatefulWidget {
  const AdminScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends State<AdminScreen> {
  int _index = 0;

  late final _sections = <(String, IconData, double, Widget)>[
    (
      'Modelgerechten',
      Icons.photo_library_outlined,
      1200,
      ReferenceMealsScreen(api: widget.api),
    ),
    ('AI-modellen', Icons.tune_outlined, 760, AiModelsScreen(api: widget.api)),
    (
      'Health check',
      Icons.health_and_safety_outlined,
      760,
      HealthCheckScreen(api: widget.api),
    ),
    (
      'Zonnepanelen',
      Icons.solar_power_outlined,
      900,
      SolarScreen(api: widget.api),
    ),
    ('Koppelingen', Icons.hub_outlined, 760, CouplingsScreen(api: widget.api)),
    (
      'Nachtchecks',
      Icons.checklist_outlined,
      760,
      NightlyChecksScreen(api: widget.api),
    ),
    ('Geheugen', Icons.psychology_outlined, 760, MemoryScreen(api: widget.api)),
  ];

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= 720;
    final (_, _, maxWidth, screen) = _sections[_index];
    final content = Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        // Elk scherm houdt zijn eigen toestand; wisselen laadt het opnieuw.
        child: KeyedSubtree(key: ValueKey(_index), child: screen),
      ),
    );
    return Scaffold(
      appBar: AppBar(
        title: const Text("Robbert's assistent · beheer"),
        actions: [
          IconButton(
            tooltip: 'Uitloggen',
            icon: const Icon(Icons.logout),
            onPressed: widget.onLoggedOut,
          ),
        ],
      ),
      body: wide
          ? Row(
              children: [
                NavigationRail(
                  extended: MediaQuery.sizeOf(context).width >= 1000,
                  labelType: MediaQuery.sizeOf(context).width >= 1000
                      ? NavigationRailLabelType.none
                      : NavigationRailLabelType.all,
                  selectedIndex: _index,
                  onDestinationSelected: (i) => setState(() => _index = i),
                  destinations: [
                    for (final (label, icon, _, _) in _sections)
                      NavigationRailDestination(
                        icon: Icon(icon),
                        label: Text(label),
                      ),
                  ],
                ),
                const VerticalDivider(width: 1),
                Expanded(child: content),
              ],
            )
          : content,
      bottomNavigationBar: wide
          ? null
          : NavigationBar(
              selectedIndex: _index,
              onDestinationSelected: (i) => setState(() => _index = i),
              labelBehavior: NavigationDestinationLabelBehavior.alwaysHide,
              destinations: [
                for (final (label, icon, _, _) in _sections)
                  NavigationDestination(icon: Icon(icon), label: label),
              ],
            ),
    );
  }
}
