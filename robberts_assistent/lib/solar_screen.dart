import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'api_client.dart';
import 'status_pill.dart';

const _weekdays = ['ma', 'di', 'wo', 'do', 'vr', 'za', 'zo'];
const _months = [
  'jan',
  'feb',
  'mrt',
  'apr',
  'mei',
  'jun',
  'jul',
  'aug',
  'sep',
  'okt',
  'nov',
  'dec',
];

String _kwh(double value) =>
    '${value.toStringAsFixed(1).replaceAll('.', ',')} kWh';
String _shortDate(DateTime d) => '${d.day} ${_months[d.month - 1]}';

/// De statuspil die bij het oordeel over de zonnepanelen hoort.
StatusPillVariant solarPillVariant(String verdict) => switch (verdict) {
  'GOED' => StatusPillVariant.good,
  'LET_OP' => StatusPillVariant.warning,
  'NIET' => StatusPillVariant.critical,
  _ => StatusPillVariant.neutral,
};

/// Staafgrafiek van de dagopbrengst met de verwachting bij het weer als lijn eroverheen. Beide
/// zijn kWh en delen dus één as. Tikken of slepen kiest een dag; de waarden staan dan erboven.
class SolarChart extends StatefulWidget {
  const SolarChart({super.key, required this.days, this.height = 160});

  final List<SolarDay> days;
  final double height;

  @override
  State<SolarChart> createState() => _SolarChartState();
}

class _SolarChartState extends State<SolarChart> {
  int? _selected;

  @override
  void didUpdateWidget(SolarChart old) {
    super.didUpdateWidget(old);
    if (old.days.length != widget.days.length) _selected = null;
  }

  void _select(Offset position, double width) {
    final count = widget.days.length;
    if (count == 0) return;
    final plotWidth = width - _SolarPainter.axisWidth;
    final index = ((position.dx - _SolarPainter.axisWidth) / plotWidth * count)
        .floor()
        .clamp(0, count - 1);
    if (index != _selected) setState(() => _selected = index);
  }

  String _describe(SolarDay day) {
    final parts = <String>[
      '${_weekdays[day.date.weekday - 1]} ${_shortDate(day.date)}',
      if (day.yieldKwh != null)
        '${_kwh(day.yieldKwh!)}${day.complete ? '' : ' tot nu toe'}',
      if (day.expectedKwh != null) 'verwacht ${_kwh(day.expectedKwh!)}',
      if (day.sunshineHours != null)
        '${day.sunshineHours!.toStringAsFixed(1).replaceAll('.', ',')} zonuren',
    ];
    return parts.join(' · ');
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final small = Theme.of(context).textTheme.bodySmall;
    final days = widget.days;
    if (days.isEmpty) {
      return Text('Nog geen dagopbrengsten.', style: small);
    }
    final selected = _selected == null ? null : days[_selected!];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          selected == null
              ? 'Tik op een dag voor de waarden'
              : _describe(selected),
          key: const ValueKey('solar-chart-detail'),
          style: small?.copyWith(
            color: selected == null
                ? scheme.onSurfaceVariant
                : scheme.onSurface,
          ),
        ),
        const SizedBox(height: 8),
        LayoutBuilder(
          builder: (context, constraints) => GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTapDown: (d) => _select(d.localPosition, constraints.maxWidth),
            onHorizontalDragUpdate: (d) =>
                _select(d.localPosition, constraints.maxWidth),
            child: CustomPaint(
              key: const ValueKey('solar-chart'),
              size: Size(constraints.maxWidth, widget.height),
              painter: _SolarPainter(
                days: days,
                selected: _selected,
                bar: scheme.primary,
                line: scheme.onSurface,
                grid: scheme.outlineVariant,
                label: small?.copyWith(
                  color: scheme.onSurfaceVariant,
                  fontSize: 11,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 16,
          runSpacing: 4,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    color: scheme.primary,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                const SizedBox(width: 6),
                Text('Opgewekt', style: small),
              ],
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(width: 14, height: 2, color: scheme.onSurface),
                const SizedBox(width: 6),
                Text('Verwacht bij dit weer', style: small),
              ],
            ),
          ],
        ),
      ],
    );
  }
}

class _SolarPainter extends CustomPainter {
  _SolarPainter({
    required this.days,
    required this.selected,
    required this.bar,
    required this.line,
    required this.grid,
    required this.label,
  });

  static const axisWidth = 30.0;
  static const _bottom = 18.0;

