// 隐私与分享: links you shared and a configuration to share.
// This edition does not offer usage statistics or chat-log upload.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/l10n.dart';
import '../share_code/profile_share_sheet.dart';
import '../share_hosted/hosted_links_tile.dart';
import '../shell/page_frame.dart';
import 'settings_widgets.dart';

class PrivacyPage extends ConsumerWidget {
  const PrivacyPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    return AppPage.list(
      title: l10n.hubPrivacy,
      children: [
        SettingsSection(
          title: l10n.hubPrivacySharing,
          children: [
            const HostedLinksSettingsTile(icon: false),
            SettingsTile(spot: Spots.shareConfig, onTap: () => unawaited(showShareProfileSheet(context))),
          ],
        ),
      ],
    );
  }
}
