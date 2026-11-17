import 'package:flutter/material.dart';

import 'api_client.dart';

const hungerLabels = ['Geen', 'Trek', 'Honger', 'Slap'];
const energyLabels = ['1 moe', '2', '3', '4', '5 fit'];

/// Korte omschrijving van een check-in voor in een tijdlijn, bv. "trek · energie 4".
String describeCheckIn(CheckIn c) => [
  if (c.hunger != null)
    c.hunger == 0
        ? 'geen honger'
        : c.hunger == 3
        ? 'slap van de honger'
        : hungerLabels[c.hunger!].toLowerCase(),
  if (c.energy != null) 'energie ${c.energy}',
].join(' · ');

/// De twee schalen als keuzechips. Nogmaals tikken op een gekozen waarde haalt hem weer weg.
class CheckInPicker extends StatelessWidget {
  const CheckInPicker({
    super.key,
    required this.hunger,
    required this.energy,
    required this.onHunger,
    required this.onEnergy,
    this.enabled = true,
  });

  final int? hunger;
  final int? energy;
  final ValueChanged<int?> onHunger;
  final ValueChanged<int?> onEnergy;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final muted = TextStyle(
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Honger', style: muted),
        const SizedBox(height: 6),
        Wrap(
          spacing: 8,
          children: [
            for (var i = 0; i < hungerLabels.length; i++)
              ChoiceChip(
                label: Text(hungerLabels[i]),
                selected: hunger == i,
                onSelected: enabled ? (on) => onHunger(on ? i : null) : null,
              ),
          ],
        ),
        const SizedBox(height: 14),
        Text('Energie', style: muted),
        const SizedBox(height: 6),
        Wrap(
          spacing: 8,
          children: [
            for (var i = 0; i < energyLabels.length; i++)
              ChoiceChip(
                label: Text(energyLabels[i]),
                selected: energy == i + 1,
                onSelected: enabled
                    ? (on) => onEnergy(on ? i + 1 : null)
                    : null,
              ),
          ],
        ),
      ],
    );
  }
}

/// Losse check-in: honger en energie in twee tikken, eventueel met een paar woorden erbij.
class CheckInScreen extends StatefulWidget {
  const CheckInScreen({super.key, required this.api, this.source = 'APP'});

  final ApiClient api;

  /// `APP` als Robbert het scherm zelf opende, `MELDING` als hij via de vaste vraag binnenkwam.
  final String source;

  @override
  State<CheckInScreen> createState() => _CheckInScreenState();
}

class _CheckInScreenState extends State<CheckInScreen> {
  final _note = TextEditingController();
  int? _hunger;
  int? _energy;
  bool _saving = false;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await widget.api.recordCheckIn(
        hunger: _hunger,
        energy: _energy,
        note: _note.text.trim(),
        source: widget.source,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (_) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Opslaan is mislukt.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final canSave = !_saving && (_hunger != null || _energy != null);
    return Scaffold(
      appBar: AppBar(title: const Text('Hoe voel je je?')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          CheckInPicker(
            hunger: _hunger,
            energy: _energy,
            enabled: !_saving,
            onHunger: (v) => setState(() => _hunger = v),
            onEnergy: (v) => setState(() => _energy = v),
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _note,
            enabled: !_saving,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Iets erbij zeggen (hoeft niet)',
            ),
          ),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: canSave ? _save : null,
            child: const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text('Klaar'),
            ),
          ),
        ],
      ),
    );
  }
}
