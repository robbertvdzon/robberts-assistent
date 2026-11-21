import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'bank_insights.dart';

/// Reconciled amounts derive from the same transactions shown in the drill-down.
class BankDashboard extends StatelessWidget {
  const BankDashboard({
    super.key,
    required this.report,
    required this.history,
    required this.budgets,
    required this.categories,
    required this.accounts,
    required this.detail,
    required this.selectedCategory,
    required this.onCategory,
    required this.onClassify,
    required this.onRule,
  });
  final Map<String, dynamic> report, history, budgets;
  final Map<String, String> categories;
  final List<Map<String, dynamic>> accounts;
  final bool detail;
  final String? selectedCategory;
  final ValueChanged<String> onCategory;
  final ValueChanged<Map<String, dynamic>> onClassify, onRule;
  Color _accent(BuildContext c) => Theme.of(c).colorScheme.primary;
  Widget _panel(BuildContext c, String title, List<Widget> children) => Card(
    margin: EdgeInsets.zero,
    child: Padding(
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            title,
            style: Theme.of(
              c,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    ),
  );
  Widget _metric(BuildContext c, String label, int amount, String subtitle) =>
      _panel(c, label, [
        Text(
          bankMoneyCents(amount),
          style: Theme.of(
            c,
          ).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 6),
        Text(subtitle, style: Theme.of(c).textTheme.bodySmall),
      ]);
  Widget _columns(List<Widget> children, {double minWidth = 280}) =>
      LayoutBuilder(
        builder: (context, constraints) {
          final n = math.min(
            children.length,
            math.max(1, (constraints.maxWidth / (minWidth + 16)).floor()),
          );
          return Wrap(
            spacing: 16,
            runSpacing: 16,
            children: children
                .map(
                  (child) => SizedBox(
                    width: (constraints.maxWidth - (n - 1) * 16) / n,
                    child: child,
                  ),
                )
                .toList(),
          );
        },
      );
  Widget _notice(
    BuildContext c,
    String title,
    String text, {
    VoidCallback? onTap,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Container(
      decoration: BoxDecoration(
        border: Border(left: BorderSide(color: _accent(c), width: 3)),
      ),
      padding: const EdgeInsets.only(left: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
          const SizedBox(height: 4),
          Text(text),
          if (onTap != null)
            TextButton(
              onPressed: onTap,
              child: const Text('Bekijk betalingen'),
            ),
        ],
      ),
    ),
  );
  @override
  Widget build(BuildContext context) {
    final insights = BankMonthInsights.fromReport(report);
    final month = '${report['month'] ?? ''}';
    final hasData = insights.transactions.isNotEmpty;
    final selected =
        selectedCategory ??
        (insights.byCategory.containsKey('INSURANCE')
            ? 'INSURANCE'
            : insights.categories.firstOrNull?.category ?? 'REVIEW');
    final cat = insights.byCategory[selected];
    final isCurrent =
        month ==
        '${DateTime.now().year}-${DateTime.now().month.toString().padLeft(2, '0')}';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 14),
        Text(
          detail
              ? categories[selected] ?? selected
              : 'Waar ging ons geld heen?',
          style: Theme.of(
            context,
          ).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 6),
        Text(
          '${accounts.length} rekeningen · interne overboekingen uitgesloten${isCurrent ? ' · lopende maand, nog onvolledig' : ''}',
        ),
        if (history['from'] != null)
          Text(
            'Beschikbare boekingen in de historie: ${history['from']} t/m ${history['to']}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        const SizedBox(height: 18),
        if (!hasData)
          _panel(context, 'Nog geen uitgavenoverzicht', [
            const Text(
              'Voor deze maand zijn geen externe betalingen opgeslagen. Dat bewijst niet dat er niets is uitgegeven. Bekijk een andere maand of werk de bankgegevens bij.',
            ),
          ]),
        if (detail) ...[
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: categories.entries
                .where((e) => e.key != 'INTERNAL')
                .map(
                  (e) => ChoiceChip(
                    label: Text(e.value),
                    selected: e.key == selected,
                    onSelected: (_) => onCategory(e.key),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 18),
          _columns([
            _metric(
              context,
              'Netto in deze categorie',
              cat?.netSpent ?? 0,
              'Uitgaven minus ontvangen bedragen',
            ),
            _metric(
              context,
              'Ontvangen in deze categorie',
              cat?.received ?? 0,
              'Controleer of dit terugbetalingen zijn',
            ),
            if (budgets[selected] != null)
              _metric(
                context,
                'Maandbudget',
                bankCents(budgets[selected]),
                'Zelf ingesteld in jaarplanning',
              )
            else
              _panel(context, 'Maandbudget', [
                const Text(
                  'Nog niet ingesteld. Je kunt dit vastleggen bij Jaarplanning & budget.',
                ),
              ]),
          ]),
          const SizedBox(height: 18),
          if (selected == 'INSURANCE')
            _notice(
              context,
              'Welke polis hoort hierbij?',
              'Je ziet de aanbieder en de bankomschrijving. De precieze verzekering is alleen bekend als die daarin staat. Meerdere polissen bij één aanbieder staan bij elkaar.',
            ),
          if (selected == 'GROCERIES')
            _notice(
              context,
              'Boodschappen per winkel',
              'Bedragen en aantallen komen uit de bankbetalingen. Welke producten je kocht staat niet in een bankafschrift.',
            ),
          if (selected == 'EXTRA')
            _notice(
              context,
              'Extra’s die je bewust kunt kiezen',
              'Alleen betalingen die als extra zijn ingedeeld tellen hier mee. Vakantie heeft een eigen categorie; onbekende aankopen blijven nog te beoordelen.',
            ),
          _trend(context, selected),
          const SizedBox(height: 18),
          _panel(context, 'Aanbieders en betalingen', [
            const Text(
              'Open een aanbieder voor alle betalingen, de oorspronkelijke omschrijving en de gebruikte indeling.',
            ),
            const SizedBox(height: 12),
            if (cat == null) const Text('Geen betalingen in deze selectie.'),
            for (final merchant in cat?.merchants ?? <BankMerchantInsights>[])
              ExpansionTile(
                key: ValueKey('$month:$selected:${merchant.key}'),
                tilePadding: EdgeInsets.zero,
                title: Text(merchant.label),
                subtitle: Text(
                  '${merchant.count} boekingen · netto ${bankMoneyCents(merchant.netSpent)}${merchant.received > 0 ? ' · ontvangen ${bankMoneyCents(merchant.received)}' : ''}',
                ),
                children: merchant.transactions
                    .map((t) => _transaction(context, t))
                    .toList(),
              ),
          ]),
        ] else if (hasData) ...[
          _columns([
            _metric(
              context,
              'Als inkomsten ingedeeld',
              insights.income,
              'Positieve boekingen in Inkomsten',
            ),
            _metric(
              context,
              'Netto uitgegaan',
              insights.netSpent,
              'Bruto ${bankMoneyCents(insights.grossSpent)} − ${bankMoneyCents(insights.refunds)} ontvangen in uitgavencategorieën',
            ),
            _metric(
              context,
              'Verschil ontvangen − betaald',
              insights.cashDifference,
              'Totaal ontvangen ${bankMoneyCents(insights.received)}. Geen vrij besteedbaar saldo.',
            ),
          ]),
          const SizedBox(height: 18),
          _columns([
            _panel(context, 'Netto per categorie', [
              for (final c in insights.categories.where(
                (c) => c.category != 'INCOME' || c.grossSpent > 0,
              ))
                _categoryRow(context, c, insights),
            ]),
            _panel(context, 'Dit verdient aandacht', [
              if (insights.reviewSpent > 0)
                _notice(
                  context,
                  'Nog te beoordelen: ${bankMoneyCents(insights.reviewSpent)}',
                  'Deze uitgaven tellen mee in het totaal, maar nog niet als extra.',
                  onTap: () => onCategory('REVIEW'),
                ),
              if (insights.creditCardSpent > 0)
                _notice(
                  context,
                  'Creditcard: ${bankMoneyCents(insights.creditCardSpent)}',
                  'De afschrijving telt mee. De afzonderlijke aankopen ontbreken nog.',
                  onTap: () => onCategory('CREDIT_CARD'),
                ),
              if (insights.byCategory['PENSION'] != null)
                _notice(
                  context,
                  'Pensioen en sparen staan apart',
                  'Stortingen en opnames zijn geldverplaatsingen, geen consumptie. Ze tellen hier wel mee in wat er netto van je betaalrekeningen afgaat.',
                  onTap: () => onCategory('PENSION'),
                ),
              ..._budgetNotices(context, insights),
              _notice(
                context,
                'Overig / extra: ${bankMoneyCents(insights.byCategory['EXTRA']?.netSpent ?? 0)}',
                'Bekijk de aanbieders en betalingen om te kiezen waarop je wilt besparen.',
                onTap: () => onCategory('EXTRA'),
              ),
              const Text(
                'Vakanties, jaarrekeningen en eenmalige aankopen kun je apart plannen. Een belastingteruggaaf of vakantiegeld krijgt een verwacht bedrag en betaalmaand.',
              ),
            ]),
          ], minWidth: 400),
          const SizedBox(height: 18),
          _trend(context, null),
          const SizedBox(height: 18),
          _panel(context, 'Recente betalingen', [
            ...insights.transactions
                .take(8)
                .map((t) => _transaction(context, t)),
            if (insights.transactions.length > 8)
              const Text(
                'Alle betalingen vind je via de categorieën hierboven.',
              ),
          ]),
        ],
      ],
    );
  }

