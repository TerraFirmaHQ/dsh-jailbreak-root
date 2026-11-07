import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/l10n.dart';

/// Host root, the locked prompt, and the optional chroot switch.
class HostRootPage extends StatefulWidget {
  const HostRootPage({super.key});

  @override
  State<HostRootPage> createState() => _HostRootPageState();
}

class _HostRootPageState extends State<HostRootPage> {
  static const _channel = MethodChannel('dsh/hostroot');
  Map<String, Object?> _status = const {};
  late final TextEditingController _persona;
  String? _error;

  @override
  void initState() {
    super.initState();
    _persona = TextEditingController();
    _load();
  }

  @override
  void dispose() {
    _persona.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final raw = await _channel.invokeMapMethod<String, Object?>('status');
      if (!mounted) return;
      setState(() {
        _status = raw ?? const {};
        _persona.text = _status['persona'] as String? ?? '';
        _error = null;
      });
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  Future<void> _set(String method, bool on) async {
    final raw = await _channel.invokeMapMethod<String, Object?>(method, {'on': on});
    if (!mounted) return;
    setState(() => _error = raw?['error'] as String?);
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final promptOk = _status['promptOk'] == true;
    final sha = _status['promptSha256'] as String? ?? '';
    return Scaffold(
      appBar: AppBar(title: Text(l10n.hostRootTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.hostRootBody),
          const SizedBox(height: 12),
          Text(promptOk ? l10n.hostRootPrompt : l10n.hostRootPromptMissing),
          if (sha.isNotEmpty) SelectableText(sha),
          SwitchListTile(
            title: Text(l10n.hostRootSwitch),
            value: _status['rootEnabled'] == true,
            onChanged: (v) => _set('setRootEnabled', v),
          ),
          SwitchListTile(
            title: Text(l10n.hostRootChroot),
            subtitle: Text(l10n.hostRootChrootHint),
            value: _status['chrootEnabled'] == true,
            onChanged: (v) => _set('setChrootEnabled', v),
          ),
          TextField(
            controller: _persona,
            minLines: 3,
            maxLines: 8,
            decoration: InputDecoration(labelText: l10n.hostRootPersona, helperText: l10n.hostRootPersonaHint),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: () async {
                await _channel.invokeMethod('setPersona', {'text': _persona.text});
                if (mounted) setState(() {});
              },
              child: Text(l10n.hostRootSave),
            ),
          ),
          Text(l10n.hostRootMigration),
          if (_error == 'NO_SU') Text(l10n.hostRootNoSu),
          if (_error != null && _error != 'NO_SU') Text(_error!),
        ],
      ),
    );
  }
}
