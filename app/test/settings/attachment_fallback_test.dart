import 'package:dsh_app/l10n/l10n.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('attachment failures tell the person where to put the file', () {
    for (final text in [
      appL10n.chatOpenAttachmentFailed,
      appL10n.stateChatAttachmentInvalid,
      appL10n.stateAttachmentUploadFailed,
    ]) {
      expect(text, contains('/sdcard/Download/DSH-Go/'));
    }
  });
}