  List<Widget> _budgetNotices(
    BuildContext context,
    BankMonthInsights insights,
  ) => budgets.entries
      .where(
        (e) =>
            insights.byCategory[e.key] != null &&
            insights.byCategory[e.key]!.netSpent > bankCents(e.value),
      )
      .take(4)
      .map((e) {
        final delta = insights.byCategory[e.key]!.netSpent - bankCents(e.value);
        return _notice(
          context,
          '${categories[e.key] ?? e.key}: ${bankMoneyCents(delta)} boven budget',
          'Ingesteld budget ${bankMoney(e.value as num)} per maand.',
          onTap: () => onCategory(e.key),
        );
      })
      .toList();
  Widget _categoryRow(
    BuildContext context,
    BankCategoryInsights c,
    BankMonthInsights insights,
  ) {
    final value = c.category == 'INCOME' ? c.grossSpent : c.netSpent;
    final maxValue = insights.categories.fold<int>(
      1,
      (a, b) =>
          math.max(a, b.category == 'INCOME' ? b.grossSpent : b.netSpent.abs()),
    );
    return InkWell(
      onTap: () => onCategory(c.category),
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 13),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    c.category == 'INCOME'
                        ? 'Afboekingen bij inkomsten'
                        : categories[c.category] ?? c.category,
                  ),
                ),
                Text(
                  bankMoneyCents(value),
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(width: 5),
                const Icon(Icons.chevron_right, size: 18),
              ],
            ),
            const SizedBox(height: 7),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Container(
                height: 5,
                color: _accent(context).withValues(alpha: .10),
                alignment: Alignment.centerLeft,
                child: FractionallySizedBox(
                  widthFactor: (value.abs() / math.max(maxValue, value.abs()))
                      .clamp(0.0, 1.0),
                  heightFactor: 1,
                  child: ColoredBox(
                    color: value < 0
                        ? Theme.of(context).colorScheme.tertiary
                        : _accent(context),
                  ),
                ),
              ),
            ),
            if (value < 0)
              const Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  'Meer ontvangen dan betaald',
                  style: TextStyle(fontSize: 12),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _transaction(BuildContext context, Map<String, dynamic> t) {
    const sources = {
      'RULE': 'Vaste regel',
      'LEARNED': 'Geleerd van jou',
      'AI': 'AI-indeling',
      'USER': 'Jouw keuze',
      'QUESTION': 'Vraag',
      'INTERNAL': 'Eigen overboeking',
    };
    final account = accounts
        .where((a) => a['id'] == t['accountId'])
        .firstOrNull;
    final iban = '${account?['iban'] ?? ''}';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => onClassify(t),
            child: Text(
              '${t['counterparty'] == '' ? 'Betaling' : t['counterparty']} · ${bankMoney(num.parse('${t['amount']}'))}',
              style: Theme.of(context).textTheme.titleSmall,
            ),
          ),
          Text(
            '${t['date']} · ${categories[t['category']] ?? t['category']}${iban.isEmpty ? '' : ' · rekening …${iban.substring(math.max(0, iban.length - 4))}'}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 5),
          SelectableText('${t['description'] ?? ''}'),
          if ('${t['question'] ?? ''}'.isNotEmpty)
            Text(
              'Vraag: ${t['question']}',
              style: TextStyle(
                color: _accent(context),
                fontWeight: FontWeight.w600,
              ),
            ),
          if ('${t['classificationSource'] ?? ''}'.isNotEmpty)
            Text(
              '${sources[t['classificationSource']] ?? 'Indeling'}${'${t['reason'] ?? ''}'.isEmpty ? '' : ': ${t['reason']}'}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          Wrap(
            spacing: 8,
            children: [
              TextButton(
                onPressed: () => onClassify(t),
                child: Text(
                  t['category'] == 'REVIEW'
                      ? 'Beantwoord / deel in'
                      : 'Categorie aanpassen',
                ),
              ),
              TextButton(
                onPressed: () => onRule(t),
                child: const Text('Regel maken'),
              ),
            ],
          ),
          const Divider(height: 1),
        ],
      ),
    );
  }

  Widget _trend(BuildContext context, String? category) {
    final reports = List<dynamic>.from(history['months'] as List? ?? []);
    if (reports.isEmpty) return const SizedBox.shrink();
    final trends = bankTrends(reports, category: category);
    final maxValue = trends.fold<int>(
      1,
      (a, b) => math.max(a, b.netSpent.abs()),
    );
    return _panel(
      context,
      category == null
          ? 'Netto uitgegaan per maand'
          : 'Deze categorie door de maanden heen',
      [
        const Text(
          'Netto bedragen uit beschikbare boekingen. Een lege maand is geen bewijs van nul uitgaven; eerste en lopende maanden kunnen onvolledig zijn.',
        ),
        const SizedBox(height: 18),
        LayoutBuilder(
          builder: (c, constraints) {
            final columns = constraints.maxWidth < 600 ? 6 : 12;
            return Wrap(
              spacing: 8,
              runSpacing: 16,
              children: trends
                  .map(
                    (t) => SizedBox(
                      width:
                          (constraints.maxWidth - (columns - 1) * 8) / columns,
                      child: Semantics(
                        label:
                            '${t.month}: ${t.hasTransactions ? bankMoneyCents(t.netSpent) : 'geen boekingen'}',
                        child: Column(
                          children: [
                            SizedBox(
                              height: 38,
                              child: Center(
                                child: Text(
                                  t.hasTransactions
                                      ? bankMoneyCents(t.netSpent)
                                      : '—',
                                  textAlign: TextAlign.center,
                                  style: Theme.of(context).textTheme.labelSmall,
                                ),
                              ),
                            ),
                            SizedBox(
                              height: 100,
                              child: Align(
                                alignment: Alignment.bottomCenter,
                                child: Container(
                                  width: 26,
                                  height: t.hasTransactions
                                      ? math.max(
                                          2,
                                          t.netSpent.abs() / maxValue * 100,
                                        )
                                      : 0,
                                  decoration: BoxDecoration(
                                    color: t.netSpent < 0
                                        ? Theme.of(context).colorScheme.tertiary
                                        : t.isCurrentMonth
                                        ? _accent(
                                            context,
                                          ).withValues(alpha: .45)
                                        : _accent(context),
                                    borderRadius: const BorderRadius.vertical(
                                      top: Radius.circular(4),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(height: 7),
                            Text(
                              t.month.substring(2),
                              style: Theme.of(context).textTheme.labelSmall,
                            ),
                            if (t.isCurrentMonth)
                              const Text(
                                'lopend',
                                style: TextStyle(fontSize: 11),
                              ),
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(),
            );
          },
        ),
        const SizedBox(height: 12),
        const Text(
          'Staafhoogte toont de grootte; negatieve bedragen betekenen meer ontvangen dan betaald.',
          style: TextStyle(fontSize: 12),
        ),
      ],
    );
  }
}
