import 'dart:convert';
import 'dart:typed_data';

import 'package:characters/characters.dart';
import 'package:crypto/crypto.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../channel_prompt/channel_prompt.dart';
import '../../channel_prompt/channel_prompt_controller.dart';
import '../../l10n/l10n.dart';
import '../providers/providers_page.dart';

/// Shows the default prompt file and points at the channel editor.
class DefaultPromptPage extends StatefulWidget {
  const DefaultPromptPage({super.key});

  @override
  State<DefaultPromptPage> createState() => _DefaultPromptPageState();
}

class _DefaultPromptPageState extends State<DefaultPromptPage> {
  String? _text;
  String? _sha;

  @override
  void initState() {
    super.initState();
    rootBundle.load('assets/engine/hostprompt/prompt.md').then((data) {
      if (!mounted) return;
      final bytes = data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes);
      setState(() {
        _text = utf8.decode(bytes);
        _sha = sha256.convert(bytes).toString();
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final text = _text;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.hostPromptLockedTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.hostPromptLockedHint),
          const SizedBox(height: 12),
          Text(text == null ? '' : '${text.length}  $_sha'),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const ProvidersPage())),
            child: Text(l10n.channelPromptOpenChannels),
          ),
        ],
      ),
    );
  }
}

/// One channel's system prompt. Opening this page does not write.
class PickedPromptFile {
  const PickedPromptFile({required this.name, required this.bytes});

  final String name;
  final Uint8List bytes;
}

/// System document picker. MIME is not a filter: a manager that mislabels
/// `.md` can still return it. The bytes are checked afterwards.
Future<PickedPromptFile?> pickPromptFile() async {
  final file = await FilePicker.pickFile(type: FileType.any);
  if (file == null) return null;
  return PickedPromptFile(name: file.name, bytes: await file.readAsBytes());
}

class ChannelPromptPage extends ConsumerStatefulWidget {
  const ChannelPromptPage({super.key, required this.providerId, this.api, this.defaultText, this.pick});

  final String providerId;
  final String? api;
  final String? defaultText;

  /// Test seam. Production uses [pickPromptFile].
  final Future<PickedPromptFile?> Function()? pick;

  @override
  ConsumerState<ChannelPromptPage> createState() => _ChannelPromptPageState();
}

class _ChannelPromptPageState extends ConsumerState<ChannelPromptPage> {
  final _text = TextEditingController();
  var _ready = false;
  var _arming = false;
  var _builtin = true;
  var _count = 0;
  var _explicitString = false;
  String _default = '';
  String? _savedText;
  ChannelPromptIssue? _issue;
  PromptFileIssue? _importIssue;

