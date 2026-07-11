import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:robberts_assistent/main.dart';

void main() {
  testWidgets('shows the Google login screen when no session is stored', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});

    // Geen pumpAndSettle: de laadstatus toont een onbepaalde CircularProgressIndicator,
    // die altijd nieuwe frames blijft plannen en pumpAndSettle daardoor laat timeouten.
    await tester.pumpWidget(const RobbertsAssistentApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Robberts Assistent'), findsOneWidget);
    expect(find.text('Log in met Google om verder te gaan.'), findsOneWidget);
  });
}
