import 'package:flutter/material.dart';

enum StatusPillVariant { good, warning, critical, neutral }

/// Eén toegankelijke statusweergave: betekenis wordt altijd door kleur én woord overgebracht.
class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.variant, this.label});

  final StatusPillVariant variant;
  final String? label;

  static const _styles = {
    StatusPillVariant.good: (
      background: Color(0xFFE9F6E9),
      foreground: Color(0xFF0A6D0A),
      word: 'goed',
    ),
    StatusPillVariant.warning: (
      background: Color(0xFFFDF3DF),
      foreground: Color(0xFF8A5C05),
      word: 'let op',
    ),
    StatusPillVariant.critical: (
      background: Color(0xFFFBEAEA),
      foreground: Color(0xFFA52C2C),
      word: 'kritiek',
    ),
    StatusPillVariant.neutral: (
      background: Color(0xFFEEF1F4),
      foreground: Color(0xFF4B545E),
      word: 'neutraal',
    ),
  };

  String get statusWord => _styles[variant]!.word;

  @override
  Widget build(BuildContext context) {
    final style = _styles[variant]!;
    return Semantics(
      label: 'Status: ${style.word}',
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
        decoration: BoxDecoration(
          color: style.background,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Text(
          label == null ? style.word : '${style.word} · $label',
          style: TextStyle(
            color: style.foreground,
            fontSize: 12,
            fontWeight: FontWeight.w700,
            height: 1.15,
          ),
        ),
      ),
    );
  }
}

/// Zet het eerste backend-statusbolletje in een regel om naar een woordelijke statuspil.
/// Tekst vóór en na het bolletje blijft inhoudelijk ongewijzigd.
class StatusTextLine extends StatelessWidget {
  const StatusTextLine(this.text, {super.key});

  final String text;

  static const _emojiVariants = {
    '🟢': StatusPillVariant.good,
    '🟡': StatusPillVariant.warning,
    '🔴': StatusPillVariant.critical,
  };

  @override
  Widget build(BuildContext context) {
    final match = RegExp('[🟢🟡🔴]', unicode: true).firstMatch(text);
    if (match == null) return Text(text);
    final emoji = match.group(0)!;
    final before = text.substring(0, match.start).trimRight();
    final after = text.substring(match.end).trimLeft();
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: 6,
      runSpacing: 4,
      children: [
        if (before.isNotEmpty) Text(before),
        StatusPill(variant: _emojiVariants[emoji]!),
        if (after.isNotEmpty) Text(after),
      ],
    );
  }
}
