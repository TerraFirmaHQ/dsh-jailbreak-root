import 'dart:convert';
import 'dart:typed_data';

/// Channel system prompt.
///
/// [systemPrompt] null means the channel was never given a text. An empty
/// string means the user cleared it. The two are not collapsed, trimmed, or
/// treated as the same falsy value.

enum ChannelPromptStatus { builtinOff, empty, defaultText, custom }

enum ChannelPromptIssue { bom, emptyFirstLine, endsWithFence }

class ChannelPromptRecord {
  const ChannelPromptRecord({this.systemPrompt, this.useBuiltinPrompt = true, this.api});

  final String? systemPrompt;
  final bool useBuiltinPrompt;
  final String? api;
}

class ChannelPromptDocument {
  const ChannelPromptDocument(this.channels, {this.loaded = true});

  static const empty = ChannelPromptDocument({});
  static const unloaded = ChannelPromptDocument({}, loaded: false);

  final Map<String, ChannelPromptRecord> channels;
  final bool loaded;

  ChannelPromptRecord? operator [](String id) => channels[id];

  String encode() {
    final body = <String, Object?>{};
    final keys = channels.keys.toList()..sort();
    for (final id in keys) {
      final record = channels[id]!;
      body[id] = {
        'systemPrompt': record.systemPrompt,
        'useBuiltinPrompt': record.useBuiltinPrompt,
        if (record.api != null) 'api': record.api,
      };
    }
    return '{"channels":${_json(body)}}';
  }

  static ChannelPromptDocument decode(String raw) {
    if (raw.isEmpty) return empty;
    final parsed = _decode(raw);
    if (parsed is! Map) return empty;
    final rows = parsed['channels'];
    if (rows is! Map) return empty;
    final channels = <String, ChannelPromptRecord>{};
    for (final entry in rows.entries) {
      final row = entry.value;
      if (row is! Map) continue;
      final hasText = row.containsKey('systemPrompt');
      final textValue = row['systemPrompt'];
      final String? text;
      if (!hasText || textValue == null) {
        text = null;
      } else if (textValue is String) {
        text = textValue;
      } else {
        text = null;
      }
      final builtin = row.containsKey('useBuiltinPrompt') ? row['useBuiltinPrompt'] == true : true;
      final api = row['api'];
      channels['${entry.key}'] = ChannelPromptRecord(
        systemPrompt: text,
        useBuiltinPrompt: builtin,
        api: api is String ? api : null,
      );
    }
    return ChannelPromptDocument(channels);
  }

  ChannelPromptDocument put(String id, ChannelPromptRecord record) =>
      ChannelPromptDocument({...channels, id: record}, loaded: loaded);
}

/// null and the builtin switch on: the default text.
/// null and the switch off: nothing.
/// any non-empty string: that string, whatever the switch says.
/// "": nothing, whatever the switch says.
String? effectiveChannelPrompt(ChannelPromptRecord? record, String defaultText) {
  if (record == null || record.systemPrompt == null) {
    return record?.useBuiltinPrompt == false ? null : defaultText;
  }
  if (record.systemPrompt!.isEmpty) return null;
  return record.systemPrompt;
}

ChannelPromptStatus channelPromptStatus(ChannelPromptRecord? record, String defaultText) {
  if (record?.useBuiltinPrompt == false) return ChannelPromptStatus.builtinOff;
  final stored = record?.systemPrompt;
  if (stored != null && stored.isEmpty) return ChannelPromptStatus.empty;
  if (stored == null || stored == defaultText) return ChannelPromptStatus.defaultText;
  return ChannelPromptStatus.custom;
}

enum PromptFileIssue { wrongType, unreadable, bom, utf8, emptyFirstLine, fence }

class PromptFileRead {
  const PromptFileRead({this.text, this.issue});

  /// Exact file bytes as text. Null when [issue] is set. Never the path.
  final String? text;
  final PromptFileIssue? issue;
}

