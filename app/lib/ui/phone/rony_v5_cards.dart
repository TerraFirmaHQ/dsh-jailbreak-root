// Rony fork v5: the Linux 环境 card (v5.0) on 设置 → Root 与 MCP（Rony）. (QQ moved to rony_qq_page.dart in v5b.)
// Thin UI over RonyChannelV5; every root operation runs natively through RootCore (audited).

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../rony/rony_channel.dart';

Widget _card(BuildContext context, String title, List<Widget> children) => Card(
  margin: const EdgeInsets.only(bottom: 12),
  child: Padding(
    padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(title, style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      ...children,
    ]),
  ),
);

String _tail(Object? s, [int n = 1500]) {
  final t = (s ?? '').toString().trim();
  return t.length > n ? '…${t.substring(t.length - n)}' : t;
}

// ── v5.0 Linux 环境 ──

class RonyLinuxCard extends StatefulWidget {
  const RonyLinuxCard({super.key, required this.state, this.channel = const RonyChannel(), required this.onRefresh});

  final Map<String, Object?> state;
  final RonyChannel channel;
  final Future<void> Function() onRefresh;

  @override
  State<RonyLinuxCard> createState() => _RonyLinuxCardState();
}

class _RonyLinuxCardState extends State<RonyLinuxCard> {
  Map<String, Object?>? _status;
  String _out = '';
  bool _busy = false;
  String? _job;
  Timer? _poll;
  late final _proxy = TextEditingController(text: (widget.state['linuxProxy'] as String?) ?? '');

  @override
  void dispose() {
    _poll?.cancel();
    _proxy.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() body) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await body();
    } on PlatformException catch (e) {
      if (mounted) setState(() => _out = '失败：${e.message}');
    } on MissingPluginException {
      if (mounted) setState(() => _out = '原生侧不可用');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _check() => _run(() async {
    final s = await widget.channel.linuxStatus();
    if (mounted) setState(() => _status = s);
  });

  void _watch() {
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 2), (_) async {
      await widget.onRefresh();
      final task = (widget.state['linuxTask'] as Map?) ?? const {};
      final job = _job;
      if (job != null) {
        try {
          final j = await widget.channel.linuxJob(job);
          if (mounted) setState(() => _out = '${j['state'] ?? ''} ${j['exitCode'] ?? ''}\n${_tail(j['output'])}');
          if (j['state'] != 'running' && j['state'] != 'starting') _job = null;
        } catch (_) {}
      }
      if (_job == null && task['phase'] != 'download' && task['phase'] != 'unpack') {
        _poll?.cancel();
        unawaited(_check());
      }
    });
  }

  Future<void> _install() => _run(() async {
    await widget.channel.linuxInstall();
    _watch();
  });

  Future<void> _tools() => _run(() async {
    final r = await widget.channel.linuxTools();
    _job = r['jobId'] as String?;
    if (mounted) setState(() => _out = _job == null ? 'Root 总开关是关的' : '正在安装常用工具（apt，几分钟）…');
    _watch();
  });

  Future<void> _wipe() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('删除 Linux 环境？'),
        content: const Text('会删掉 /data/local/rony/debian 里的一切（装过的软件、QQ、SnowLuma）。'),
        actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')), FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('删除'))],
      ),
    );
    if (ok != true) return;
    await _run(() async {
      final r = await widget.channel.linuxWipe();
      if (mounted) setState(() => _out = _tail('${r['stdout'] ?? ''}${r['stderr'] ?? ''}'));
    });
    await _check();
  }

  @override
  Widget build(BuildContext context) {
    final task = (widget.state['linuxTask'] as Map?) ?? const {};
    final st = (_status?['status'] as Map?) ?? const {};
    final pf = (_status?['preflight'] as Map?) ?? const {};
    final phase = task['phase'] as String? ?? 'idle';
    final installed = st['installed'] == 'yes';
    final rootOn = widget.state['rootEnabled'] == true;
    final mono = Theme.of(context).textTheme.bodySmall?.copyWith(fontFamily: 'monospace');
    String line;
    if (phase == 'download') {
      final got = (task['bytes'] as num?) ?? 0, total = (task['total'] as num?) ?? 1;
      line = '下载中 ${(got * 100 / total).clamp(0, 100).toStringAsFixed(0)}%（${(got / 1048576).toStringAsFixed(1)} / ${(total / 1048576).toStringAsFixed(1)} MB，校验 sha256）';
    } else if (phase == 'unpack') {
      line = '解压中（真 Root）…';
    } else if (phase == 'error') {
      line = '安装失败：${task['error']}';
    } else if (_status == null) {
      line = '点「检查」看状态';
    } else if (installed) {
      line = '已安装 · ${st['os']} · ${((int.tryParse('${st['size_kb']}') ?? 0) / 1024).toStringAsFixed(0)} MB · 后端：${pf['result'] == 'ok' ? 'chroot（真 Root）' : '不可用（${pf['reason'] ?? pf['unshare'] ?? '?'}）'}';
    } else {
      line = pf['result'] == 'ok' ? '未安装 · 本机支持 chroot（unshare ${pf['unshare']}，busybox ${pf['busybox']}）' : '本机不支持真 Root chroot（${pf['reason'] ?? _status?['detail'] ?? '没有 Root'}）→ 用引擎自带的 Ubuntu（bash 工具，proot）';
    }
    return _card(context, 'Linux 环境（Debian 13 arm64 · 真 Root chroot）', [
      Text(line),
      if (!rootOn) const Text('需要先打开上面的 Root 总开关。', style: TextStyle(color: Colors.orange)),
      const SizedBox(height: 6),
      Wrap(spacing: 8, runSpacing: 4, children: [
        OutlinedButton(onPressed: _busy || !rootOn ? null : _check, child: const Text('检查')),
        if (!installed) FilledButton(onPressed: _busy || !rootOn || phase == 'download' || phase == 'unpack' ? null : _install, child: const Text('安装（约 35 MB）')),
        if (installed) OutlinedButton(onPressed: _busy || _job != null ? null : _tools, child: const Text('装常用工具')),
        if (installed) TextButton(onPressed: _busy ? null : _wipe, child: const Text('删除环境')),
      ]),
      TextField(
        controller: _proxy,
        decoration: const InputDecoration(labelText: '环境里的 HTTP 代理（可选，如 http://127.0.0.1:7890）', isDense: true),
        onSubmitted: (v) => _run(() async { await widget.channel.setLinuxProxy(v); await widget.onRefresh(); }),
      ),
      const SizedBox(height: 6),
      Text('AI 通过 mcp__root__linux 在这里以真 root 执行命令（apt / python / git / 服务）；私有挂载命名空间，命令结束挂载就消失。/sdcard 可用。', style: Theme.of(context).textTheme.bodySmall),
      if (_out.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: SelectableText(_out, style: mono)),
    ]);
  }
}
