import 'package:dsh_app/runtime/runtime_state.dart';
import 'package:dsh_app/state/host_root_state.dart';
import 'package:dsh_app/ui/settings/host_persona_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../shell/golden_harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('opening the page shows a saved persona and does not write', (tester) async {
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      calls.add(call);
      if (call.method == 'status') {
        return {
          'rootEnabled': false,
          'chrootEnabled': false,
          'promptOk': true,
          'persona': '升级前留下的人设',
        };
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          localRuntimeProvider.overrideWith(() => FakeRuntime(const LocalRuntimeState(loaded: true))),
        ],
        child: const MaterialApp(home: HostPersonaPage()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('升级前留下的人设'), findsOneWidget);
    expect(calls.where((c) => c.method == 'setPersona'), isEmpty);

    await tester.enterText(find.byType(TextField), '新的人设');
    await tester.tap(find.text('保存'));
    await tester.pump();
    final saved = calls.where((c) => c.method == 'setPersona').single;
    expect((saved.arguments as Map)['text'], '新的人设');
  });
}