  @override
  void initState() {
    super.initState();
    _text.addListener(() {
      if (!mounted) return;
      setState(() => _count = _text.text.characters.length);
    });
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _ensure() async {
    if (_ready) return;
    final supplied = widget.defaultText;
    final String fallback;
    if (supplied != null) {
      fallback = supplied;
    } else {
      final loaded = await rootBundle.load('assets/engine/hostprompt/prompt.md');
      fallback = utf8.decode(loaded.buffer.asUint8List(loaded.offsetInBytes, loaded.lengthInBytes));
    }
    if (!mounted) return;
    final record = ref.read(channelPromptProvider)[widget.providerId];
    _default = fallback;
    _savedText = record?.systemPrompt;
    _builtin = record?.useBuiltinPrompt ?? true;
    _text.text = record?.systemPrompt ?? fallback;
    _explicitString = record?.systemPrompt != null;
    _ready = true;
    setState(() => _count = _text.text.characters.length);
  }

  String? _storedText() {
    final edited = _text.text;
    if (edited.isEmpty) return '';
    if (_savedText == null && !_explicitString && edited == _default) return null;
    return edited;
  }

  Future<void> _write(String? text, bool builtin) async {
    await ref.read(channelPromptProvider.notifier).put(
      widget.providerId,
      ChannelPromptRecord(systemPrompt: text, useBuiltinPrompt: builtin, api: widget.api),
    );
    _savedText = text;
    _explicitString = text != null;
  }

  Future<void> _save() async {
    final issue = validateChannelPrompt(_text.text);
    if (issue != null) {
      setState(() => _issue = issue);
      return;
    }
    setState(() => _issue = null);
    await _write(_storedText(), _builtin);
    if (mounted) Navigator.of(context).maybePop();
  }

  Future<void> _import() async {
    final pick = widget.pick ?? pickPromptFile;
    PickedPromptFile? picked;
    Object? error;
    try {
      picked = await pick();
    } catch (caught) {
      error = caught;
    }
    if (!mounted) return;
    if (picked == null && error == null) return;
    final read = readPromptFile(name: picked?.name ?? '', bytes: picked?.bytes, error: error);
    if (read.issue != null || read.text == null) {
      setState(() => _importIssue = read.issue ?? PromptFileIssue.unreadable);
      return;
    }
    final next = read.text!;
    if (_text.text.isNotEmpty) {
      final l10n = context.l10n;
      final preview = next.isEmpty ? l10n.channelPromptImportEmpty : next.characters.take(240).toString();
      final ok = await showDialog<bool>(
        context: context,
        builder: (dialog) => AlertDialog(
          title: Text(l10n.channelPromptImportReplace),
          content: next.isEmpty ? Text(l10n.channelPromptImportEmpty) : SelectableText(preview),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialog, false), child: Text(l10n.channelPromptCancel)),
            FilledButton(onPressed: () => Navigator.pop(dialog, true), child: Text(l10n.channelPromptImportDo)),
          ],
        ),
      );
      if (ok != true || !mounted) return;
    }
    setState(() {
      _text.text = next;
      _explicitString = true;
      _importIssue = null;
      _issue = null;
    });
  }

  Future<void> _confirm(String title, VoidCallback apply) async {
    final l10n = context.l10n;
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialog) => AlertDialog(
        title: Text(title),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialog, false), child: Text(l10n.channelPromptCancel)),
          FilledButton(onPressed: () => Navigator.pop(dialog, true), child: Text(l10n.hostRootSave)),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    apply();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final doc = ref.watch(channelPromptProvider);
    if (doc.loaded && !_ready && !_arming) {
      _arming = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _ready) return;
        _ensure();
      });
    }
    final issue = _issue;
    final imported = _importIssue;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.channelPromptTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            title: Text(l10n.channelPromptBuiltin),
            subtitle: Text(l10n.channelPromptBuiltinHint),
            value: _builtin,
            onChanged: _ready ? (value) => setState(() => _builtin = value) : null,
          ),
          TextField(
            key: const ValueKey('channel-prompt-text'),
            controller: _text,
            minLines: 8,
            maxLines: 16,
            enabled: _ready,
            style: const TextStyle(fontFamily: 'monospace'),
            decoration: InputDecoration(
              errorText: switch (issue) {
                null => null,
                ChannelPromptIssue.bom => l10n.channelPromptBom,
                ChannelPromptIssue.emptyFirstLine => l10n.channelPromptFirstLine,
                ChannelPromptIssue.endsWithFence => l10n.channelPromptFence,
              },
              errorStyle: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
          if (imported != null)
            Text(
              switch (imported) {
                PromptFileIssue.wrongType => l10n.channelPromptImportType,
                PromptFileIssue.unreadable => l10n.channelPromptImportRead,
                PromptFileIssue.bom => l10n.channelPromptImportBom,
                PromptFileIssue.utf8 => l10n.channelPromptImportUtf8,
                PromptFileIssue.emptyFirstLine => l10n.channelPromptImportFirst,
                PromptFileIssue.fence => l10n.channelPromptImportFence,
              },
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          const SizedBox(height: 8),
          Text('$_count'),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(onPressed: _ready ? _import : null, child: Text(l10n.channelPromptImport)),
          ),
          const SizedBox(height: 12),
          FilledButton(onPressed: _ready ? _save : null, child: Text(l10n.hostRootSave)),
          TextButton(
            onPressed: !_ready
                ? null
                : () => _confirm(l10n.channelPromptRestoreConfirm, () {
                    setState(() {
                      _text.text = _default;
                      _builtin = true;
                      _explicitString = true;
                      _issue = null;
                    });
                    _write(_default, true);
                  }),
            child: Text(l10n.channelPromptRestore),
          ),
          TextButton(
            onPressed: !_ready
                ? null
                : () => _confirm(l10n.channelPromptClearConfirm, () {
                    setState(() {
                      _text.text = '';
                      _explicitString = true;
                      _issue = null;
                    });
                    _write('', _builtin);
                  }),
            child: Text(l10n.channelPromptClear),
          ),
        ],
      ),
    );
  }

}
