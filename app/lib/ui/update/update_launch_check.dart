import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/notify_state.dart' show launchNoticeShownProvider;
import 'package:shared_preferences/shared_preferences.dart';

import '../../rony/rony_channel.dart' show kRonyOnlineUpdatesDisabled;
import '../../state/update_state.dart';
import '../phone/rony_tools_page.dart';
import 'update_sheet.dart';

/// Mounted once in the home shell; draws nothing.
///
/// A few seconds after launch (the engine starts first) it runs the daily
/// automatic check and offers the sheet when there's a version the person
/// hasn't skipped. It also tells the controller when the app comes back to
/// the front, which continues an install waiting on the "install unknown
/// apps" permission.
class UpdateLaunchCheck extends ConsumerStatefulWidget {
  const UpdateLaunchCheck({super.key, this.delay = const Duration(seconds: 6)});

  final Duration delay;

  @override
  ConsumerState<UpdateLaunchCheck> createState() => _UpdateLaunchCheckState();
}

class _UpdateLaunchCheckState extends ConsumerState<UpdateLaunchCheck> {
  Timer? _timer;
  late final AppLifecycleListener _lifecycle;

  @override
  void initState() {
    super.initState();
    _timer = Timer(widget.delay, () => unawaited(_run()));
    _lifecycle = AppLifecycleListener(onResume: () => unawaited(ref.read(appUpdateProvider.notifier).onAppResumed()));
  }

  @override
  void dispose() {
    _timer?.cancel();
    _lifecycle.dispose();
    super.dispose();
  }

  Future<void> _run() async {
    if (!mounted) return;
    if (kRonyOnlineUpdatesDisabled) {
      await _ronyFirstRunHint();
      return;
    }
    final offer = await ref.read(appUpdateProvider.notifier).autoCheckIfDue();
    if (!offer || !mounted) return;
    // Don't cover a page or sheet the person is in, or interrupt typing (raw
    // view insets: a Scaffold on the way may have consumed the keyboard's).
    // The mark in 设置 → 关于 stays either way.
    if (ModalRoute.of(context)?.isCurrent == false || View.of(context).viewInsets.bottom > 0) return;
    ref.read(launchNoticeShownProvider.notifier).set(true);
    await showUpdateSheet(context);
  }

  Future<void> _ronyFirstRunHint() async {
    const key = 'rony_ace7_first_run_hint_v1';
    try {
      final prefs = await SharedPreferences.getInstance();
      if (prefs.getBool(key) == true || !mounted) return;
      if (ModalRoute.of(context)?.isCurrent == false) return;
      await prefs.setBool(key, true);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('dsh Ace7：Root 默认关'),
          content: const Text(
            '设置 → Ace7 / Root 与 MCP：\n'
            '· 破甲提示词已内置（锁死）\n'
            '· Root 模式默认关，不影响隐藏环境\n'
            '· 需要时再开 Root，并只在 KSU 授权本 App\n'
            '· 一键自检可看刷新率模式（含 165Hz）',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('知道了')),
            FilledButton(
              onPressed: () {
                Navigator.pop(ctx);
                Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const RonyToolsPage()));
              },
              child: const Text('去看看'),
            ),
          ],
        ),
      );
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
