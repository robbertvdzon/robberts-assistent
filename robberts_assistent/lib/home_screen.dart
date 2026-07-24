import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import 'api_client.dart';
import 'conversations_screen.dart';
import 'fcm_service.dart';
import 'health_check_screen.dart';
import 'more_screen.dart';
import 'schedules_screen.dart';
import 'self_update_prompt.dart';
import 'summary_screen.dart';

/// App-shell na het inloggen: navigatie tussen Upcoming (briefing), Health check, de assistent,
/// herinneringen en 'Meer'.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  var _tab = 2;

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
    // Tik op de dagelijkse Morgen-briefing-push (of koude start via die push) → deze tab tonen.
    FcmService.deepLinkTab.addListener(_onDeepLinkTab);
    _onDeepLinkTab();
  }

  @override
  void dispose() {
    FcmService.deepLinkTab.removeListener(_onDeepLinkTab);
    super.dispose();
  }

  void _onDeepLinkTab() {
    final tab = FcmService.deepLinkTab.value;
    if (tab == null) return;
    FcmService.deepLinkTab.value = null;
    if (mounted) setState(() => _tab = tab);
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      SummaryScreen(api: widget.api),
      HealthCheckScreen(api: widget.api),
      ConversationsScreen(api: widget.api),
      SchedulesScreen(api: widget.api),
      MoreScreen(api: widget.api),
    ];
    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Image.asset('assets/icon/icon.png', width: 28, height: 28),
            ),
            const Text("Robbert's assistent"),
          ],
        ),
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
          NavigationDestination(icon: Icon(Icons.today_outlined), selectedIcon: Icon(Icons.today), label: 'Upcoming'),
          NavigationDestination(icon: Icon(Icons.health_and_safety_outlined), selectedIcon: Icon(Icons.health_and_safety), label: 'Health check'),
          NavigationDestination(icon: Icon(Icons.assistant_outlined), selectedIcon: Icon(Icons.assistant), label: 'Assistent'),
          NavigationDestination(icon: Icon(Icons.alarm_outlined), selectedIcon: Icon(Icons.alarm), label: 'Herinneringen'),
          NavigationDestination(icon: Icon(Icons.more_horiz_outlined), selectedIcon: Icon(Icons.more_horiz), label: 'Meer'),
        ],
      ),
    );
  }
}
