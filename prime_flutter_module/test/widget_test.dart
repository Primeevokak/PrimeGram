import 'package:flutter_test/flutter_test.dart';

import 'package:prime_flutter_module/main.dart';

void main() {
  testWidgets('Hello Flutter screen renders', (WidgetTester tester) async {
    await tester.pumpWidget(const PrimeFlutterApp());
    expect(find.text('Hello Flutter'), findsOneWidget);
  });
}
