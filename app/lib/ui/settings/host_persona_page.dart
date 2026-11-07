import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/l10n.dart';
import '../../runtime/runtime_bridge.dart';
import '../../runtime/runtime_state.dart';
import '../../state/host_root_state.dart';
import '../common/brief_notice.dart';

/// Persona text. Separate from Root and from the locked built-in prompt.
/// Opening the page does not write. Save stores the text; a blank save
/// mounts no persona section and does not replace the locked prompt.
class HostPersonaPage extends ConsumerStatefulWidget {
  const HostPersonaPage({super.key});

  @override
  ConsumerState<HostPersonaPage> createState() => _HostPersonaPageState();
}

class _HostPersonaPageState extends ConsumerState<HostPersonaPage> {
  late final TextEditingController _persona;
  var _ready = false;

  @override
  void initState() {
    super.initState();
    _persona = TextEditingController();
  }

  @override
  void dispose() {
    _persona.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final result = await ref.read(hostRootProvider.notifier).setPersona(_persona.text);
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
      _persona.text = status.persona;
      _ready = true;
    }
    return Scaffold(
      appBar: AppBar(title: Text(l10n.hostPersonaTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(status.promptOk ? l10n.hostPromptLockedHint : l10n.hostRootPromptMissing),
          const SizedBox(height: 12),
          Text(l10n.hostRootPersonaHint),
          const SizedBox(height: 12),
          TextField(
            controller: _persona,
            enabled: _ready && !status.restarting,
            minLines: 4,
            maxLines: 10,
            decoration: InputDecoration(labelText: l10n.hostPersonaTitle),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton(
              onPressed: _ready && !status.restarting ? _save : null,
              child: Text(l10n.hostRootSave),
            ),
          ),
        ],
      ),
    );
  }
}
