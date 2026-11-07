import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/l10n.dart';
import '../../runtime/runtime_bridge.dart';
import '../../runtime/runtime_state.dart';
import '../../state/host_root_state.dart';
import '../common/brief_notice.dart';

/// Host root, the locked prompt, and the optional chroot switch.
/// The row lives in 设置 › 能力. Turning a switch restarts a running engine
/// so the overlay actually mounts; sessions stay on disk.
class HostRootPage extends ConsumerStatefulWidget {
  const HostRootPage({super.key});

  @override
  ConsumerState<HostRootPage> createState() => _HostRootPageState();
}

class _HostRootPageState extends ConsumerState<HostRootPage> {
  late final TextEditingController _persona;
  var _personaReady = false;

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

  Future<void> _toggle(bool root, bool on) async {
    final result = root
        ? await ref.read(hostRootProvider.notifier).setRoot(on)
        : await ref.read(hostRootProvider.notifier).setChroot(on);
    if (!mounted) return;
    final l10n = context.l10n;
    final String? text;
    if (result.safe) {
      text = l10n.hostRootSafe;
    } else if (result.error == 'NO_SU') {
      text = l10n.hostRootNoSu;
    } else if (!result.ok) {
      text = result.error;
    } else if (result.restarting) {
      text = l10n.hostRootRestarting;
    } else if (ref.read(localRuntimeProvider).status.phase != RuntimePhase.running) {
      text = l10n.hostRootPendingStart;
    } else {
      text = null;
    }
    if (text != null && text.isNotEmpty) showBriefNotice(context, text);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final status = ref.watch(hostRootProvider);
    if (!_personaReady && status.loaded) {
      _persona.text = status.persona;
      _personaReady = true;
    }
    final promptOk = status.promptOk;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.hostRootTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.hostRootBody),
          const SizedBox(height: 12),
          Text(status.rootEnabled ? l10n.hostRootToolsOn : l10n.hostRootToolsOff),
          const SizedBox(height: 8),
          Text(promptOk ? l10n.hostRootPrompt : l10n.hostRootPromptMissing),
          SwitchListTile(
            title: Text(l10n.hostRootSwitch),
            value: status.rootEnabled,
            onChanged: status.restarting ? null : (v) => _toggle(true, v),
          ),
          SwitchListTile(
            title: Text(l10n.hostRootChroot),
            subtitle: Text(l10n.hostRootChrootHint),
            value: status.chrootEnabled,
            onChanged: status.restarting ? null : (v) => _toggle(false, v),
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
              onPressed: () => ref.read(hostRootProvider.notifier).setPersona(_persona.text),
              child: Text(l10n.hostRootSave),
            ),
          ),
          Text(l10n.hostRootMigration),
          if (status.safeBlocked) Text(l10n.hostRootSafe),
          if (status.error == 'NO_SU') Text(l10n.hostRootNoSu),
          if (status.error != null && status.error != 'NO_SU' && status.error != 'SAFE') Text(status.error!),
        ],
      ),
    );
  }
}
