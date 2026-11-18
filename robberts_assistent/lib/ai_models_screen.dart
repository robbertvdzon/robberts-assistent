import 'package:flutter/material.dart';

import 'api_client.dart';

/// Beheer: per toepassing (assistent, eetdagboek, overig) kiezen welk AI-model de backend gebruikt.
/// De lijst met modellen komt van de Agent Runtime; een keuze geldt direct, zonder uitrol.
class AiModelsScreen extends StatefulWidget {
  const AiModelsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<AiModelsScreen> createState() => _AiModelsScreenState();
}

class _AiModelsScreenState extends State<AiModelsScreen> {
  static const _defaultKey = '__standaard__';

  AiModels? _models;
  String? _error;
  String? _savingRole;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final models = await widget.api.getAiModels();
      if (mounted) {
        setState(() {
          _models = models;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'De modellen konden niet worden geladen.');
      }
    }
  }

  Future<void> _choose(AiRoleSetting role, String? key) async {
    final models = _models;
    if (key == null || models == null) return;
    setState(() => _savingRole = role.role);
    try {
      final AiModels updated;
      if (key == _defaultKey) {
        updated = await widget.api.resetAiModel(role.role);
      } else {
        final execution = models.options
            .map((o) => o.execution)
            .followedBy([role.execution])
            .firstWhere((e) => e.key == key);
        updated = await widget.api.setAiModel(role.role, execution);
      }
      if (mounted) setState(() => _models = updated);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Opslaan is mislukt.')));
      }
    } finally {
      if (mounted) setState(() => _savingRole = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final models = _models;
    final muted = TextStyle(
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    );
    return Scaffold(
      appBar: AppBar(title: const Text('AI-modellen')),
      body: models == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text(
                    'Kies per toepassing welk model de Agent Runtime gebruikt. Een wijziging geldt '
                    'direct. Standaard is ${models.defaultExecution.label}.',
                    style: muted,
                  ),
                  if (models.optionsError != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 12),
                      child: Text(
                        'De lijst met modellen kon niet bij de runtime worden opgehaald. '
                        'Terugzetten naar standaard kan wel.',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  const SizedBox(height: 12),
                  for (final role in models.roles)
                    Card(
                      margin: const EdgeInsets.only(bottom: 12),
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              role.label,
                              style: const TextStyle(
                                fontWeight: FontWeight.w600,
                                fontSize: 16,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(role.description, style: muted),
                            const SizedBox(height: 12),
                            DropdownButtonFormField<String>(
                              key: ValueKey(
                                '${role.role}-${role.custom}-${role.execution.key}',
                              ),
                              initialValue: role.custom
                                  ? role.execution.key
                                  : _defaultKey,
                              isExpanded: true,
                              decoration: const InputDecoration(
                                border: OutlineInputBorder(),
                                isDense: true,
                              ),
                              onChanged: _savingRole == null
                                  ? (key) => _choose(role, key)
                                  : null,
                              items: [
                                DropdownMenuItem(
                                  value: _defaultKey,
                                  child: Text(
                                    'Standaard (${models.defaultExecution.model})',
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                for (final o in models.options)
                                  DropdownMenuItem(
                                    value: o.execution.key,
                                    child: Text(
                                      o.available
                                          ? o.execution.label
                                          : '${o.execution.label} (nu geen worker)',
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                // Een eerder gekozen model dat de runtime niet meer aanbiedt blijft
                                // zichtbaar, anders zou de keuzelijst een andere waarde tonen dan geldt.
                                if (role.custom &&
                                    !models.options.any(
                                      (o) =>
                                          o.execution.key == role.execution.key,
                                    ))
                                  DropdownMenuItem(
                                    value: role.execution.key,
                                    child: Text(
                                      '${role.execution.label} (niet meer in de lijst)',
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                              ],
                            ),
                            if (_savingRole == role.role)
                              const Padding(
                                padding: EdgeInsets.only(top: 8),
                                child: LinearProgressIndicator(),
                              ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
    );
  }
}
