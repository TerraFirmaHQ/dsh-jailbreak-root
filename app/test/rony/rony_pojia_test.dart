import 'dart:io';

import 'package:dsh_app/rony/rony_channel.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// sha256 of DeepSeek-4.1-Flash-破甲提示词.md as downloaded from the owner's Drive.
const _expectedSha = '4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('the 破甲 prompt is bundled, non-empty and unchanged', () async {
    final info = await loadRonyPojiaInfo(rootBundle);
    expect(info.bytes, greaterThan(1000));
    expect(info.sha256, _expectedSha);
  });

  test('the engine plugin is bundled next to it and injects it first, literally', () async {
    final plugin = await rootBundle.loadString('assets/engine/pojia/index.mjs');
    expect(plugin, contains("inject = ['systemPrompt']"));
    expect(plugin, contains('SECTION_ORDER = -2000'));
    expect(plugin, contains('interpolate: false'));
    expect(plugin, contains("join(here, 'prompt.md')"));
  });

  test('the overlay writer copies both files and mounts the plugin by path', () {
    final kt = File('android/app/src/main/kotlin/io/github/ccch1mneyyy/dsh_app/rony/RonyPojia.kt').readAsStringSync();
    expect(kt, contains('listOf("index.mjs", PROMPT)'));
    expect(kt, contains(r'name: $ENTRY'));
    final prefs = File('android/app/src/main/kotlin/io/github/ccch1mneyyy/dsh_app/rony/RonyPrefs.kt').readAsStringSync();
    expect(prefs, contains('getBoolean(K_POJIA, true)'), reason: '破甲提示词 defaults to on');
  });

  test('size and hash helpers', () {
    final info = RonyPojiaInfo.of('破甲'.codeUnits.map((c) => c & 0xff).toList());
    expect(info.bytes, 2);
    expect(RonyPojiaInfo.sha256Of(const []), 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  });
}
