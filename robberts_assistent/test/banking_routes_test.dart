import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:robberts_assistent/main.dart';
import 'package:robberts_assistent/banking_screen.dart';
import 'banking_screen_test.dart' show BankApi;

void main() {
  testWidgets(
    'directe banklink en gekozen maand blijven na opnieuw openen behouden',
    (tester) async {
      final api = BankApi();
      late GoRouter router;
      Widget page(Uri uri) => BankingScreen(
        api: api,
        initialMonth: uri.queryParameters['month'],
        onMonthChanged: (month) => router.go('/meer/bankzaken?month=$month'),
      );
      router = createAppRouter(
        initialLocation: '/meer/bankzaken?month=2026-08',
        rootBuilder: page,
      );
      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      await tester.pumpAndSettle();
      expect(find.text('2026-08'), findsOneWidget);
      await tester.tap(find.byTooltip('Vorige maand'));
      await tester.pumpAndSettle();
      expect(
        router.routeInformationProvider.value.uri.toString(),
        '/meer/bankzaken?month=2026-07',
      );
      expect(find.text('2026-07'), findsOneWidget);
      final restoredUri = router.routeInformationProvider.value.uri.toString();
      await tester.pumpWidget(const SizedBox());
      router.dispose();
      router = createAppRouter(initialLocation: restoredUri, rootBuilder: page);
      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      await tester.pumpAndSettle();
      expect(find.text('2026-07'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      router.dispose();
    },
  );
}
