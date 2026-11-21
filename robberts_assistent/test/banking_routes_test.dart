import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:robberts_assistent/main.dart';
import 'package:robberts_assistent/more_screen.dart';
import 'banking_screen_test.dart' show BankApi, revealAndTap;

void main() {
  testWidgets(
    'maand categorie en tab blijven via echte routekoppeling bewaard',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1100, 1600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final api = BankApi();
      late GoRouter router;
      Widget page(Uri uri) => MoreScreen.detail(
        'bankzaken',
        api,
        month: uri.queryParameters['month'],
        location: uri,
        onBankLocationChanged: (params) => router.go(
          Uri(path: '/meer/bankzaken', queryParameters: params).toString(),
        ),
      );
      router = createAppRouter(
        initialLocation: '/meer/bankzaken?month=2026-08',
        rootBuilder: page,
      );
      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      await tester.pumpAndSettle();
      expect(find.text('2026-08'), findsOneWidget);
      await revealAndTap(tester, find.text('Verzekeringen'));
      expect(
        router.routeInformationProvider.value.uri.queryParameters['view'],
        'detail',
      );
      expect(
        router.routeInformationProvider.value.uri.queryParameters['category'],
        'INSURANCE',
      );
      await revealAndTap(tester, find.byTooltip('Vorige maand'));
      final current = router.routeInformationProvider.value.uri;
      expect(current.queryParameters['month'], '2026-07');
      expect(current.queryParameters['view'], 'detail');
      expect(current.queryParameters['category'], 'INSURANCE');
      expect(find.text('2026-07'), findsOneWidget);
      final restored = current.toString();
      await tester.pumpWidget(const SizedBox());
      router.dispose();
      router = createAppRouter(initialLocation: restored, rootBuilder: page);
      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      await tester.pumpAndSettle();
      expect(find.text('2026-07'), findsOneWidget);
      expect(
        tester
            .widget<ChoiceChip>(
              find.widgetWithText(ChoiceChip, 'Categorie uitpluizen'),
            )
            .selected,
        isTrue,
      );
      expect(
        tester
            .widget<ChoiceChip>(
              find.widgetWithText(ChoiceChip, 'Verzekeringen'),
            )
            .selected,
        isTrue,
      );
      expect(find.text('Aanbieders en betalingen'), findsOneWidget);
      expect(api.requests, contains('/api/v1/banking/month?month=2026-07'));
      await tester.pumpWidget(const SizedBox());
      router.dispose();
    },
  );

  testWidgets('directe jaarplanninglink herstelt tab en jaar', (tester) async {
    final api = BankApi();
    late GoRouter router;
    router = createAppRouter(
      initialLocation: '/meer/bankzaken?month=2026-10&view=plan&year=2028',
      rootBuilder: (uri) => MoreScreen.detail(
        'bankzaken',
        api,
        month: uri.queryParameters['month'],
        location: uri,
      ),
    );
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<ChoiceChip>(
            find.widgetWithText(ChoiceChip, 'Jaarplanning & budget'),
          )
          .selected,
      isTrue,
    );
    expect(api.requests, contains('/api/v1/banking/planning?year=2028'));
    expect(find.text('2028'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    router.dispose();
  });
}
