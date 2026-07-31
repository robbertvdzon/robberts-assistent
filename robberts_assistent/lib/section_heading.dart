import 'package:flutter/material.dart';

/// Consistente kleine sectiekop voor de Vandaag- en Health-checkkaarten.
class SectionHeading extends StatelessWidget {
  const SectionHeading(this.text, {super.key, this.selectable = false});

  final String text;
  final bool selectable;

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(context).textTheme.labelSmall?.copyWith(
      color: Theme.of(context).colorScheme.onSurfaceVariant,
      fontWeight: FontWeight.w700,
      letterSpacing: 1.4,
      height: 1,
    );
    final label = text.toUpperCase();
    return selectable
        ? SelectableText(label, style: style)
        : Text(label, style: style);
  }
}
