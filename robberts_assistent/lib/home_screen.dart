import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import 'api_client.dart';
import 'app_logo.dart';
import 'conversations_screen.dart';
import 'fcm_service.dart';
import 'more_screen.dart';
import 'schedules_screen.dart';
import 'self_update_prompt.dart';
import 'summary_screen.dart';
import 'watches_screen.dart';

/// App-shell na het inloggen: vier hoofdbestemmingen met de overige schermen onder 'Meer'.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.api, required this.onLoggedOut});

  final ApiClient api;
  final VoidCallback onLoggedOut;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  var _tab = 1;

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
    // Tik op een briefing- of watch-push (ook bij koude start) → het bijbehorende doel tonen.
    FcmService.deepLinkTarget.addListener(_onDeepLinkTarget);
    _onDeepLinkTarget();
  }

  @override
  void dispose() {
    FcmService.deepLinkTarget.removeListener(_onDeepLinkTarget);
    super.dispose();
  }

  void _onDeepLinkTarget() {
    final target = FcmService.deepLinkTarget.value;
    if (target == null) return;
    FcmService.deepLinkTarget.value = null;
    if (!mounted) return;
    if (target == DeepLinkTarget.today) {
      Navigator.of(context).popUntil((route) => route.isFirst);
      _selectTab(0);
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      Navigator.of(
        context,
      ).push(MaterialPageRoute(builder: (_) => WatchesScreen(api: widget.api)));
    });
  }

  void _selectTab(int tab) {
    setState(() => _tab = tab);
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      SummaryScreen(api: widget.api),
      ConversationsScreen(api: widget.api),
      SchedulesScreen(api: widget.api),
      MoreScreen(api: widget.api),
    ];
    return Scaffold(
      appBar: AppBar(
        title: const AppHeaderTitle(),
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
        onDestinationSelected: _selectTab,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.today_outlined),
            selectedIcon: Icon(Icons.today),
            label: 'Vandaag',
          ),
          NavigationDestination(
            icon: Icon(Icons.assistant_outlined),
            selectedIcon: Icon(Icons.assistant),
            label: 'Assistent',
          ),
          NavigationDestination(
            icon: Icon(Icons.alarm_outlined),
            selectedIcon: Icon(Icons.alarm),
            label: 'Taken',
          ),
          NavigationDestination(
            icon: Icon(Icons.more_horiz_outlined),
            selectedIcon: Icon(Icons.more_horiz),
            label: 'Meer',
          ),
        ],
      ),
    );
  }
}
