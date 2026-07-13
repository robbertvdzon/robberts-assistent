import 'package:flutter/material.dart';

import 'update_checker.dart';

/// Checkt (async, niet-blokkerend) of er een nieuwe versie van deze app is en vraagt de gebruiker
/// via een dialoogje of hij nu bijgewerkt wil worden. Aanroepen vanuit `initState` na het opstarten
/// — faalt stil (geen dialoog, geen foutmelding) bij netwerkproblemen: dit mag de app nooit
/// hinderen, het is puur een vriendelijke herinnering.
Future<void> maybePromptSelfUpdate(BuildContext context) async {
  final checker = UpdateChecker();
  AppUpdateInfo? checkedInfo;
  try {
    checkedInfo = await checker.checkSelf();
  } catch (_) {
    return;
  }
  if (!checkedInfo.updateAvailable || !context.mounted) return;
  // Lokale niet-nullable variabele: `checkedInfo` zelf wordt niet gepromoot in de closures
  // hieronder (showDialog-builder, catch-blokken) omdat het een gevangen, mutabele variabele is.
  final info = checkedInfo;

  final shouldUpdate = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text('Nieuwe versie beschikbaar'),
      content: Text(
        'Er is een nieuwere versie van ${info.label} (v${info.latestVersionCode}, '
        'je hebt v${info.installedVersionCode}). Nu bijwerken?',
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Later')),
        FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Bijwerken')),
      ],
    ),
  );
  if (shouldUpdate != true || !context.mounted) return;

  try {
    await checker.update(info);
  } on UpdatePermissionRequiredException {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Zet "installeren van deze app toestaan" aan en probeer daarna opnieuw bij te werken.'),
        ),
      );
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Bijwerken mislukt: $e')));
    }
  }
}
