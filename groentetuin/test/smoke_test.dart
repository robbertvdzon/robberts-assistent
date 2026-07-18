import 'package:flutter_test/flutter_test.dart';
import 'package:groentetuin/api_client.dart';

void main() {
  test('ApiClient start zonder sessie-token', () {
    final api = ApiClient();
    expect(api.token, isNull);
  });
}
