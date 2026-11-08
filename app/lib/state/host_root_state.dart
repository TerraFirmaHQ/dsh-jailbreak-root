import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../runtime/runtime_bridge.dart';
import '../runtime/runtime_state.dart';
import 'engine_control.dart';

/// What the phone last reported for the host-root switch. Missing native
/// side stays off and does not invent a grant.
class HostRootSnapshot {
  const HostRootSnapshot({
    this.loaded = false,
    this.rootEnabled = false,
    this.chrootEnabled = false,
    this.promptOk = false,
    this.persona = '',
    this.readonlySkip = false,
    this.autoAllowShell = false,
    this.restarting = false,
    this.safeBlocked = false,
    this.error,
  });

  final bool loaded;
  final bool rootEnabled;
  final bool chrootEnabled;
  final bool promptOk;
  final String persona;
  final bool readonlySkip;
  final bool autoAllowShell;
  final bool restarting;
  final bool safeBlocked;
  final String? error;

  HostRootSnapshot copyWith({
    bool? loaded,
    bool? rootEnabled,
    bool? chrootEnabled,
    bool? promptOk,
    String? persona,
    bool? readonlySkip,
    bool? autoAllowShell,
    bool? restarting,
    bool? safeBlocked,
    String? error,
    bool clearError = false,
  }) => HostRootSnapshot(
    loaded: loaded ?? this.loaded,
    rootEnabled: rootEnabled ?? this.rootEnabled,
    chrootEnabled: chrootEnabled ?? this.chrootEnabled,
    promptOk: promptOk ?? this.promptOk,
    persona: persona ?? this.persona,
    readonlySkip: readonlySkip ?? this.readonlySkip,
    autoAllowShell: autoAllowShell ?? this.autoAllowShell,
    restarting: restarting ?? this.restarting,
    safeBlocked: safeBlocked ?? this.safeBlocked,
    error: clearError ? null : error ?? this.error,
  );
}

class HostRootToggle {
  const HostRootToggle({required this.ok, this.restarting = false, this.safe = false, this.error});

  final bool ok;
  final bool restarting;
  final bool safe;
  final String? error;
}

/// MethodChannel `dsh/hostroot`. A status read does not call su.
class HostRootController extends Notifier<HostRootSnapshot> {
  static const methods = MethodChannel('dsh/hostroot');

  @override
  HostRootSnapshot build() {
    Future.microtask(refresh);
    return const HostRootSnapshot();
  }

  Future<void> refresh() async {
    try {
      final raw = await methods.invokeMapMethod<String, Object?>('status');
      if (!ref.mounted) return;
      state = _from(raw);
    } on MissingPluginException {
      if (ref.mounted) state = state.copyWith(loaded: true);
    } catch (e) {
      if (ref.mounted) state = state.copyWith(loaded: true, error: '$e');
    }
  }

  HostRootSnapshot _from(Map<String, Object?>? raw) => HostRootSnapshot(
    loaded: true,
    rootEnabled: raw?['rootEnabled'] == true,
    chrootEnabled: raw?['chrootEnabled'] == true,
    promptOk: raw?['promptOk'] == true,
    persona: raw?['persona'] as String? ?? '',
    readonlySkip: raw?['readonlySkip'] == true,
    autoAllowShell: raw?['autoAllowShell'] == true,
    restarting: state.restarting,
  );

  /// Writes the switch, then restarts a running normal engine so the overlay
  /// mounts or unmounts. Safe mode refuses the on position without calling su.
  Future<HostRootToggle> setRoot(bool on) => _set('setRootEnabled', on, root: true);

  Future<HostRootToggle> setChroot(bool on) => _set('setChrootEnabled', on, root: false);

  /// User switch. Default off. The native tool reads it at call time.
  Future<void> setAutoAllow(bool on) async {
    try {
      await methods.invokeMethod<Object?>('setAutoAllow', {'on': on});
      if (ref.mounted) state = state.copyWith(autoAllowShell: on, clearError: true);
    } on MissingPluginException {
      if (ref.mounted) state = state.copyWith(error: 'NO_CHANNEL');
    } catch (e) {
      if (ref.mounted) state = state.copyWith(error: '$e');
    }
  }