  final List<SolarDay> days;
  final int? selected;
  final Color bar;
  final Color line;
  final Color grid;
  final TextStyle? label;

  /// Een ronde bovengrens, zodat de aslabels hele of halve getallen zijn.
  static double niceMax(double value) {
    if (value <= 0) return 1;
    final magnitude = math.pow(10, (math.log(value) / math.ln10).floor());
    for (final step in [1, 2, 4, 5, 10]) {
      if (step * magnitude >= value) return (step * magnitude).toDouble();
    }
    return value;
  }

  void _text(
    Canvas canvas,
    String text,
    Offset at, {
    bool right = false,
    bool center = false,
  }) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: label),
      textDirection: TextDirection.ltr,
    )..layout();
    final dx = right
        ? at.dx - painter.width
        : center
        ? at.dx - painter.width / 2
        : at.dx;
    painter.paint(canvas, Offset(dx, at.dy));
  }

  @override
  void paint(Canvas canvas, Size size) {
    final plot = Rect.fromLTRB(axisWidth, 4, size.width, size.height - _bottom);
    final highest = days.fold<double>(
      0,
      (m, d) => math.max(m, math.max(d.yieldKwh ?? 0, d.expectedKwh ?? 0)),
    );
    final top = niceMax(highest);
    double y(double value) => plot.bottom - value / top * plot.height;

    final gridPaint = Paint()
      ..color = grid
      ..strokeWidth = 1;
    for (final fraction in [0.0, 0.5, 1.0]) {
      final value = top * fraction;
      canvas.drawLine(
        Offset(plot.left, y(value)),
        Offset(plot.right, y(value)),
        gridPaint,
      );
      final text = value == value.roundToDouble()
          ? value.toStringAsFixed(0)
          : value.toStringAsFixed(1).replaceAll('.', ',');
      _text(canvas, text, Offset(plot.left - 6, y(value) - 7), right: true);
    }

    final slot = plot.width / days.length;
    final gap = slot > 6 ? 2.0 : (slot > 3 ? 1.0 : 0.0);
    final radius = Radius.circular(math.min(4, (slot - gap) / 2));
    for (var i = 0; i < days.length; i++) {
      final value = days[i].yieldKwh;
      if (value == null) continue;
      final dimmed = !days[i].complete || (selected != null && selected != i);
      final rect = Rect.fromLTRB(
        plot.left + i * slot + gap / 2,
        y(value),
        plot.left + (i + 1) * slot - gap / 2,
        plot.bottom,
      );
      canvas.drawRRect(
        RRect.fromRectAndCorners(rect, topLeft: radius, topRight: radius),
        Paint()..color = bar.withValues(alpha: dimmed ? 0.4 : 1),
      );
    }

    final linePaint = Paint()
      ..color = line
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeJoin = StrokeJoin.round;
    Path? path;
    for (var i = 0; i < days.length; i++) {
      final value = days[i].expectedKwh;
      if (value == null) {
        if (path != null) canvas.drawPath(path, linePaint);
        path = null;
        continue;
      }
      final point = Offset(plot.left + (i + 0.5) * slot, y(value));
      if (path == null) {
        path = Path()..moveTo(point.dx, point.dy);
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }
    if (path != null) canvas.drawPath(path, linePaint);

    _text(
      canvas,
      _shortDate(days.first.date),
      Offset(plot.left, plot.bottom + 4),
    );
    if (days.length > 14) {
      _text(
        canvas,
        _shortDate(days[days.length ~/ 2].date),
        Offset(plot.left + plot.width / 2, plot.bottom + 4),
        center: true,
      );
    }
    _text(
      canvas,
      _shortDate(days.last.date),
      Offset(plot.right, plot.bottom + 4),
      right: true,
    );
  }

  @override
  bool shouldRepaint(_SolarPainter old) =>
      old.days != days || old.selected != selected || old.bar != bar;
}

/// Grafieken van de zonnepanelen: dagopbrengst naast de verwachting bij het weer, het oordeel
/// over de laatste week en de totalen per maand.
class SolarScreen extends StatefulWidget {
  const SolarScreen({super.key, required this.api, this.initial});

  final ApiClient api;

  /// Het overzicht dat het Health check-scherm al had opgehaald.
  final SolarOverview? initial;

  @override
  State<SolarScreen> createState() => _SolarScreenState();
}

class _SolarScreenState extends State<SolarScreen> {
  late SolarOverview? _overview = widget.initial;
  String? _error;
  int? _period = 30;

  @override
  void initState() {
    super.initState();
    if (_overview == null) _load();
  }

