import 'dart:async';

import 'package:dsh_app/state/host_root_state.dart';
import 'package:dsh_app/ui/settings/host_root_approval.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('the approval dialog answers the same event the tool emits', (tester) async {
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      calls.add(call);
      return true;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));

    final events = StreamController<dynamic>();
    addTearDown(events.close);
    await tester.pumpWidget(
      MaterialApp(
        home: HostRootApprovalHost(
          events: events.stream,
          child: const SizedBox.shrink(),
        ),
      ),
    );
    events.add({
      'type': 'approval',
      'id': 'a1',
      'preview': '这会改动隐藏、模块或 root 运行环境。\n\nSIMULATED write /data/adb/modules/hma/module.prop',
      'protected': true,
      'command': 'redacted',
    });
    await tester.pumpAndSettle();
    expect(find.text('允许这条 root 命令？'), findsOneWidget);
    expect(find.textContaining('/data/adb'), findsOneWidget);
    await tester.tap(find.text('允许'));
    await tester.pumpAndSettle();
    expect(calls, hasLength(1));
    expect(calls.single.method, 'resolveApproval');
    final args = calls.single.arguments as Map;
    expect(args['id'], 'a1');
    expect(args['accept'], isTrue);
    expect(find.text('允许这条 root 命令？'), findsNothing);
  });

  testWidgets('deny resolves the same id as not accepted', (tester) async {
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      calls.add(call);
      return true;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));
    final events = StreamController<dynamic>();
    addTearDown(events.close);
    await tester.pumpWidget(
      MaterialApp(home: HostRootApprovalHost(events: events.stream, child: const SizedBox.shrink())),
    );
    events.add({
      'type': 'approval',
      'id': 'a2',
      'preview': '这条命令会在安卓主机上以 uid 0 执行。\n\nSIMULATED echo hi',
      'protected': false,
    });
    await tester.pumpAndSettle();
    await tester.tap(find.text('拒绝'));
    await tester.pumpAndSettle();
    final args = calls.single.arguments as Map;
    expect(args['id'], 'a2');
    expect(args['accept'], isFalse);
    expect(args['session'], isFalse);
  });

  testWidgets('session remember is a third button only when the event says so', (tester) async {
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, (call) async {
      calls.add(call);
      return true;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(HostRootController.methods, null));
    final events = StreamController<dynamic>();
    addTearDown(events.close);
    await tester.pumpWidget(
      MaterialApp(home: HostRootApprovalHost(events: events.stream, child: const SizedBox.shrink())),
    );
    events.add({
      'type': 'approval',
      'id': 'a3',
      'preview': '这条命令会在安卓主机上以 uid 0 执行，不是沙箱。\nid &\nid  read\n全部子命令均为只读，被复合规则拦截',
      'session': true,
      'command': 'id &',
    });
    await tester.pumpAndSettle();
    expect(find.text('本会话不再询问'), findsOneWidget);
    await tester.tap(find.text('本会话不再询问'));
    await tester.pumpAndSettle();
    final args = calls.single.arguments as Map;
    expect(args['accept'], isTrue);
    expect(args['session'], isTrue);

    events.add({
      'type': 'approval',
      'id': 'a4',
      'preview': 'setprop debug.t 1  write',
      'session': false,
      'command': 'setprop debug.t 1',
    });
    await tester.pumpAndSettle();
    expect(find.text('本会话不再询问'), findsNothing);
    expect(find.text('允许'), findsOneWidget);
  });
}
