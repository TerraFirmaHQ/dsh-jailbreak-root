import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../settings/settings_widgets.dart';
import '../theme/app_icons.dart';
import '../theme/theme.dart';

/// 设置 → 关于: one locked-version row. There is no check and no automatic check.
class AppUpdateTiles extends ConsumerWidget {
  const AppUpdateTiles({super.key, this.tint});

  /// The group hue on the settings hub (rows get icons); null for plain rows.
  final int? tint;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tint = this.tint;
    return SettingsTile(
      icon: tint == null ? null : AppIcons.info,
      tint: tint ?? 0,
      spot: Spots.lockedEdition,
    );
  }
}

/// The "new version" mark: a dot and a short label on a tinted pill.
class UpdateBadge extends StatelessWidget {
  const UpdateBadge(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final colors = DshColors.of(context);
    return DecoratedBox(
      decoration: ShapeDecoration(color: scheme.primaryContainer, shape: const StadiumBorder()),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(8, 3, 10, 3),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            DecoratedBox(
              decoration: ShapeDecoration(color: colors.danger, shape: const CircleBorder()),
              child: const SizedBox.square(dimension: 6),
            ),
            const SizedBox(width: 6),
            Text(
              text,
              style: theme.textTheme.labelMedium?.copyWith(
                color: scheme.onPrimaryContainer,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
