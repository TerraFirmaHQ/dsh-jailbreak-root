import 'dart:convert';
import 'dart:typed_data';

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

  testWidgets('import replaces the draft only after confirm and save', (tester) async {
    final writes = <String>[];
    var stored = '{"channels":{"a":{"systemPrompt":"已有","useBuiltinPrompt":true}}}';
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      if (call.method == 'readChannelPrompts') return stored;
      if (call.method == 'writeChannelPrompts') {
        final json = (call.arguments as Map)['json'] as String;
        writes.add(json);
        stored = json;
        return true;
      }
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));

    Future<void> open() async {
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            home: ChannelPromptPage(
              providerId: 'a',
              defaultText: 'DEFAULT',
              pick: () async => PickedPromptFile(name: '笔记.MD', bytes: Uint8List.fromList(utf8.encode('导入🙂\r\n'))),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    await open();
    expect(tester.widget<Switch>(find.byType(Switch)).value, isTrue);
    await tester.tap(find.text('导入文件'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(find.text('已有'), findsOneWidget);
    expect(writes, isEmpty);

    await tester.tap(find.text('导入文件'));
    await tester.pumpAndSettle();
    expect(find.text('导入🙂\r\n'), findsWidgets);
    await tester.tap(find.text('替换'));
    await tester.pumpAndSettle();
    expect(find.text('已有'), findsNothing);
    expect(tester.widget<Switch>(find.byType(Switch)).value, isTrue);
    expect(writes, isEmpty);

    await tester.pumpWidget(const SizedBox.shrink());
    await open();
    expect(find.text('已有'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await open();
    await tester.tap(find.text('导入文件'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('替换'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(writes.single, contains(r'导入🙂\r\n'));
    expect(writes.single, contains('"useBuiltinPrompt":true'));
  });

  testWidgets('a bad import and a cancelled empty file leave the saved text', (tester) async {
    final writes = <String>[];
    var next = PickedPromptFile(name: 'bad.txt', bytes: Uint8List.fromList([0xEF, 0xBB, 0xBF, 0x61]));
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
      ProviderScope(
        child: MaterialApp(
          home: ChannelPromptPage(providerId: 'a', defaultText: 'DEFAULT', pick: () async => next),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('导入文件'));
    await tester.pumpAndSettle();
    expect(find.text('文件以 UTF-8 BOM 开头，没有改草稿。'), findsOneWidget);
    expect(find.text('已有'), findsOneWidget);
    expect(writes, isEmpty);

    next = PickedPromptFile(name: 'empty.TXT', bytes: Uint8List(0));
    await tester.tap(find.text('导入文件'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(find.text('已有'), findsOneWidget);
    expect(writes, isEmpty);
  });
}
