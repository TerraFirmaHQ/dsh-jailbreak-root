// Rony fork v5.1: the 活人感 switch really changes the in-app chat behaviour.
// ON : a casual reply streams as a typing indicator, then appears as separate bubbles one after
//      another (typing delay between them); work replies (code / lists) are untouched.
// OFF: exactly the stock single Markdown reply, streamed as text, no bubbles, no delay.

import 'package:dsh_app/rony/humanlike.dart';
import 'package:dsh_app/rony/rony_channel.dart';
import 'package:dsh_app/ui/chat/render/markdown_body.dart';
import 'package:dsh_app/ui/chat/widgets/assistant_message.dart';
import 'package:dsh_app/ui/common/typing_indicator.dart';
import 'package:dsh_client/dsh_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../chat/support/harness.dart';

const casual = '在的在的～\n\n今天辛苦啦，先歇会儿。\n\n要不要我帮你把明天的事理一理？';

AssistantChatItem item(String text, {bool streaming = false}) =>
    AssistantChatItem(id: 'a1', rowKey: 'r1', seq: streaming ? null : 1, text: text, reasoning: '', streaming: streaming, interrupted: false);

Future<void> pumpReply(WidgetTester tester, AssistantChatItem it) =>
    tester.pumpWidget(testApp(Scaffold(body: SingleChildScrollView(child: AssistantMessage(item: it)))));

void main() {
  tearDown(() => ronyHumanlike.value = true);

  group('split rules', () {
    test('casual paragraphs split into bubbles', () {
      expect(humanlikeSplit(casual), ['在的在的～', '今天辛苦啦，先歇会儿。', '要不要我帮你把明天的事理一理？']);
    });
    test('work replies are never split', () {
      expect(humanlikeSplit('看这里：\n\n```dart\nprint(1);\n```'), isNull);
      expect(humanlikeSplit('步骤：\n\n- 第一步\n- 第二步'), isNull);
      expect(humanlikeSplit('## 标题\n\n正文'), isNull);
      expect(humanlikeSplit('一段话'), isNull);
      expect(humanlikeSplit(List.filled(5, '很长的一段' * 30).join('\n\n')), isNull);
    });
    test('more than four paragraphs fold into the last bubble', () {
      expect(humanlikeSplit('a\n\nb\n\nc\n\nd\n\ne')!.length, kHumanlikeMaxBubbles);
    });
    test('typing delay grows with length, bounded', () {
      expect(humanlikeDelay('x', 0), Duration.zero);
      expect(humanlikeDelay('x', 1), const Duration(milliseconds: 500));
      expect(humanlikeDelay('长' * 200, 1), const Duration(milliseconds: 1800));
    });
  });

  testWidgets('ON: streaming casual reply shows typing, then bubbles appear one by one', (tester) async {
    ronyHumanlike.value = true;
    await pumpReply(tester, item('在的在的～\n\n今天', streaming: true));
    expect(find.byKey(const ValueKey('humanlike-streaming')), findsOneWidget);
    expect(find.byType(MarkdownBody), findsNothing);

    await pumpReply(tester, item(casual));
    await tester.pump();
    expect(find.byKey(const ValueKey('humanlike-bubble-0')), findsOneWidget);
    expect(find.byKey(const ValueKey('humanlike-bubble-1')), findsNothing);
    expect(find.byKey(const ValueKey('humanlike-typing')), findsOneWidget);
    await tester.pump(humanlikeDelay('今天辛苦啦，先歇会儿。', 1));
    expect(find.byKey(const ValueKey('humanlike-bubble-1')), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    expect(find.byKey(const ValueKey('humanlike-bubble-2')), findsOneWidget);
    expect(find.byKey(const ValueKey('humanlike-typing')), findsNothing);
    expect(find.byType(MarkdownBody), findsNWidgets(3));
  });

  testWidgets('ON: an old reply (not seen streaming) shows all bubbles at once', (tester) async {
    ronyHumanlike.value = true;
    await pumpReply(tester, item(casual));
    await tester.pump();
    expect(find.byType(MarkdownBody), findsNWidgets(3));
    expect(find.byKey(const ValueKey('humanlike-typing')), findsNothing);
  });

  testWidgets('OFF: stock behaviour — streamed text, one Markdown body, no bubbles, no typing', (tester) async {
    ronyHumanlike.value = false;
    await pumpReply(tester, item('在的在的～\n\n今天', streaming: true));
    expect(find.byKey(const ValueKey('humanlike-streaming')), findsNothing);
    expect(find.byType(MarkdownBody), findsOneWidget);

    await pumpReply(tester, item(casual));
    await tester.pump();
    expect(find.byType(MarkdownBody), findsOneWidget);
    expect(find.byKey(const ValueKey('humanlike-bubble-0')), findsNothing);
    expect(find.byKey(const ValueKey('humanlike-typing')), findsNothing);
    expect(find.byType(TypingIndicator), findsNothing);
  });

  testWidgets('ON: code replies stream and render exactly like OFF', (tester) async {
    ronyHumanlike.value = true;
    const code = '改这里：\n\n```dart\nprint(1);\n```';
    await pumpReply(tester, item(code, streaming: true));
    expect(find.byKey(const ValueKey('humanlike-streaming')), findsNothing);
    await pumpReply(tester, item(code));
    await tester.pump();
    expect(find.byType(MarkdownBody), findsOneWidget);
  });

  testWidgets('flipping the switch re-renders at once', (tester) async {
    ronyHumanlike.value = true;
    await pumpReply(tester, item(casual));
    await tester.pump();
    expect(find.byType(MarkdownBody), findsNWidgets(3));
    ronyHumanlike.value = false;
    await tester.pump();
    expect(find.byType(MarkdownBody), findsOneWidget);
  });
}
