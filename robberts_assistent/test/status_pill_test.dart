import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:robberts_assistent/status_pill.dart';

void main() {
  testWidgets(
    'alle statuspillen tonen het woord en het vastgelegde kleurenpaar',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Column(
              children: [
                StatusPill(variant: StatusPillVariant.good),
                StatusPill(variant: StatusPillVariant.warning),
                StatusPill(variant: StatusPillVariant.critical),
                StatusPill(variant: StatusPillVariant.neutral),
              ],
            ),
          ),
        ),
      );

      const backgrounds = [
        Color(0xFFE9F6E9),
        Color(0xFFFDF3DF),
        Color(0xFFFBEAEA),
        Color(0xFFEEF1F4),
      ];
      const foregrounds = [
        Color(0xFF0A6D0A),
        Color(0xFF8A5C05),
        Color(0xFFA52C2C),
        Color(0xFF4B545E),
      ];
      const words = ['goed', 'let op', 'kritiek', 'neutraal'];

      final pills = find.byType(StatusPill);
      expect(pills, findsNWidgets(4));
      for (var index = 0; index < 4; index++) {
        final container = tester.widget<Container>(
          find.descendant(
            of: pills.at(index),
            matching: find.byType(Container),
          ),
        );
        final decoration = container.decoration! as BoxDecoration;
        expect(decoration.color, backgrounds[index]);
        final text = tester.widget<Text>(
          find.descendant(of: pills.at(index), matching: find.byType(Text)),
        );
        expect(text.data, words[index]);
        expect(text.style?.color, foregrounds[index]);
      }
    },
  );
}
