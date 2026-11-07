import 'package:dsh_app/state/host_root_state.dart';
import 'package:dsh_app/ui/settings/channel_prompt_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('cancel leaves the stored prompt alone and clear writes an empty string', (tester) async {
    final writes = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      if (call.method == 'readChannelPrompts') return '{"channels":{"a":{"systemPrompt":"已有","useBuiltinPrompt":true}}}';
      if (call.method == 'writeChannelPrompts') {
        writes.add((call.arguments as Map)['json'] as String);
        return true;
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));

    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: ChannelPromptPage(providerId: 'a', defaultText: 'DEFAULT'))),
    );
    await tester.pumpAndSettle();
    expect(find.text('已有'), findsOneWidget);

    await tester.tap(find.text('清空'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(writes, isEmpty);
    expect(find.text('已有'), findsOneWidget);

    await tester.tap(find.text('清空'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('保存').last);
    await tester.pumpAndSettle();
    expect(writes, hasLength(1));
    expect(writes.single, contains('"systemPrompt":""'));
    expect(writes.single, contains('"useBuiltinPrompt":true'));
  });

  testWidgets('turning the switch off does not replace saved text', (tester) async {
    final writes = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      if (call.method == 'readChannelPrompts') return '{"channels":{"a":{"systemPrompt":null,"useBuiltinPrompt":true}}}';
      if (call.method == 'writeChannelPrompts') {
        writes.add((call.arguments as Map)['json'] as String);
        return true;
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));

    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: ChannelPromptPage(providerId: 'a', defaultText: 'DEFAULT'))),
    );
    await tester.pumpAndSettle();
    expect(find.text('DEFAULT'), findsOneWidget);
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(find.text('DEFAULT'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(writes.single, contains('"systemPrompt":null'));
    expect(writes.single, contains('"useBuiltinPrompt":false'));
  });
}
