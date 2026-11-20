import 'package:flutter/material.dart';

import 'api_client.dart';
import 'food_screen.dart';

/// Beheer van de modelgerechten: de bevestigde maaltijden met foto waarmee de AI een nieuwe foto
/// vergelijkt. Per maaltijd is te zien wat de rol is (modelgerecht, dubbel of niet gebruikt) en
/// die is hier zelf te zetten, met een eigen beschrijving. Hoeveelheden wijzigen gaat via het
/// eetmoment zelf.
class ReferenceMealsScreen extends StatefulWidget {
  const ReferenceMealsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<ReferenceMealsScreen> createState() => _ReferenceMealsScreenState();
}

class _ReferenceMealsScreenState extends State<ReferenceMealsScreen> {
  static const _filters = [
    ('REFERENTIE', 'Modelgerechten'),
    ('DUBBEL', 'Dubbel'),
    ('GEEN', 'Niet gebruikt'),
    ('ONBEKEND', 'Nog niet beoordeeld'),
  ];

  List<Meal>? _meals;
  String? _error;
  String _filter = 'REFERENTIE';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final meals = await widget.api.getReferenceMeals();
      if (mounted) {
        setState(() {
          _meals = meals;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'De modelgerechten konden niet worden geladen.',
        );
      }
    }
  }

  /// Op een breed scherm (de beheerpagina) staat de gekozen maaltijd naast de lijst.
  Meal? _selected;
  bool _wide = false;

  Future<void> _open(Meal meal) async {
    if (_wide) {
      setState(() => _selected = meal);
      return;
    }
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ReferenceMealScreen(api: widget.api, meal: meal),
      ),
    );
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final meals = _meals;
    return Scaffold(
      appBar: AppBar(title: const Text('Modelgerechten')),
      body: meals == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : LayoutBuilder(
              builder: (context, constraints) {
                _wide = constraints.maxWidth >= 900;
                final list = RefreshIndicator(
                  onRefresh: _load,
                  child: _list(meals),
                );
                if (!_wide) return list;
                final selected = _selected;
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SizedBox(width: 440, child: list),
                    const VerticalDivider(width: 1),
                    Expanded(
                      child: selected == null
                          ? const Center(
                              child: Text('Kies links een maaltijd.'),
                            )
                          : ReferenceMealScreen(
                              key: ValueKey(selected.id),
                              api: widget.api,
                              meal: selected,
                              embedded: true,
                              onChanged: _load,
                            ),
                    ),
                  ],
                );
              },
            ),
    );
  }

  Widget _list(List<Meal> meals) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    final shown = meals.where((m) => m.referenceRole == _filter).toList();
    return ListView(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        16 + MediaQuery.viewPaddingOf(context).bottom,
      ),
      children: [
        Text(
          'Een nieuwe foto wordt vergeleken met deze bevestigde maaltijden van het laatste half '
          'jaar. Tik op een maaltijd om hem wel of niet te gebruiken of de beschrijving aan te passen.',
          style: muted,
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          children: [
            for (final (role, label) in _filters)
              if (role == 'REFERENTIE' ||
                  meals.any((m) => m.referenceRole == role))
                ChoiceChip(
                  key: ValueKey('rol-$role'),
                  label: Text(
                    '$label (${meals.where((m) => m.referenceRole == role).length})',
                  ),
                  selected: _filter == role,
                  onSelected: (_) => setState(() => _filter = role),
                ),
          ],
        ),
        const SizedBox(height: 8),
        if (shown.isEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 48),
            child: Center(
              child: Text('Geen maaltijden in deze groep.', style: muted),
            ),
          ),
        for (final meal in shown)
          Card(
            margin: const EdgeInsets.only(top: 8),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              key: ValueKey('modelgerecht-${meal.id}'),
              onTap: () => _open(meal),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    MealPhoto(
                      api: widget.api,
                      photoId: meal.referencePhotoId.isNotEmpty
                          ? meal.referencePhotoId
                          : meal.photoIds.firstOrNull,
                      size: 72,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            meal.title,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          Text(
                            '${referenceDate(meal.eatenAt)}${meal.referenceManual ? ' · zelf gekozen' : ''}',
                            style: muted,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            referencePortion(meal),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          if (meal.referenceDescription.isNotEmpty)
                            Text(
                              meal.referenceDescription,
                              style: muted,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

String referenceDate(DateTime at) =>
    '${at.day}-${at.month}-${at.year} ${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}';

/// De hele portie in één regel: "Kwark 340 g, Havermout 85 g".
String referencePortion(Meal meal) =>
    meal.items.map((i) => '${i.name} ${i.grams} g').join(', ');

/// Eén modelgerecht bekijken en bewerken.
class ReferenceMealScreen extends StatefulWidget {
  const ReferenceMealScreen({
    super.key,
    required this.api,
    required this.meal,
    this.embedded = false,
    this.onChanged,
  });

  final ApiClient api;
  final Meal meal;

  /// Naast de lijst getoond op de beheerpagina: zonder eigen titelbalk.
  final bool embedded;

  /// Aangeroepen na opslaan, zodat de lijst ernaast meeverandert.
  final VoidCallback? onChanged;

  @override
  State<ReferenceMealScreen> createState() => _ReferenceMealScreenState();
}

class _ReferenceMealScreenState extends State<ReferenceMealScreen> {
  late Meal _meal = widget.meal;
  late final _description = TextEditingController(
    text: widget.meal.referenceDescription,
  );
  bool _saving = false;

  bool get _isReference => _meal.referenceRole == 'REFERENTIE';

  @override
  void dispose() {
    _description.dispose();
    super.dispose();
  }

  Future<void> _save({required bool use}) async {
    setState(() => _saving = true);
    try {
      final meal = await widget.api.setMealReference(
        _meal.id,
        use: use,
        description: _description.text.trim(),
      );
      if (!mounted) return;
      setState(() => _meal = meal);
      widget.onChanged?.call();
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Opgeslagen.')));
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

  Future<void> _openMeal() async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => MealDetailScreen(api: widget.api, mealId: _meal.id),
      ),
    );
    // Hoeveelheden of de titel kunnen zijn gewijzigd; verwijderd kan ook.
    try {
      final meal = await widget.api.getMeal(_meal.id);
      if (mounted) setState(() => _meal = meal);
    } catch (_) {
      if (mounted && !widget.embedded) Navigator.of(context).pop();
    }
    widget.onChanged?.call();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    final role = switch (_meal.referenceRole) {
      'REFERENTIE' => 'Wordt gebruikt als modelgerecht',
      'DUBBEL' => 'Dubbel van een ander modelgerecht; wordt niet gebruikt',
      'GEEN' => 'Wordt niet gebruikt',
      _ => 'Nog niet beoordeeld',
    };
    return Scaffold(
      appBar: widget.embedded ? null : AppBar(title: Text(_meal.title)),
      body: ListView(
        padding: EdgeInsets.fromLTRB(
          16,
          16,
          16,
          16 + MediaQuery.viewPaddingOf(context).bottom,
        ),
        children: [
          SizedBox(
            height: 160,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _meal.photoIds.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (_, index) => MealPhoto(
                api: widget.api,
                photoId: _meal.photoIds[index],
                size: 160,
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (widget.embedded)
            Text(_meal.title, style: theme.textTheme.titleLarge),
          Text(referenceDate(_meal.eatenAt), style: muted),
          const SizedBox(height: 12),
          for (final item in _meal.items)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Row(
                children: [
                  Expanded(child: Text(item.name)),
                  Text('${item.grams} g · ${item.kcal} kcal'),
                ],
              ),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: _openMeal,
              icon: const Icon(Icons.edit_outlined),
              label: const Text('Hoeveelheden bewerken'),
            ),
          ),
          const Divider(height: 32),
          SwitchListTile(
            key: const ValueKey('gebruik-als-modelgerecht'),
            contentPadding: EdgeInsets.zero,
            title: const Text('Gebruiken als modelgerecht'),
            subtitle: Text(
              '$role${_meal.referenceManual ? ' (zelf gekozen)' : ''}',
            ),
            value: _isReference,
            onChanged: _saving ? null : (value) => _save(use: value),
          ),
          const SizedBox(height: 8),
          TextField(
            key: const ValueKey('beschrijving'),
            controller: _description,
            minLines: 2,
            maxLines: 5,
            decoration: const InputDecoration(
              labelText: 'Beschrijving',
              helperText:
                  'Wat er te zien is: servies, vulling, herkenningspunten. '
                  'Hiermee kiest de AI welke foto\'s hij opent.',
              helperMaxLines: 3,
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _saving ? null : () => _save(use: _isReference),
            child: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Beschrijving opslaan'),
          ),
        ],
      ),
    );
  }
}
