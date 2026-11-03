// Rony fork: 刷新率「自动」(default since v4, like official 1.0.6). Reports sustained frame
// production (scrolling, animations, streaming output) to the native side over `dsh/rony`
// `motion`; RefreshRate.kt boosts to the highest rate and hands back to the system 0.7 s after
// the last report. One stray frame (a cursor blink, a display-mode change) does not count: it takes
// [minFrames] frames within [window]. Reports are throttled to one per [throttle].

import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';

class RefreshMotion {
  RefreshMotion({
    this.report,
    this.minFrames = 3,
    this.window = const Duration(milliseconds: 120),
    this.throttle = const Duration(milliseconds: 250),
  });

  /// Sends one motion report; defaults to the `dsh/rony` channel.
  final void Function()? report;
  final int minFrames;
  final Duration window;
  final Duration throttle;

  static const _channel = MethodChannel('dsh/rony');
  static RefreshMotion? _installed;

  final List<Duration> _recent = [];
  Duration? _lastReport;

  /// Hooks every frame of [binding] (once per process).
  static void install([SchedulerBinding? binding]) {
    if (_installed != null) return;
    final m = _installed = RefreshMotion();
    (binding ?? SchedulerBinding.instance).addPersistentFrameCallback(m.onFrame);
  }

  /// One frame at [now] (monotonic). Returns whether a report was sent.
  bool onFrame(Duration now) {
    _recent.add(now);
    while (_recent.isNotEmpty && now - _recent.first > window) {
      _recent.removeAt(0);
    }
    if (_recent.length < minFrames) return false;
    final last = _lastReport;
    if (last != null && now - last < throttle) return false;
    _lastReport = now;
    (report ?? _send)();
    return true;
  }

  static void _send() {
    _channel.invokeMethod<void>('motion').catchError((_) {});
  }
}
