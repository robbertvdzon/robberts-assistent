import 'package:flutter/material.dart';

import 'update_checker.dart';

/// Toont voor alle drie de apps (wind, robberts_assistent, notities) de geïnstalleerde vs. laatste
/// versie, met een bijwerk-knop per app. Zie [UpdateChecker] voor hoe dat bepaald wordt.
class UpdatesScreen extends StatefulWidget {
  const UpdatesScreen({super.key});

  @override
  State<UpdatesScreen> createState() => _UpdatesScreenState();
}

class _UpdatesScreenState extends State<UpdatesScreen> {
  final _checker = UpdateChecker();
  List<AppUpdateInfo>? _apps;
  var _loading = false;
  final _updating = <String>{};
  String? _lastError;

  @override
  void initState() {
    super.initState();
    _check();
  }

  Future<void> _check() async {
    setState(() {
      _loading = true;
      _lastError = null;
    });
    try {
      final apps = await _checker.checkAll();
      if (mounted) setState(() => _apps = apps);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _update(AppUpdateInfo info) async {
    setState(() {
      _updating.add(info.packageName);
      _lastError = null;
    });
    try {
      await _checker.update(info);
    } on UpdatePermissionRequiredException {
      if (mounted) {
        setState(() => _lastError =
            '${info.label}: zet in de systeeminstellingen "installeren van deze app toestaan" aan en probeer opnieuw.');
      }
    } catch (e) {
      if (mounted) setState(() => _lastError = '${info.label}: bijwerken mislukt ($e).');
    } finally {
      if (mounted) setState(() => _updating.remove(info.packageName));
    }
  }

  /// Werkt alle apps met een beschikbare update na elkaar bij. Elke installatie vereist zelf een
  /// tik op het systeemdialoogje (Android staat een derde-partij-app niet toe om dat over te
  /// slaan) — dit knopje bespaart dus het los aftikken per app-kaart, niet de installatiebevestiging.
  Future<void> _updateAll() async {
    for (final app in _apps?.where((a) => a.updateAvailable).toList() ?? const <AppUpdateInfo>[]) {
      await _update(app);
    }
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: _check,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (_lastError != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(_lastError!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
          if (_loading && _apps == null) const Center(child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator())),
          if ((_apps?.any((a) => a.updateAvailable) ?? false) && _updating.isEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: FilledButton.icon(
                onPressed: _updateAll,
                icon: const Icon(Icons.system_update),
                label: const Text('Alles bijwerken'),
              ),
            ),
          ...?_apps?.map((app) => _AppUpdateCard(
                info: app,
                updating: _updating.contains(app.packageName),
                onUpdate: () => _update(app),
              )),
        ],
      ),
    );
  }
}

class _AppUpdateCard extends StatelessWidget {
  const _AppUpdateCard({required this.info, required this.updating, required this.onUpdate});

  final AppUpdateInfo info;
  final bool updating;
  final VoidCallback onUpdate;

  @override
  Widget build(BuildContext context) {
    final installedText = info.isInstalled ? 'v${info.installedVersionCode}' : 'niet geïnstalleerd';
    final latestText = info.latestVersionCode != null ? 'v${info.latestVersionCode}' : 'onbekend';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(info.label, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text('Geïnstalleerd: $installedText  •  Nieuwste: $latestText'),
                  if (info.error != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        info.error!,
                        style: TextStyle(color: Theme.of(context).colorScheme.error, fontSize: 12),
                      ),
                    ),
                ],
              ),
            ),
            if (updating)
              const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
            else if (info.updateAvailable)
              FilledButton(onPressed: onUpdate, child: const Text('Bijwerken'))
            else if (info.error == null)
              const Icon(Icons.check_circle, color: Colors.green),
          ],
        ),
      ),
    );
  }
}
