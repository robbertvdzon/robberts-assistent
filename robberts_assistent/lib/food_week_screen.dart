import 'package:flutter/material.dart';

import 'api_client.dart';

const _dayNames = ['ma', 'di', 'wo', 'do', 'vr', 'za', 'zo'];
const _basisLabels = {
  'GEWOGEN': 'gewogen',
  'ETIKET': 'van het etiket',
  'RECEPT': 'uit een recept',
  'VERGELEKEN': 'vergeleken met een gewogen foto',
  'GESCHAT': 'geschat',
};

String _kg(double v) => v.toStringAsFixed(1).replaceAll('.', ',');

/// Weekoverzicht: per dag gegeten tegen verbruikt, de gemiddelden, waar de cijfers op rusten en
/// het gewicht (handmatig in te voeren).
class FoodWeekScreen extends StatefulWidget {
  const FoodWeekScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<FoodWeekScreen> createState() => _FoodWeekScreenState();
}

class _FoodWeekScreenState extends State<FoodWeekScreen> {
  final _weight = TextEditingController();
  WeekOverview? _week;
  String? _error;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _weight.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final week = await widget.api.getWeekOverview();
      if (mounted) {
        setState(() {
          _week = week;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'Het weekoverzicht kon niet worden geladen.');
      }
    }
  }

  Future<void> _saveWeight() async {
    final kg = double.tryParse(_weight.text.trim().replaceAll(',', '.'));
    if (kg == null || kg < 30 || kg > 250) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vul je gewicht in kilo\'s in, bijvoorbeeld 81,4.'),
        ),
      );
      return;
    }
    setState(() => _saving = true);
    try {
      await widget.api.setWeight(kg);
      _weight.clear();
      await _load();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Opslaan is mislukt.')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final week = _week;
    final scheme = Theme.of(context).colorScheme;
    final muted = TextStyle(color: scheme.onSurfaceVariant);
    return Scaffold(
      appBar: AppBar(title: const Text('Deze week')),
      body: week == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Expanded(child: SizedBox()),
                            SizedBox(
                              width: 76,
                              child: Text(
                                'gegeten',
                                textAlign: TextAlign.right,
                                style: muted,
                              ),
                            ),
                            SizedBox(
                              width: 76,
                              child: Text(
                                'verbruikt',
                                textAlign: TextAlign.right,
                                style: muted,
                              ),
                            ),
                          ],
                        ),
                        for (final d in week.days) _DayRow(day: d),
                        const Divider(height: 24),
                        Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: Text(
                            'Vandaag telt niet mee in de gemiddelden en percentages.',
                            style: muted,
                          ),
                        ),
                        Row(
                          children: [
                            const Expanded(
                              child: Text(
                                'Gemiddeld',
                                style: TextStyle(fontWeight: FontWeight.w600),
                              ),
                            ),
                            SizedBox(
                              width: 76,
                              child: Text(
                                week.avgEatenKcal?.toString() ?? '–',
                                textAlign: TextAlign.right,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                            SizedBox(
                              width: 76,
                              child: Text(
                                week.avgBurnedKcal?.toString() ?? '–',
                                textAlign: TextAlign.right,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (week.avgBalanceKcal != null)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: Text(
                              _balance(week.avgBalanceKcal!),
                              style: muted,
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                if (week.avgEatenKcal != null) ...[
                  const SizedBox(height: 12),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Gemiddeld per dag', style: muted),
                          const SizedBox(height: 6),
                          Text(
                            'Eiwit ${week.avgProteinG?.round() ?? 0} g · vezels ${week.avgFiberG?.round() ?? 0} g · groente ${week.avgVegetableG ?? 0} g',
                          ),
                          Text(
                            'Richtwaarde per dag: vezels 30–40 g, groente 250 g.',
                            style: muted,
                          ),
                          if (week.basisShare.isNotEmpty) ...[
                            const SizedBox(height: 14),
                            Text('Waar je calorieën op rusten', style: muted),
                            const SizedBox(height: 6),
                            for (final e in week.basisShare.entries)
                              Text(
                                '${e.value}% ${_basisLabels[e.key] ?? e.key.toLowerCase()}',
                              ),
                          ],
                          if (week.unchangedShare != null) ...[
                            const SizedBox(height: 14),
                            Text(
                              '${week.unchangedShare}% van de bevestigde voorstellen nam je zonder wijziging over.',
                              style: muted,
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Gewicht', style: muted),
                        const SizedBox(height: 6),
                        if (week.weights.isEmpty)
                          const Text('Nog niets ingevuld.')
                        else
                          for (final w in week.weights.reversed.take(6))
                            Text(
                              '${w.date.day}-${w.date.month}: ${_kg(w.weightKg)} kg',
                            ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: TextField(
                                controller: _weight,
                                enabled: !_saving,
                                keyboardType:
                                    const TextInputType.numberWithOptions(
                                      decimal: true,
                                    ),
                                decoration: const InputDecoration(
                                  border: OutlineInputBorder(),
                                  labelText: 'Gewicht (kg)',
                                  isDense: true,
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            FilledButton(
                              onPressed: _saving ? null : _saveWeight,
                              child: const Text('Opslaan'),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }

  String _balance(int diff) {
    if (diff.abs() < 50) {
      return 'Op afgeronde dagen at je gemiddeld ongeveer evenveel als je verbruikte.';
    }
    return diff < 0
        ? 'Op afgeronde dagen at je gemiddeld ${-diff} kcal minder dan je verbruikte.'
        : 'Op afgeronde dagen at je gemiddeld $diff kcal meer dan je verbruikte.';
  }
}

class _DayRow extends StatelessWidget {
  const _DayRow({required this.day});

  final OverviewDay day;

  @override
  Widget build(BuildContext context) {
    final muted = TextStyle(
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    );
    final sleep = day.sleepMinutes;
    final extras = [
      if (day.steps != null) '${day.steps} stappen',
      if (sleep != null)
        '${sleep ~/ 60} u ${(sleep % 60).toString().padLeft(2, '0')} slaap',
    ].join(' · ');
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${_dayNames[day.date.weekday - 1]} ${day.date.day}'),
                if (extras.isNotEmpty)
                  Text(extras, style: muted.copyWith(fontSize: 12)),
              ],
            ),
          ),
          SizedBox(
            width: 76,
            child: Text(
              day.mealCount > 0 ? '${day.eatenKcal}' : '–',
              textAlign: TextAlign.right,
            ),
          ),
          SizedBox(
            width: 76,
            child: Text(
              day.burnedKcal?.toString() ?? '–',
              textAlign: TextAlign.right,
            ),
          ),
        ],
      ),
    );
  }
}

/// Verbanden uit Robberts eigen gegevens. Elke bevinding toont de getallen erachter en of er al
/// genoeg waarnemingen zijn.
class InsightsScreen extends StatefulWidget {
  const InsightsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> {
  List<Insight>? _insights;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final insights = await widget.api.getInsights();
      if (mounted) setState(() => _insights = insights);
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'De verbanden konden niet worden geladen.');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final insights = _insights;
    final scheme = Theme.of(context).colorScheme;
    final muted = TextStyle(color: scheme.onSurfaceVariant);
    return Scaffold(
      appBar: AppBar(title: const Text('Verbanden')),
      body: insights == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Uitgerekend over de laatste acht weken. Het is samenhang in jouw eigen gegevens, '
                  'geen bewezen oorzaak: slaap, sport en eten veranderen vaak tegelijk.',
                  style: muted,
                ),
                const SizedBox(height: 12),
                if (insights.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      'Nog niet genoeg gegevens. Verbanden verschijnen zodra er een paar weken '
                      'aan eetmomenten en check-ins staan.',
                      textAlign: TextAlign.center,
                      style: muted,
                    ),
                  ),
                for (final i in insights)
                  Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            i.title,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          const SizedBox(height: 4),
                          Text(i.text),
                          const SizedBox(height: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color:
                                  (i.clear
                                          ? scheme.primary
                                          : const Color(0xFFB7791F))
                                      .withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(99),
                            ),
                            child: Text(
                              i.clear
                                  ? 'genoeg waarnemingen'
                                  : 'nog te weinig waarnemingen',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: i.clear
                                    ? scheme.primary
                                    : const Color(0xFF8A5A10),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}