/// Reads a picked `.txt` or `.md`. MIME is ignored. The name is only the
/// extension. Empty bytes are an explicit empty draft, not a missing file.
PromptFileRead readPromptFile({required String name, Uint8List? bytes, Object? error}) {
  if (error != null || bytes == null) return const PromptFileRead(issue: PromptFileIssue.unreadable);
  final base = _baseName(name);
  final dot = base.lastIndexOf('.');
  final ext = dot < 0 ? '' : base.substring(dot + 1).toLowerCase();
  if (ext != 'txt' && ext != 'md') return const PromptFileRead(issue: PromptFileIssue.wrongType);
  if (bytes.isEmpty) return const PromptFileRead(text: '');
  if (bytes.length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) {
    return const PromptFileRead(issue: PromptFileIssue.bom);
  }
  final String text;
  try {
    text = utf8.decode(bytes);
  } on FormatException {
    return const PromptFileRead(issue: PromptFileIssue.utf8);
  }
  final issue = validateChannelPrompt(text);
  return switch (issue) {
    ChannelPromptIssue.bom => const PromptFileRead(issue: PromptFileIssue.bom),
    ChannelPromptIssue.emptyFirstLine => const PromptFileRead(issue: PromptFileIssue.emptyFirstLine),
    ChannelPromptIssue.endsWithFence => const PromptFileRead(issue: PromptFileIssue.fence),
    null => PromptFileRead(text: text),
  };
}

String _baseName(String name) {
  final normalized = name.replaceAll('\\', '/');
  final slash = normalized.lastIndexOf('/');
  return slash < 0 ? normalized : normalized.substring(slash + 1);
}

/// User text check. Empty is the explicit clear and is valid. Nothing is rewritten.
ChannelPromptIssue? validateChannelPrompt(String text) {
  if (text.isEmpty) return null;
  if (text.startsWith('\uFEFF')) return ChannelPromptIssue.bom;
  final first = text.split('\n').first;
  if (first.isEmpty) return ChannelPromptIssue.emptyFirstLine;
  if (text.endsWith('```')) return ChannelPromptIssue.endsWithFence;
  return null;
}

class PromptSection {
  const PromptSection(this.name, this.text);

  final String name;
  final String text;
}

const harnessIdentityMark = 'You are an AI agent powered by DeepSeek Harness.';

const _droppedSections = {'dsh:builtin-prompt', 'deployment:persona-prefix', 'identity'};

bool _keepSection(PromptSection section) {
  if (section.text.isEmpty) return false;
  if (_droppedSections.contains(section.name)) return false;
  if (section.name == 'dsh:persona' || section.name == 'dsh:channel-prompt') return true;
  if (section.text.startsWith(harnessIdentityMark)) return false;
  return true;
}

/// Replaces the old global chat prompt. Tool sections and the editable persona stay.
/// OpenAI chat completions become one system string, in the same order, with no empty entry.
List<PromptSection> rewriteSections(List<PromptSection> sections, String? channelText, String? api) {
  final kept = <PromptSection>[for (final section in sections) if (_keepSection(section)) PromptSection(section.name, section.text)];
  if (channelText != null && channelText.isNotEmpty) {
    kept.insert(0, PromptSection('dsh:channel-prompt', channelText));
  }
  if (api == 'openai-completions') {
    if (kept.isEmpty) return const [];
    return [PromptSection('dsh:system', kept.map((section) => section.text).join('\n\n'))];
  }
  return kept;
}

class ProjectedRequest {
  const ProjectedRequest({this.system, this.instructions, this.messages = const []});

  final String? system;
  final String? instructions;
  final List<Map<String, Object?>> messages;
}

/// A new request. [history] is not written.
/// Only a system row whose content equals [replacedGlobal] is left out of the copy.
/// A user system note or a tool instruction stays.
ProjectedRequest projectRequest(
  List<Map<String, Object?>> history,
  String? systemText,
  String? api, {
  String? replacedGlobal,
}) {
  final rest = <Map<String, Object?>>[
    for (final message in history)
      if (!(message['role'] == 'system' && replacedGlobal != null && message['content'] == replacedGlobal))
        Map<String, Object?>.from(message),
  ];
  final send = systemText != null && systemText.isNotEmpty;
  if (api == 'anthropic-messages') {
    return ProjectedRequest(system: send ? systemText : null, messages: rest);
  }
  if (api == 'openai-responses') {
    return ProjectedRequest(instructions: send ? systemText : null, messages: rest);
  }
  return ProjectedRequest(
    messages: [
      if (send) {'role': 'system', 'content': systemText},
      ...rest,
    ],
  );
}

