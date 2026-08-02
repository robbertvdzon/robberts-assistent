import 'package:flutter_test/flutter_test.dart';

import 'package:notities/note_versions_screen.dart';

void main() {
  group('formatVersionMoment', () {
    final now = DateTime(2026, 8, 2, 12, 0);

    test('vandaag toont "vandaag" met de tijd', () {
      expect(formatVersionMoment(DateTime(2026, 8, 2, 11, 30), now: now), 'vandaag 11:30');
    });

    test('gisteren toont "gisteren" met de tijd', () {
      expect(formatVersionMoment(DateTime(2026, 8, 1, 9, 5), now: now), 'gisteren 09:05');
    });

    test('ouder toont dag, datum, maand en tijd in het Nederlands', () {
      // 28 juli 2026 is een dinsdag.
      expect(formatVersionMoment(DateTime(2026, 7, 28, 9, 5), now: now), 'di 28 jul 09:05');
      // 27 juli 2026 is een maandag.
      expect(formatVersionMoment(DateTime(2026, 7, 27, 23, 59), now: now), 'ma 27 jul 23:59');
    });

    test('middernacht en enkele cijfers worden met voorloopnul getoond', () {
      expect(formatVersionMoment(DateTime(2026, 8, 2, 0, 4), now: now), 'vandaag 00:04');
    });
  });
}
