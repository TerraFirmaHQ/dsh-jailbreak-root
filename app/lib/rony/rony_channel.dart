// Rony ace7: Dart side of MethodChannel `dsh/rony`. QQ stack removed.

import 'dart:convert';

import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/foundation.dart' show ValueNotifier;
import 'package:flutter/services.dart';

/// No network update check: official releases must never replace the fork.
const bool kRonyOnlineUpdatesDisabled = true;

const String kRonyPojiaAsset = 'assets/engine/pojia/prompt.md';
const String kRonyPojiaSource = 'DeepSeek-4.1-Flash-破甲提示词.md';
const String kRonyPojiaVersion = '4.1';
/// Locked expected sha256 of the bundled prompt (DeepSeek-4.1-Flash-破甲提示词.md).
const String kRonyPojiaSha256 = '4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118';

class RonyPojiaInfo {
  const RonyPojiaInfo({required this.bytes, required this.chars, required this.sha256});

  final int bytes;
  final int chars;
  final String sha256;

  String get shortSha => sha256.length > 12 ? sha256.substring(0, 12) : sha256;
  bool get matchesLocked => sha256 == kRonyPojiaSha256;

  static RonyPojiaInfo of(List<int> data) =>
      RonyPojiaInfo(bytes: data.length, chars: utf8.decode(data, allowMalformed: true).runes.length, sha256: sha256Of(data));

  static String sha256Of(List<int> data) => crypto.sha256.convert(data).toString();
}

Future<RonyPojiaInfo> loadRonyPojiaInfo([AssetBundle? bundle]) async {
  final data = await (bundle ?? rootBundle).load(kRonyPojiaAsset);
  return RonyPojiaInfo.of(data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes));
}

class RonyMcpServer {
  const RonyMcpServer({required this.name, this.url, this.command, this.enabled = true, this.hasToken = false, this.token});

  factory RonyMcpServer.fromMap(Map<Object?, Object?> m) => RonyMcpServer(
    name: (m['name'] as String?) ?? '',
    url: m['url'] as String?,
    command: m['command'] as String?,
    enabled: m['enabled'] != false,
    hasToken: m['hasToken'] == true,
  );

  final String name;
  final String? url;
  final String? command;
  final bool enabled;
  final bool hasToken;
  final String? token;

  static final nameRe = RegExp(r'^[A-Za-z0-9_-]{1,32}$');
  static const reserved = {'phone', 'root', 'dsh', 'mcp'};

  static String? problem(String name, String target) {
    if (!nameRe.hasMatch(name)) return '名字只能用英文字母、数字、_、-，最多 32 个';
    if (reserved.contains(name.toLowerCase())) return '名字 $name 被占用了，换一个';
    final t = target.trim();
    if (t.isEmpty) return '填 http(s) 地址，或在 Linux 环境里启动它的命令';
    if (t.contains('://') && !(t.startsWith('http://') || t.startsWith('https://'))) return '地址只支持 http:// 或 https://';
    return null;
  }

  RonyMcpServer copyWith({bool? enabled, String? token}) =>
      RonyMcpServer(name: name, url: url, command: command, enabled: enabled ?? this.enabled, hasToken: hasToken || (token?.isNotEmpty ?? false), token: token ?? this.token);

  Map<String, Object?> toWire() => {
    'name': name,
    'url': url,
    'command': command,
    'enabled': enabled,
    'token': token,
    'keepToken': token == null && hasToken,
  };
}

class RonyChannel {
  const RonyChannel([this._channel = const MethodChannel('dsh/rony')]);

  final MethodChannel _channel;

  Future<Map<String, Object?>> _map(String method, [Object? args]) async {
    final r = await _channel.invokeMapMethod<String, Object?>(method, args);
    return r ?? const {};
  }

  Future<Map<String, Object?>> state() => _map('state');
  Future<Map<String, Object?>> setRootEnabled(bool on) => _map('setRootEnabled', {'on': on});
  Future<Map<String, Object?>> setApproval(bool on) => _map('setApproval', {'on': on});
  Future<Map<String, Object?>> checkRoot() => _map('checkRoot');
  Future<Map<String, Object?>> runRoot(String command) => _map('runRoot', {'command': command});
  Future<Map<String, Object?>> setPojiaEnabled(bool on) => _map('setPojiaEnabled', {'on': on});
  Future<Map<String, Object?>> setRefreshMode(String mode) => _map('setRefreshMode', {'mode': mode});
  Future<Map<String, Object?>> setRefreshModeId(int modeId) => _map('setRefreshModeId', {'modeId': modeId});
  Future<Map<String, Object?>> ace7SelfCheck() => _map('ace7SelfCheck');

  Future<Map<String, Object?>> setMcpServers(List<RonyMcpServer> servers) =>
      _map('setMcpServers', {'servers': [for (final s in servers) s.toWire()]});

  Future<String?> mcpToken(String name) => _channel.invokeMethod<String>('mcpToken', {'name': name});

  Future<List<Map<String, Object?>>> audit() async {
    final r = await _channel.invokeListMethod<Object?>('audit') ?? const [];
    return [for (final e in r) if (e is Map) e.cast<String, Object?>()];
  }

  Future<void> clearAudit() => _channel.invokeMethod<bool>('clearAudit');

  Future<Map<String, Object?>> linuxStatus() => _map('linuxStatus');
  Future<Map<String, Object?>> linuxInstall() => _map('linuxInstall');
  Future<Map<String, Object?>> linuxTools() => _map('linuxTools');
  Future<Map<String, Object?>> linuxJob(String id) => _map('linuxJob', {'id': id});
  Future<Map<String, Object?>> linuxWipe() => _map('linuxWipe');
  Future<Map<String, Object?>> setLinuxProxy(String proxy) => _map('setLinuxProxy', {'proxy': proxy});
  Future<Map<String, Object?>> setChrootEnhance(bool on) => _map('setChrootEnhance', {'on': on});

  Future<Map<String, Object?>> setHumanlike(bool on) => _map('setHumanlike', {'on': on});
  Future<Map<String, Object?>> setPersonaText(String text) => _map('setPersonaText', {'text': text});
}

/// 活人感 optional; ace7 default off. Persona overlay mounts when personaText is non-empty.
final ValueNotifier<bool> ronyHumanlike = ValueNotifier<bool>(false);

Future<void> loadRonyHumanlike([RonyChannel channel = const RonyChannel()]) async {
  try {
    final s = await channel.state();
    ronyHumanlike.value = s['humanlike'] == true;
  } catch (_) {}
}

void ronyTouchUserMessage([MethodChannel channel = const MethodChannel('dsh/rony')]) {
  try {
    channel.invokeMethod<void>('touchUserMessage').catchError((Object _) {});
  } catch (_) {}
}
