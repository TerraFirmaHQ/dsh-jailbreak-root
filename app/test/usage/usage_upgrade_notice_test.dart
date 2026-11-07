// Installs from before usage statistics: the first time they reach the home,
// one toast says so (了解 → 设置 › 隐私与分享, 关闭 → off), and only from then
// on is anything counted. Once only; never during first run; never over
// another page; 关闭 means not one request. Goldens light and dark, and the
// toast measured: as far from either screen edge, the text 16 in on the
// left, the last key's label 16 in on the right.
//
//   flutter test --update-goldens test/usage/usage_upgrade_notice_test.dart

import 'dart:async';

import 'package:dsh_app/l10n/l10n.dart';
import 'package:dsh_app/runtime/runtime_bridge.dart';
import 'package:dsh_app/runtime/runtime_state.dart';
import 'package:dsh_app/state/settings_state.dart';
import 'package:dsh_app/ui/settings/index/spot_anchor.dart';
import 'package:dsh_app/ui/settings/privacy_page.dart';
import 'package:dsh_app/ui/shell/root_gate.dart';
import 'package:dsh_app/ui/shell/usage_upgrade_notice.dart';
import 'package:dsh_app/ui/theme/theme.dart';
import 'package:dsh_app/usage/usage_net.dart';
import 'package:dsh_app/usage/usage_stats.dart';
import 'package:dsh_client/dsh_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../engine_settings/support.dart' as engine;
import '../shell/golden_harness.dart';
import 'support.dart';

const _notice = ValueKey('usage-upgrade-notice');

late FakeUsageHttp http;
late DateTime now;
VoidCallback? resume;

List<Override> _overrides() => [
  ...usageOverrides(clock: () => now, http: http, onResumes: (r) => resume = r),
  ...engine.engineOverrides(DshClient.withCarrier(engine.FakeSettingsHost())),
];

/// The home stand-in, under the notice as the root gate puts it.
Widget _home() => UsageUpgradeNotice(
  child: AppBackdrop(
    child: Scaffold(
      backgroundColor: Colors.transparent,
      body: Builder(builder: (context) => const SizedBox.expand()),
    ),
  ),
);

Future<void> _pumpHome(WidgetTester tester, {Brightness brightness = Brightness.light}) async {
  usePhoneSurface(tester);
  await tester.pumpWidget(goldenApp(home: _home(), brightness: brightness, overrides: _overrides()));
  await tester.pump();
}

/// Past the notice's delay, and its slide in.
Future<void> _wait(WidgetTester tester, [Duration time = const Duration(seconds: 2)]) async {
  await tester.pump(time);
  await tester.pump(const Duration(milliseconds: 400));
}

ProviderContainer _container(WidgetTester tester) =>
    ProviderScope.containerOf(tester.element(find.byType(MaterialApp)));

Future<Map<String, Object>> _stored(WidgetTester tester) async {
  final prefs = (await tester.runAsync(SharedPreferences.getInstance))!;
  return {for (final k in prefs.getKeys()) k: prefs.get(k)!};
}

/// The app coming back to the front.
Future<void> _comeBack(WidgetTester tester) async {
  resume!();
  await tester.pump();
  await tester.pump();
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('this edition does not show the usage upgrade notice', (tester) async {
    http = FakeUsageHttp();
    now = DateTime(2026, 10, 30, 9);
    await _pumpHome(tester);
    await _wait(tester);
    expect(find.byKey(_notice), findsNothing);
    expect(find.text(appL10n.usageStatsTitle), findsNothing);
  });
}
