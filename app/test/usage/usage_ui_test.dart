// This edition does not show the usage-statistics switch on the welcome
// screen or under 设置 › 隐私与分享.

import 'package:dsh_app/l10n/l10n.dart';
import 'package:dsh_app/ui/onboarding/welcome_page.dart';
import 'package:dsh_app/ui/settings/privacy_page.dart';
import 'package:dsh_client/dsh_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../engine_settings/support.dart' as engine;
import '../shell/golden_harness.dart';

void main() {
  testWidgets('welcome does not offer usage statistics', (tester) async {
    tester.view.devicePixelRatio = 2;
    tester.view.physicalSize = const Size(390 * 2, 844 * 2);
    addTearDown(tester.view.reset);
    await tester.pumpWidget(goldenApp(home: WelcomePage(onStart: () {}), brightness: Brightness.light));
    await tester.pump();
    expect(find.text(appL10n.usageStatsTitle), findsNothing);
    expect(find.byKey(const ValueKey('usage-notice')), findsNothing);
    expect(find.byType(FilledButton), findsOneWidget);
  });

  testWidgets('privacy does not offer usage statistics or chat-log upload', (tester) async {
    await engine.pumpPage(
      tester,
      const PrivacyPage(),
      overrides: engine.engineOverrides(DshClient.withCarrier(engine.FakeSettingsHost())),
    );
    expect(find.text(appL10n.usageStatsTitle), findsNothing);
    expect(find.text(appL10n.engineChatLogUpload), findsNothing);
    expect(find.text(appL10n.hubSettingsShareConfig), findsOneWidget);
  });
}
