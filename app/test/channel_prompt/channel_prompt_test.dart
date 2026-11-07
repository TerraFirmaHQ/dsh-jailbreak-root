import 'dart:convert';
import 'dart:typed_data';

import 'package:dsh_app/channel_prompt/channel_prompt.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const fallback = 'DEFAULT\r\n正文';

  ChannelPromptRecord? record(String? text, bool builtin) =>
      text == null && builtin ? null : ChannelPromptRecord(systemPrompt: text, useBuiltinPrompt: builtin);

  test('null falls back only while the switch is on', () {
    expect(effectiveChannelPrompt(null, fallback), fallback);
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: null, useBuiltinPrompt: true), fallback), fallback);
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: null, useBuiltinPrompt: false), fallback), isNull);
  });

  test('empty stays empty and a saved string ignores the switch', () {
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: '', useBuiltinPrompt: true), fallback), isNull);
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: '', useBuiltinPrompt: false), fallback), isNull);
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: '  ', useBuiltinPrompt: false), fallback), '  ');
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: '自定', useBuiltinPrompt: false), fallback), '自定');
    expect(effectiveChannelPrompt(const ChannelPromptRecord(systemPrompt: fallback, useBuiltinPrompt: false), fallback), fallback);
  });

  test('status prefers the off switch, then empty, default, custom', () {
    expect(channelPromptStatus(null, fallback), ChannelPromptStatus.defaultText);
    expect(channelPromptStatus(const ChannelPromptRecord(useBuiltinPrompt: false), fallback), ChannelPromptStatus.builtinOff);
    expect(channelPromptStatus(const ChannelPromptRecord(systemPrompt: '自定', useBuiltinPrompt: false), fallback), ChannelPromptStatus.builtinOff);
    expect(channelPromptStatus(const ChannelPromptRecord(systemPrompt: ''), fallback), ChannelPromptStatus.empty);
    expect(channelPromptStatus(const ChannelPromptRecord(systemPrompt: fallback), fallback), ChannelPromptStatus.defaultText);
    expect(channelPromptStatus(const ChannelPromptRecord(systemPrompt: '自定'), fallback), ChannelPromptStatus.custom);
  });

  test('validation blocks BOM, a blank first line and a closing fence without rewriting', () {
    const bom = '\uFEFF你好';
    const blank = '\n第二行';
    const fence = '正文```';
    const crlf = '首行\r\n尾部  ';
    expect(validateChannelPrompt(''), isNull);
    expect(validateChannelPrompt(bom), ChannelPromptIssue.bom);
    expect(validateChannelPrompt(blank), ChannelPromptIssue.emptyFirstLine);
    expect(validateChannelPrompt(fence), ChannelPromptIssue.endsWithFence);
    expect(validateChannelPrompt(crlf), isNull);
    expect(crlf.endsWith('  '), isTrue);
    expect(crlf.split('\n').first, '首行\r');
  });

  test('a long prompt with an emoji round-trips and an empty string stays empty', () {
    final long = '${'汉' * 5000}🙂\r\n';
    final doc = ChannelPromptDocument({
      'a': ChannelPromptRecord(systemPrompt: long, api: 'openai-completions'),
      'b': const ChannelPromptRecord(systemPrompt: '', useBuiltinPrompt: false),
      'c': const ChannelPromptRecord(systemPrompt: null, useBuiltinPrompt: true),
    });
    final again = ChannelPromptDocument.decode(doc.encode());
    expect(again['a']!.systemPrompt, long);
    expect(again['b']!.systemPrompt, '');
    expect(again['b']!.useBuiltinPrompt, isFalse);
    expect(again['c']!.systemPrompt, isNull);
    expect(again['c']!.useBuiltinPrompt, isTrue);
    expect(again.encode(), doc.encode());
  });

  test('missing fields migrate to null and builtin on', () {
    final doc = ChannelPromptDocument.decode('{"channels":{"old":{}}}');
    expect(doc['old']!.systemPrompt, isNull);
    expect(doc['old']!.useBuiltinPrompt, isTrue);
    expect(effectiveChannelPrompt(doc['old'], fallback), fallback);
    expect(doc['absent'], isNull);
  });

  test('two channels do not share a request body', () {
    const tool = 'Check the [exit code: N] marker on every bash result; investigate failures before moving on.';
    const history = [
      {'role': 'system', 'content': 'OLD-GLOBAL'},
      {'role': 'system', 'content': 'USER-NOTE'},
      {'role': 'system', 'content': tool},
      {'role': 'user', 'content': 'hi'},
    ];
    final before = jsonEncode(history);
    final a = projectRequest(history, 'PROMPT-A', 'openai-completions', replacedGlobal: 'OLD-GLOBAL');
    final b = projectRequest(history, null, 'openai-completions', replacedGlobal: 'OLD-GLOBAL');
    expect(jsonEncode(history), before);
    expect(a.messages.first['content'], 'PROMPT-A');
    expect(a.messages.map((m) => m['content']), containsAll(['USER-NOTE', tool, 'hi']));
    expect(a.messages.map((m) => m['content']), isNot(contains('OLD-GLOBAL')));
    expect(a.messages.map((m) => m['content']).join(), isNot(contains('PROMPT-B')));
    expect(b.messages.map((m) => m['content']), containsAll(['USER-NOTE', tool, 'hi']));
    expect(b.messages.map((m) => m['content']), isNot(contains('OLD-GLOBAL')));
    final anthropic = projectRequest(history, 'PROMPT-A', 'anthropic-messages');
    expect(anthropic.system, 'PROMPT-A');
    expect(anthropic.messages.any((m) => m['role'] == 'user' && m['content'] == 'hi'), isTrue);
    expect(anthropic.messages.any((m) => m['content'] == 'USER-NOTE'), isTrue);
    final responses = projectRequest(history, 'PROMPT-A', 'openai-responses', replacedGlobal: 'OLD-GLOBAL');
    expect(responses.instructions, 'PROMPT-A');
    expect(responses.messages.map((m) => m['content']), containsAll(['USER-NOTE', tool, 'hi']));
    expect(responses.messages.map((m) => m['content']), isNot(contains('OLD-GLOBAL')));
  });

  test('chat completions merge system sections in order and drop the old global prompt', () {
    final sections = [
      const PromptSection('dsh:builtin-prompt', 'OLD'),
      const PromptSection('identity', 'You are an AI agent powered by DeepSeek Harness. stay out'),
      const PromptSection('dsh:persona', 'PERSONA'),
      const PromptSection('tools', 'call the tool'),
      const PromptSection('blank', ''),
    ];
    final before = sections.map((s) => s.text).join('|');
    final merged = rewriteSections(sections, 'CHANNEL', 'openai-completions');
    expect(sections.map((s) => s.text).join('|'), before);
    expect(merged, hasLength(1));
    expect(merged.single.text, 'CHANNEL\n\nPERSONA\n\ncall the tool');
    final native = rewriteSections(sections, null, 'anthropic-messages');
    expect(native.map((s) => s.text), ['PERSONA', 'call the tool']);
  });

  test('txt and md imports keep bytes and reject a bad file without rewriting it', () {
    final good = readPromptFile(name: r'C:\下载\笔记.TXT', bytes: Uint8List.fromList(utf8.encode('你好🙂\r\n第二行')));
    expect(good.text, '你好🙂\r\n第二行');
    expect(good.text!.contains('下载'), isFalse);
    expect(readPromptFile(name: 'A.MD', bytes: Uint8List.fromList(utf8.encode('md'))).text, 'md');
    expect(readPromptFile(name: 'empty.md', bytes: Uint8List(0)).text, '');
    final long = '${'汉' * 5000}🙂';
    expect(readPromptFile(name: 'long.txt', bytes: Uint8List.fromList(utf8.encode(long))).text, long);
    expect(readPromptFile(name: 'x.markdown', bytes: Uint8List.fromList(utf8.encode('a'))).issue, PromptFileIssue.wrongType);
    expect(readPromptFile(name: 'a.txt', bytes: Uint8List.fromList([0xEF, 0xBB, 0xBF, 0x61])).issue, PromptFileIssue.bom);
    expect(readPromptFile(name: 'a.txt', bytes: Uint8List.fromList([0xFF, 0x61])).issue, PromptFileIssue.utf8);
    expect(readPromptFile(name: 'a.txt', error: 'denied').issue, PromptFileIssue.unreadable);
    expect(readPromptFile(name: 'a.txt', bytes: Uint8List.fromList(utf8.encode('\n第二行'))).issue, PromptFileIssue.emptyFirstLine);
    expect(readPromptFile(name: 'a.md', bytes: Uint8List.fromList(utf8.encode('正文```'))).issue, PromptFileIssue.fence);
  });

  test('the shipped default file is unchanged', () {
    final bytes = utf8.encode(fallback);
    expect(utf8.decode(bytes), fallback);
  });
}
