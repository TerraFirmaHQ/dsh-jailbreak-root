import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/l10n.dart';
import '../../runtime/runtime_bridge.dart';
import '../../runtime/runtime_state.dart';
import '../../state/host_root_state.dart';
import '../common/brief_notice.dart';

/// Host root and the optional chroot switch. Persona has its own page.
/// Turning a switch restarts a running engine so the overlay mounts.
class HostRootPage extends ConsumerWidget {
  const HostRootPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final status = ref.watch(hostRootProvider);
    Future<void> toggle(bool root, bool on) async {
      final result = root
          ? await ref.read(hostRootProvider.notifier).setRoot(on)
          : await ref.read(hostRootProvider.notifier).setChroot(on);
      if (!context.mounted) return;
      final String? text;
      if (result.safe) {
        text = l10n.hostRootSafe;
      } else if (result.error == 'NO_SU') {
        text = l10n.hostRootNoSu;
      } else if (result.error == 'MOUNTS_DISABLED') {
        text = l10n.hostRootChrootHint;
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

    return Scaffold(
      appBar: AppBar(title: Text(l10n.hostRootTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.hostRootBody),
          const SizedBox(height: 12),
          Text(status.rootEnabled ? l10n.hostRootToolsOn : l10n.hostRootToolsOff),
          const SizedBox(height: 8),
          Text(status.promptOk ? l10n.hostPromptLockedHint : l10n.hostRootPromptMissing),
          SwitchListTile(
            title: Text(l10n.hostRootSwitch),
            value: status.rootEnabled,
            onChanged: status.restarting ? null : (v) => toggle(true, v),
          ),
          SwitchListTile(
            title: Text(l10n.hostRootChroot),
            subtitle: Text(l10n.hostRootChrootHint),
            value: false,
            onChanged: status.restarting ? null : (v) => toggle(false, true),
          ),
          SwitchListTile(
            title: Text(l10n.hostRootReadonlySkip),
            subtitle: Text(l10n.hostRootReadonlySkipHint),
            value: status.readonlySkip,
            onChanged: (v) => ref.read(hostRootProvider.notifier).setReadonlySkip(v),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: () => ref.read(hostRootProvider.notifier).clearSessionMemory(),
              child: Text(l10n.hostRootClearMemory),
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
