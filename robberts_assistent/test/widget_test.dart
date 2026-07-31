import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:robberts_assistent/main.dart';

void main() {
  test('licht thema gebruikt de vastgelegde kleuren en kaartvorm', () {
    final scheme = robbertsAssistentTheme.colorScheme;
    expect(
      robbertsAssistentTheme.scaffoldBackgroundColor,
      const Color(0xFFF6F7F8),
    );
    expect(scheme.surface, const Color(0xFFFFFFFF));
    expect(scheme.onSurface, const Color(0xFF171A1D));
    expect(scheme.onSurfaceVariant, const Color(0xFF6B7480));
    expect(scheme.outline, const Color(0xFFE7EAED));
    expect(scheme.primary, const Color(0xFF0F6E6E));

    final cardTheme = robbertsAssistentTheme.cardTheme;
    expect(cardTheme.elevation, 0);
    final shape = cardTheme.shape! as RoundedRectangleBorder;
    expect(shape.borderRadius, const BorderRadius.all(Radius.circular(16)));
    expect(shape.side, const BorderSide(color: Color(0xFFE7EAED), width: 1));
  });

  testWidgets('shows the Google login screen when no session is stored', (
    WidgetTester tester,
  ) async {
    SharedPreferences.setMockInitialValues({});

    // Geen pumpAndSettle: de laadstatus toont een onbepaalde CircularProgressIndicator,
    // die altijd nieuwe frames blijft plannen en pumpAndSettle daardoor laat timeouten.
    await tester.pumpWidget(const RobbertsAssistentApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text("Robbert's assistent"), findsOneWidget);
    expect(find.text('Log in met Google om verder te gaan.'), findsOneWidget);
    expect(
      find.bySemanticsLabel("Logo van Robbert's assistent"),
      findsOneWidget,
    );
  });
}
