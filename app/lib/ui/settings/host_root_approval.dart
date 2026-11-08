import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/l10n.dart';
import '../../state/host_root_state.dart';

/// Root command approval. The native side blocks the tool until this answers.
/// Nothing in the chat stream shows that wait, so the dialog has to.
class HostRootApprovalHost extends StatefulWidget {
  const HostRootApprovalHost({super.key, required this.child, this.events});

  final Widget child;

  /// Test seam. Production uses `dsh/hostroot/events`.
  final Stream<dynamic>? events;

  @override
  State<HostRootApprovalHost> createState() => _HostRootApprovalHostState();
}

class _HostRootApprovalHostState extends State<HostRootApprovalHost> {
  static const _events = EventChannel('dsh/hostroot/events');
  StreamSubscription<dynamic>? _sub;
  bool _showing = false;

  @override
  void initState() {
    super.initState();
    final injected = widget.events;
    if (injected != null) {
      _sub = injected.listen(_onEvent, onError: (_) {});
    } else {
      unawaited(_openNative());
    }
  }

  /// Widget tests have no native plugin. Opening the event channel there
  /// reports a missing plugin and fails the test. A status call is the probe.
  Future<void> _openNative() async {
    try {
      await HostRootController.methods.invokeMethod<Object?>('status');
    } on MissingPluginException {
      return;
    } catch (_) {}
    if (!mounted) return;
    _sub = _events.receiveBroadcastStream().listen(_onEvent, onError: (_) {});
  }

  @override
  void dispose() {
    unawaited(_sub?.cancel());
    super.dispose();
  }

  void _onEvent(dynamic event) {
    if (_showing || event is! Map) return;
    if (event['type'] != 'approval') return;
    final id = event['id'] as String? ?? '';
    if (id.isEmpty) return;
    final preview = event['preview'] as String? ?? event['command'] as String? ?? '';
    final command = event['command'] as String? ?? '';
    final protectedChange = event['protected'] == true;
    unawaited(_ask(id, preview, command, protectedChange));
  }

  Future<void> _ask(String id, String preview, String command, bool protectedChange) async {
    if (!mounted) {
      await _answer(id, false);
      return;
    }
    _showing = true;
    final l10n = context.l10n;
    final choice = await showDialog<String>(
      context: context,
      useRootNavigator: true,
      builder: (dialog) => AlertDialog(
        title: Text(l10n.hostRootApprovalTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (protectedChange) Text(l10n.hostRootApprovalProtected),
            SelectableText(
              command.isEmpty ? preview : command,
              style: const TextStyle(fontFamily: 'monospace'),
            ),
            ExpansionTile(
              title: Text(l10n.hostRootAudit),
              initiallyExpanded: false,
              children: [SelectableText(preview)],
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialog, 'deny'), child: Text(l10n.hostRootDeny)),
          FilledButton(onPressed: () => Navigator.pop(dialog, 'allow'), child: Text(l10n.hostRootAllow)),
        ],
      ),
    );
    _showing = false;
    await _answer(id, choice == 'allow' || choice == 'session', session: choice == 'session');
  }

  Future<void> _answer(String id, bool accept, {bool session = false}) async {
    try {
      await HostRootController.methods.invokeMethod<bool>('resolveApproval', {
        'id': id,
        'accept': accept,
        'session': session,
      });
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
