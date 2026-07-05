import 'package:flutter_test/flutter_test.dart';
import 'package:wind/wind_data.dart';

void main() {
  group('WindData', () {
    test('windsnelheid-antwoord is niet leeg en noemt een eenheid', () {
      expect(WindData.windSpeedAnswer, isNotEmpty);
      expect(WindData.windSpeedAnswer.toLowerCase(), contains('kilometer'));
    });

    test('voorspelling-antwoord is niet leeg', () {
      expect(WindData.forecastAnswer, isNotEmpty);
      expect(WindData.forecastAnswer.toLowerCase(), contains('verwachting'));
    });

    test('antwoorden zijn onderling verschillend', () {
      expect(WindData.windSpeedAnswer, isNot(equals(WindData.forecastAnswer)));
    });
  });
}
