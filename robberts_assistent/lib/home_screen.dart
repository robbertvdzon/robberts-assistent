import 'package:flutter/material.dart';

import 'api_client.dart';
import 'assistant_screen.dart';
import 'summary_screen.dart';

/// App-shell na het inloggen: navigatie tussen de dagelijkse samenvatting en de assistent.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  var _tab = 0;

  @override
  Widget build(BuildContext context) {
    final screens = [
      SummaryScreen(api: widget.api),
      AssistantScreen(api: widget.api),
    ];
    return Scaffold(
      appBar: AppBar(
        title: const Text("Robbert's Assistent"),
        actions: [
          IconButton(
            tooltip: 'Uitloggen',
            icon: const Icon(Icons.logout),
            onPressed: widget.onLoggedOut,
          ),
        ],
      ),
      body: IndexedStack(index: _tab, children: screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (index) => setState(() => _tab = index),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.today_outlined), selectedIcon: Icon(Icons.today), label: 'Samenvatting'),
          NavigationDestination(icon: Icon(Icons.assistant_outlined), selectedIcon: Icon(Icons.assistant), label: 'Assistent'),
        ],
      ),
    );
  }
}