  Future<void> setReadonlySkip(bool on) async {
    try {
      await methods.invokeMethod<Object?>('setReadonlySkip', {'on': on});
      if (ref.mounted) state = state.copyWith(readonlySkip: on, clearError: true);
    } on MissingPluginException {
      if (ref.mounted) state = state.copyWith(error: 'NO_CHANNEL');
    } catch (e) {
      if (ref.mounted) state = state.copyWith(error: '$e');
    }
  }

  Future<void> clearSessionMemory() async {
    try {
      await methods.invokeMethod<Object?>('clearSessionMemory');
    } catch (_) {}
  }

  /// Writes the persona the user just saved. Does not run on startup, so an
  /// upgrade keeps whatever was already stored and a fresh install stays blank.
  /// A running normal engine restarts so the separate persona section mounts
  /// or drops. The locked prompt file is not touched.
  Future<HostRootToggle> setPersona(String text) async {
    try {
      await methods.invokeMethod<void>('setPersona', {'text': text});
      if (!ref.mounted) return const HostRootToggle(ok: true);
      state = state.copyWith(persona: text, clearError: true);
      final now = ref.read(localRuntimeProvider).status;
      final restarting =
          !now.inSafeMode && (now.phase == RuntimePhase.running || now.phase == RuntimePhase.starting);
      if (restarting) {
        state = state.copyWith(restarting: true);
        unawaited(
          EngineControl.restart(ref.container).whenComplete(() {
            if (ref.mounted) state = state.copyWith(restarting: false);
          }),
        );
      }
      return HostRootToggle(ok: true, restarting: restarting);
    } on MissingPluginException {
      if (ref.mounted) state = state.copyWith(error: 'NO_CHANNEL');
      return const HostRootToggle(ok: false, error: 'NO_CHANNEL');
    } catch (e) {
      if (ref.mounted) state = state.copyWith(error: '$e');
      return HostRootToggle(ok: false, error: '$e');
    }
  }

  Future<HostRootToggle> _set(String method, bool on, {required bool root}) async {
    final runtime = ref.read(localRuntimeProvider).status;
    if (on && runtime.inSafeMode) {
      state = state.copyWith(safeBlocked: true, error: 'SAFE');
      return const HostRootToggle(ok: false, safe: true, error: 'SAFE');
    }
    try {
      final raw = await methods.invokeMapMethod<String, Object?>(method, {'on': on});
      final error = raw?['error'] as String?;
      final ok = raw?['ok'] == true && (error == null || error.isEmpty);
      if (!ref.mounted) return HostRootToggle(ok: ok, error: error);
      if (!ok) {
        state = state.copyWith(
          rootEnabled: root ? false : state.rootEnabled,
          chrootEnabled: root ? state.chrootEnabled : false,
          safeBlocked: false,
          error: error ?? 'FAILED',
        );
        return HostRootToggle(ok: false, error: error);
      }
      state = state.copyWith(
        loaded: true,
        rootEnabled: root ? on : state.rootEnabled,
        chrootEnabled: root ? state.chrootEnabled : on,
        safeBlocked: false,
        clearError: true,
      );
      final now = ref.read(localRuntimeProvider).status;
      final restarting =
          !now.inSafeMode && (now.phase == RuntimePhase.running || now.phase == RuntimePhase.starting);
      if (restarting) {
        state = state.copyWith(restarting: true);
        unawaited(
          EngineControl.restart(ref.container).whenComplete(() {
            if (ref.mounted) state = state.copyWith(restarting: false);
          }),
        );
      }
      return HostRootToggle(ok: true, restarting: restarting);
    } on MissingPluginException {
      if (ref.mounted) state = state.copyWith(error: 'NO_CHANNEL');
      return const HostRootToggle(ok: false, error: 'NO_CHANNEL');
    } catch (e) {
      if (ref.mounted) state = state.copyWith(error: '$e');
      return HostRootToggle(ok: false, error: '$e');
    }
  }
}

final hostRootProvider = NotifierProvider<HostRootController, HostRootSnapshot>(HostRootController.new);
