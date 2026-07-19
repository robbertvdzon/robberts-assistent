import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import 'api_client.dart';
import 'conversations_screen.dart';
import 'schedules_screen.dart';
import 'self_update_prompt.dart';
import 'summary_screen.dart';
import 'updates_screen.dart';

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
  void initState() {
    super.initState();
    // Web heeft geen APK/MethodChannel-concept — alleen op Android relevant. Async/niet-blokkerend:
    // de rest van het scherm wacht niet op deze check.
    if (!kIsWeb) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) maybePromptSelfUpdate(context);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      SummaryScreen(api: widget.api),
      ConversationsScreen(api: widget.api),
      SchedulesScreen(api: widget.api),
      const UpdatesScreen(),
    ];
    return Scaffold(
      appBar: AppBar(
        title: const Text("Robbert's assistent"),
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
          NavigationDestination(icon: Icon(Icons.alarm_outlined), selectedIcon: Icon(Icons.alarm), label: 'Herinneringen'),
          NavigationDestination(icon: Icon(Icons.system_update_outlined), selectedIcon: Icon(Icons.system_update), label: 'Updates'),
        ],
      ),
    );
  }
}