String _json(Object? value) {
  if (value == null) return 'null';
  if (value is bool) return value ? 'true' : 'false';
  if (value is String) return _string(value);
  if (value is Map) {
    final keys = value.keys.map((key) => '$key').toList()..sort();
    return '{${keys.map((key) => '${_string(key)}:${_json(value[key])}').join(',')}}';
  }
  return 'null';
}

String _string(String text) {
  final buffer = StringBuffer('"');
  for (final code in text.runes) {
    final char = String.fromCharCode(code);
    switch (char) {
      case '\\':
        buffer.write(r'\\');
      case '"':
        buffer.write(r'\"');
      case '\n':
        buffer.write(r'\n');
      case '\r':
        buffer.write(r'\r');
      case '\t':
        buffer.write(r'\t');
      default:
        if (code < 0x20) {
          buffer.write('\\u${code.toRadixString(16).padLeft(4, '0')}');
        } else {
          buffer.write(char);
        }
    }
  }
  buffer.write('"');
  return buffer.toString();
}

Object? _decode(String raw) {
  // The document is JSON this file writes. A hand edit that is not JSON
  // reads as empty rather than as a prompt.
  try {
    return _Json(raw).value();
  } on FormatException {
    return null;
  }
}

class _Json {
  _Json(this.source);
  final String source;
  int i = 0;

  Object? value() {
    _ws();
    final out = _any();
    _ws();
    return out;
  }

  Object? _any() {
    _ws();
    if (i >= source.length) throw const FormatException('end');
    final char = source[i];
    if (char == '{') return _map();
    if (char == '[') return _list();
    if (char == '"') return _str();
    if (char == 't') return _lit('true', true);
    if (char == 'f') return _lit('false', false);
    if (char == 'n') return _lit('null', null);
    throw const FormatException('value');
  }

  Map<String, Object?> _map() {
    i++;
    final out = <String, Object?>{};
    _ws();
    if (_eat('}')) return out;
    while (true) {
      final key = _str();
      _ws();
      if (!_eat(':')) throw const FormatException('colon');
      out[key] = _any();
      _ws();
      if (_eat('}')) return out;
      if (!_eat(',')) throw const FormatException('comma');
    }
  }

  List<Object?> _list() {
    i++;
    final out = <Object?>[];
    _ws();
    if (_eat(']')) return out;
    while (true) {
      out.add(_any());
      _ws();
      if (_eat(']')) return out;
      if (!_eat(',')) throw const FormatException('comma');
    }
  }

  String _str() {
    if (!_eat('"')) throw const FormatException('string');
    final buffer = StringBuffer();
    while (i < source.length) {
      final char = source[i++];
      if (char == '"') return buffer.toString();
      if (char != '\\') {
        buffer.write(char);
        continue;
      }
      if (i >= source.length) break;
      final esc = source[i++];
      switch (esc) {
        case '\\':
        case '"':
        case '/':
          buffer.write(esc);
        case 'n':
          buffer.write('\n');
        case 'r':
          buffer.write('\r');
        case 't':
          buffer.write('\t');
        case 'u':
          if (i + 4 > source.length) throw const FormatException('u');
          buffer.writeCharCode(int.parse(source.substring(i, i + 4), radix: 16));
          i += 4;
        default:
          throw const FormatException('escape');
      }
    }
    throw const FormatException('string');
  }

  Object? _lit(String word, Object? value) {
    if (!source.startsWith(word, i)) throw const FormatException('literal');
    i += word.length;
    return value;
  }

  bool _eat(String char) {
    if (i < source.length && source[i] == char) {
      i++;
      return true;
    }
    return false;
  }

  void _ws() {
    while (i < source.length) {
      final char = source[i];
      if (char != ' ' && char != '\n' && char != '\r' && char != '\t') return;
      i++;
    }
  }
}
