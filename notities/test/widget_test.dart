import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:notities/main.dart';

void main() {
  testWidgets('shows the Google login screen when no session is stored', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});

    // Geen pumpAndSettle: de laadstatus toont een onbepaalde CircularProgressIndicator,
    // die altijd nieuwe frames blijft plannen en pumpAndSettle daardoor laat timeouten.
    await tester.pumpWidget(const NotitiesApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Notities'), findsOneWidget);
    expect(find.text('Log in met Google om verder te gaan.'), findsOneWidget);

    // Donker thema: zwarte achtergrond, geen geel/amber meer.
    final theme = tester.widget<MaterialApp>(find.byType(MaterialApp)).theme!;
    expect(theme.brightness, Brightness.dark);
    expect(theme.scaffoldBackgroundColor, Colors.black);
  });

  testWidgets('de teksten op het loginscherm zijn leesbaar op donker', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const NotitiesApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    final uitleg = tester.widget<Text>(find.text('Log in met Google om verder te gaan.'));
    expect(uitleg.style?.color, isNot(Colors.black54));
    expect(tester.widget<Icon>(find.byIcon(Icons.edit_note)).color, Colors.white);
  });
}