  Future<void> _load() async {
    try {
      final overview = await widget.api.getSolar();
      if (mounted) {
        setState(() {
          _overview = overview;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'De opbrengst kon niet worden geladen.');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final overview = _overview;
    return Scaffold(
      appBar: AppBar(title: const Text('Zonnepanelen')),
      body: overview == null
          ? Center(
              child: _error != null
                  ? Text(_error!)
                  : const CircularProgressIndicator(),
            )
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: EdgeInsets.fromLTRB(
                  16,
                  16,
                  16,
                  16 + MediaQuery.viewPaddingOf(context).bottom,
                ),
                children: _content(overview),
              ),
            ),
    );
  }

  List<Widget> _content(SolarOverview overview) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    final days = _period == null || overview.days.length <= _period!
        ? overview.days
        : overview.days.sublist(overview.days.length - _period!);
    return [
      Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              StatusPill(
                variant: solarPillVariant(overview.verdict),
                label: overview.weekPercent == null
                    ? null
                    : '${overview.weekPercent}% van verwacht',
              ),
              const SizedBox(height: 10),
              Text(overview.verdictText),
              if (overview.currentPowerWatt != null) ...[
                const SizedBox(height: 4),
                Text('Nu: ${overview.currentPowerWatt} W', style: muted),
              ],
            ],
          ),
        ),
      ),
      const SizedBox(height: 16),
      Wrap(
        spacing: 8,
        children: [
          for (final (label, value) in const [
            ('30 dagen', 30),
            ('90 dagen', 90),
            ('Alles', null),
          ])
            ChoiceChip(
              key: ValueKey('solar-period-${value ?? 'alles'}'),
              label: Text(label),
              selected: _period == value,
              onSelected: (_) => setState(() => _period = value),
            ),
        ],
      ),
      const SizedBox(height: 12),
      Text('Opbrengst per dag (kWh)', style: theme.textTheme.titleSmall),
      const SizedBox(height: 8),
      SolarChart(days: days, height: 220),
      const SizedBox(height: 24),
      Text('Per maand', style: theme.textTheme.titleSmall),
      const SizedBox(height: 8),
      _monthTable(overview.days, muted),
      const SizedBox(height: 24),
      Text(
        'De verwachting komt uit je eigen panelen: wat ze de dertig dagen ervoor opleverden per '
        'hoeveelheid diffuus en direct zonlicht, toegepast op het weer van die dag (Open-Meteo). '
        'Het weermodel wijkt per dag flink af van je dak, dus let op het beeld over een week. '
        'Het oordeel slaat aan onder de helft van de verwachting.',
        style: muted,
      ),
    ];
  }

  /// Totalen per maand. De verwachting telt alleen dagen waarvoor er een is, en het percentage
  /// vergelijkt dezelfde dagen, anders zou de eerste maand altijd te goed lijken.
  Widget _monthTable(List<SolarDay> days, TextStyle? muted) {
    final months = <String, List<SolarDay>>{};
    for (final day in days) {
      months
          .putIfAbsent(
            '${_months[day.date.month - 1]} ${day.date.year}',
            () => [],
          )
          .add(day);
    }
    TableRow row(String a, String b, String c, String d, {TextStyle? style}) =>
        TableRow(
          children: [
            for (final (text, right) in [
              (a, false),
              (b, true),
              (c, true),
              (d, true),
            ])
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Text(
                  text,
                  style: style,
                  textAlign: right ? TextAlign.right : TextAlign.left,
                ),
              ),
          ],
        );
    return Table(
      columnWidths: const {0: FlexColumnWidth(1.2)},
      children: [
        row('Maand', 'Opgewekt', 'Zonuren', 'Van verwacht', style: muted),
        for (final entry in months.entries.toList().reversed)
          () {
            final list = entry.value;
            final total = list.fold<double>(0, (s, d) => s + (d.yieldKwh ?? 0));
            final sun = list.fold<double>(
              0,
              (s, d) => s + (d.sunshineHours ?? 0),
            );
            final compared = list.where(
              (d) => d.complete && d.yieldKwh != null && d.expectedKwh != null,
            );
            final expected = compared.fold<double>(
              0,
              (s, d) => s + d.expectedKwh!,
            );
            final actual = compared.fold<double>(0, (s, d) => s + d.yieldKwh!);
            return row(
              entry.key,
              _kwh(total),
              sun.round().toString(),
              expected > 0 ? '${(100 * actual / expected).round()}%' : '–',
            );
          }(),
      ],
    );
  }
}
