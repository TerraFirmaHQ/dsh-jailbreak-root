// Rony fork v5.1: 设置 → Root 与 MCP（Rony）→ 人设自定义. ONE page, two controls:
// - 活人感: one real switch (default on). Off strips everything 活人感: the persona overlay is not
//   mounted at the next engine start (system prompt = 破甲 + stock DSH), the chat stops splitting
//   replies / showing the typing delay at once, and the proactive check-in alarm is cancelled.
// - 自定义人设: free text (Markdown ok), used only while 活人感 is on; empty = the built-in voice.
// The QQ side (Aurabot) keeps its own personas; this page is the in-app chat.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../rony/rony_channel.dart';

class RonyPersonaPage extends StatefulWidget {
  const RonyPersonaPage({super.key, this.channel = const RonyChannel(), this.onChanged});

  final RonyChannel channel;

  /// Something that needs an engine restart changed.
  final VoidCallback? onChanged;

  @override
  State<RonyPersonaPage> createState() => _RonyPersonaPageState();
}

class _RonyPersonaPageState extends State<RonyPersonaPage> {
  final _text = TextEditingController();
  bool _humanlike = ronyHumanlike.value;
  String _default = '';
  String _saved = '';
  String _note = '';
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final s = await widget.channel.state();
      if (!mounted) return;
      setState(() {
        _humanlike = s['humanlike'] != false;
        _default = (s['personaDefault'] as String?) ?? '';
        _saved = (s['personaText'] as String?) ?? '';
        _text.text = _saved;
      });
    } on MissingPluginException {
      // Widget tests.
    } on PlatformException catch (e) {
      if (mounted) setState(() => _note = '读取失败：${e.message}');
    }
  }

  Future<void> _setHumanlike(bool on) async {
    setState(() {
      _busy = true;
      _humanlike = on;
    });
    ronyHumanlike.value = on;
    try {
      await widget.channel.setHumanlike(on);
      widget.onChanged?.call();
      if (mounted) {
        setState(() => _note = on
            ? '活人感已打开：聊天里的分条发送和打字停顿马上生效；人设注入在重启引擎后生效。'
            : '活人感已关闭：分条发送、打字停顿、主动找你都停了；重启引擎后系统提示词只剩破甲。');
      }
    } on MissingPluginException {
      // Widget tests.
    } on PlatformException catch (e) {
      if (mounted) setState(() => _note = '失败：${e.message}');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _save() async {
    setState(() => _busy = true);
    try {
      await widget.channel.setPersonaText(_text.text);
      _saved = _text.text;
      widget.onChanged?.call();
      if (mounted) setState(() => _note = '人设已保存，重启引擎后新对话生效。');
    } on MissingPluginException {
      // Widget tests.
    } on PlatformException catch (e) {
      if (mounted) setState(() => _note = '保存失败：${e.message}');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final dirty = _text.text != _saved;
    return Scaffold(
      appBar: AppBar(title: const Text('人设自定义')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            key: const ValueKey('rony-humanlike-switch'),
            contentPadding: EdgeInsets.zero,
            title: const Text('活人感（默认开）'),
            subtitle: Text(_humanlike
                ? '开：用下面的人设说话，闲聊分几条发、带打字停顿，很久没聊会偶尔主动找你'
                : '关：没有人设、不分条、不停顿、不主动找你，只剩破甲提示词'),
            value: _humanlike,
            onChanged: _busy ? null : _setHumanlike,
          ),
          const SizedBox(height: 12),
          TextField(
            key: const ValueKey('rony-persona-text'),
            controller: _text,
            enabled: _humanlike,
            minLines: 8,
            maxLines: 20,
            maxLength: 16000,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              labelText: '自定义人设',
              hintText: _default.isEmpty ? '例如：你叫小鲸，是我的搭档，说话直接、爱开玩笑……' : '留空 = 默认：$_default',
              hintMaxLines: 6,
              border: const OutlineInputBorder(),
              alignLabelWithHint: true,
            ),
          ),
          Row(
            children: [
              FilledButton(onPressed: _busy || !dirty ? null : _save, child: const Text('保存人设')),
              const SizedBox(width: 12),
              if (_saved.isNotEmpty)
                TextButton(
                  onPressed: _busy ? null : () { _text.clear(); setState(() {}); },
                  child: const Text('恢复默认'),
                ),
            ],
          ),
          if (_note.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_note)),
          const SizedBox(height: 16),
          Text(
            '人设放在系统提示词里破甲之后、工具说明之前。破甲有自己的开关，和这里互不影响。QQ 上的人格由 Aurabot 管（QQ 连接 → Aurabot 面板）。',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}
