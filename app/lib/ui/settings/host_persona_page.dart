import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/l10n.dart';
import '../../runtime/runtime_bridge.dart';
import '../../runtime/runtime_state.dart';
import '../../state/host_root_state.dart';
import '../common/brief_notice.dart';

/// Expression fields. Stored apart from the locked prompt and from approval.
/// Opening the page does not write. Save and restore-default do.
class HostPersonaPage extends ConsumerStatefulWidget {
  const HostPersonaPage({super.key});

  @override
  ConsumerState<HostPersonaPage> createState() => _HostPersonaPageState();
}

class PersonaExpression {
  const PersonaExpression({
    this.call = '你',
    this.self = '我',
    this.tone = '平和',
    this.speech = '普通',
    this.note = '',
  });

  final String call;
  final String self;
  final String tone;
  final String speech;
  final String note;

  static const tones = ['平和', '直接', '温和'];
  static const speeches = ['少', '普通', '多'];

  static PersonaExpression parse(String raw) {
    String field(String name) {
      final match = RegExp('^$name：(.+)\$', multiLine: true).firstMatch(raw);
      return match?.group(1)?.trim() ?? '';
    }

    final call = field('称呼');
    final self = field('自称');
    final tone = field('语气');
    final speech = field('口语程度');
    final extra = RegExp(r'^补充：(.+)$', multiLine: true).firstMatch(raw)?.group(1)?.trim() ?? '';
    final structured = raw.contains('称呼：');
    return PersonaExpression(
      call: call.isEmpty ? '你' : call,
      self: self.isEmpty ? '我' : self,
      tone: tones.contains(tone) ? tone : '平和',
      speech: speeches.contains(speech) ? speech : '普通',
      note: structured ? extra : raw,
    );
  }

  String compose() {
    final body = [
      '以下只影响表达，不覆盖破甲提示词，也不改变审批。',
      '称呼：$call',
      '自称：$self',
      '语气：$tone',
      '口语程度：$speech',
      '短句，可以用「不知道」「等我看看」。不要机械列表。',
      if (note.trim().isNotEmpty) '补充：${note.trim()}',
    ];
    return body.join('\n');
  }
}

class _HostPersonaPageState extends ConsumerState<HostPersonaPage> {
  final _call = TextEditingController();
  final _self = TextEditingController();
  final _note = TextEditingController();
  var _tone = '平和';
  var _speech = '普通';
  var _ready = false;

  @override
  void dispose() {
    _call.dispose();
    _self.dispose();
    _note.dispose();
    super.dispose();
  }

  void _apply(PersonaExpression fields) {
    _call.text = fields.call;
    _self.text = fields.self;
    _note.text = fields.note;
    _tone = fields.tone;
    _speech = fields.speech;
  }

  PersonaExpression _current() => PersonaExpression(
    call: _call.text.trim().isEmpty ? '你' : _call.text.trim(),
    self: _self.text.trim().isEmpty ? '我' : _self.text.trim(),
    tone: _tone,
    speech: _speech,
    note: _note.text,
  );

  Future<void> _save(PersonaExpression fields) async {
    final result = await ref.read(hostRootProvider.notifier).setPersona(fields.compose());
    if (!mounted) return;
    final l10n = context.l10n;
    final text = result.restarting
        ? l10n.hostRootRestarting
        : ref.read(localRuntimeProvider).status.phase != RuntimePhase.running
        ? l10n.hostRootPendingStart
        : l10n.hostRootSave;
    showBriefNotice(context, text);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final status = ref.watch(hostRootProvider);
    if (!_ready && status.loaded) {
      _apply(PersonaExpression.parse(status.persona));
      _ready = true;
    }
    return Scaffold(
      appBar: AppBar(title: Text(l10n.hostPersonaTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(status.promptOk ? l10n.hostPromptLockedHint : l10n.hostRootPromptMissing),
          const SizedBox(height: 8),
          Text(l10n.hostPersonaScope),
          const SizedBox(height: 12),
          TextField(
            key: const ValueKey('persona-call'),
            controller: _call,
            enabled: _ready && !status.restarting,
            decoration: InputDecoration(labelText: l10n.hostPersonaCall),
          ),
          TextField(
            key: const ValueKey('persona-self'),
            controller: _self,
            enabled: _ready && !status.restarting,
            decoration: InputDecoration(labelText: l10n.hostPersonaSelf),
          ),
          DropdownButtonFormField<String>(
            initialValue: _tone,
            decoration: InputDecoration(labelText: l10n.hostPersonaTone),
            items: PersonaExpression.tones.map((tone) => DropdownMenuItem(value: tone, child: Text(tone))).toList(),
            onChanged: !_ready || status.restarting ? null : (value) => setState(() => _tone = value ?? _tone),
          ),
          DropdownButtonFormField<String>(
            initialValue: _speech,
            decoration: InputDecoration(labelText: l10n.hostPersonaSpeech),
            items: PersonaExpression.speeches.map((item) => DropdownMenuItem(value: item, child: Text(item))).toList(),
            onChanged: !_ready || status.restarting ? null : (value) => setState(() => _speech = value ?? _speech),
          ),
          TextField(
            key: const ValueKey('persona-note'),
            controller: _note,
            enabled: _ready && !status.restarting,
            minLines: 2,
            maxLines: 6,
            decoration: InputDecoration(labelText: l10n.hostPersonaNote),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            children: [
              FilledButton(
                onPressed: _ready && !status.restarting ? () => _save(_current()) : null,
                child: Text(l10n.hostRootSave),
              ),
              TextButton(
                onPressed: _ready && !status.restarting
                    ? () {
                        final fields = const PersonaExpression();
                        setState(() => _apply(fields));
                        _save(fields);
                      }
                    : null,
                child: Text(l10n.hostPersonaRestore),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
