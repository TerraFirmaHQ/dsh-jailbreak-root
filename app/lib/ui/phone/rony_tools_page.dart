// Rony ace7: 设置 → Ace7 / Root 与 MCP. No QQ. Root default off; 破甲 locked; Ace7 self-check.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../rony/rony_channel.dart';
import '../theme/theme.dart';
import 'rony_persona_page.dart';

class RonyToolsPage extends StatefulWidget {
  const RonyToolsPage({super.key, this.channel = const RonyChannel()});

  final RonyChannel channel;

  @override
  State<RonyToolsPage> createState() => _RonyToolsPageState();
}

class _RonyToolsPageState extends State<RonyToolsPage> {
  Map<String, Object?> _state = const {};
  Map<String, Object?>? _selfCheck;
  bool _loading = true;
  String? _error;
  RonyPojiaInfo? _pojia;

  @override
  void initState() {
    super.initState();
    _reload();
    loadRonyPojiaInfo().then((i) {
      if (mounted) setState(() => _pojia = i);
    });
  }

  Future<void> _reload() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final s = await widget.channel.state();
      if (!mounted) return;
      setState(() {
        _state = s;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  bool get _rootOn => _state['rootEnabled'] == true;
  bool get _pojiaOn => _state['pojiaEnabled'] != false;
  bool get _approval => _state['approval'] != false;
  String get _refreshMode => (_state['refreshMode'] as String?) ?? 'auto';

  Future<void> _toggleRoot(bool want) async {
    if (want) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('打开 Root 模式？'),
          content: const Text(
            '打开后 App 才会去要 su，并可能弹出 KernelSU 授权。\n\n'
            '请只在 KSU 里授权「dsh Ace7」这一个 App。\n'
            '隐藏环境目录（KSU / Zygisk / HMA / Tricky Store / Shamiko）写进硬黑名单，模型碰不到。\n\n'
            '建议：装前装后用春秋检测 / Key Attestation 对比隐藏环境。',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('打开')),
          ],
        ),
      );
      if (ok != true) return;
    }
    final s = await widget.channel.setRootEnabled(want);
    if (!mounted) return;
    setState(() => _state = s);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(want ? 'Root 模式已开：请到 KSU 允许本 App，然后重启引擎' : 'Root 模式已关：不会再请求 su')),
    );
  }

  Future<void> _runSelfCheck() async {
    final r = await widget.channel.ace7SelfCheck();
    if (!mounted) return;
    setState(() => _selfCheck = r);
  }

  @override
  Widget build(BuildContext context) {
    final colors = DshColors.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Ace7 / Root 与 MCP')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              children: [
                if (_error != null) Text(_error!, style: TextStyle(color: colors.warning)),
                _card(
                  title: 'Ace7 Pro 适配',
                  children: [
                    Text(
                      '型号：${_state['deviceBrand'] ?? '?'} ${_state['deviceModel'] ?? '?'}\n'
                      '包名：${_state['package'] ?? 'io.github.ronyfork.dsh_app'}\n'
                      '隐藏环境硬黑名单：始终生效\n'
                      '165Hz：默认跟随系统；自检页列出设备真实 Display 模式（装机实测）',
                      style: TextStyle(color: colors.textTertiary, height: 1.4),
                    ),
                    const SizedBox(height: 8),
                    FilledButton.tonal(onPressed: _runSelfCheck, child: const Text('一键自检')),
                    if (_selfCheck != null) ...[
                      const SizedBox(height: 8),
                      SelectableText(_formatSelfCheck(_selfCheck!), style: TextStyle(fontFamily: 'monospace', fontSize: 12, color: colors.textTertiary)),
                    ],
                  ],
                ),
                _card(
                  title: '破甲提示词（系统提示词第一段，锁死）',
                  children: [
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('启用破甲（默认开）'),
                      subtitle: Text(
                        _pojia == null
                            ? '内置 DeepSeek-4.1-Flash-破甲提示词.md，不可编辑'
                            : 'sha256 ${_pojia!.shortSha}… · ${_pojia!.bytes} B · ${_pojia!.matchesLocked ? "校验通过" : "校验异常"}',
                      ),
                      value: _pojiaOn,
                      onChanged: (v) async {
                        final s = await widget.channel.setPojiaEnabled(v);
                        if (mounted) setState(() => _state = s);
                      },
                    ),
                    Text(
                      '文案锁死不可改。挂载状态：${_state['pojiaMounted'] == true ? "已注入本引擎" : "未注入（关着或引擎未重启）"}。改开关后请重启引擎。',
                      style: TextStyle(color: colors.textTertiary, fontSize: 12),
                    ),
                  ],
                ),
                _card(
                  title: '人设偏好',
                  children: [
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('自定义人设'),
                      subtitle: Text(
                        ((_state['personaText'] as String?)?.trim().isNotEmpty ?? false)
                            ? '已填写（写在破甲之后）'
                            : '默认空白：只用人设空，不注入人设段',
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => RonyPersonaPage(channel: widget.channel)));
                        await _reload();
                      },
                    ),
                  ],
                ),
                _card(
                  title: 'Root 模式（默认关）',
                  children: [
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Root 模式'),
                      subtitle: Text(_rootOn
                          ? '已开：可挂载 mcp__root__*；改动类命令默认先问你'
                          : '关着：完全不请求 su、不弹 KSU、不探测执行'),
                      value: _rootOn,
                      onChanged: _toggleRoot,
                    ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('危险命令审批'),
                      subtitle: const Text('默认开。关掉后危险命令不再弹卡（仍拦黑名单）'),
                      value: _approval,
                      onChanged: _rootOn
                          ? (v) async {
                              final s = await widget.channel.setApproval(v);
                              if (mounted) setState(() => _state = s);
                            }
                          : null,
                    ),
                    if (_rootOn)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('检测 Root 授权'),
                        subtitle: Text('上次：${(_state['root'] as Map?)?['state'] ?? "unknown"}'),
                        trailing: const Icon(Icons.refresh),
                        onTap: () async {
                          final r = await widget.channel.checkRoot();
                          await _reload();
                          if (!mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Root：${r['state']} ${r['manager'] ?? ""}')));
                        },
                      ),
                    Text(
                      '硬黑名单：/data/adb、KSU/Zygisk/HMA/TrickyStore/Shamiko、oplus_secure_guard、remount、setenforce、装卸模块、resetprop…',
                      style: TextStyle(color: colors.textTertiary, fontSize: 12),
                    ),
                  ],
                ),
                _card(
                  title: '刷新率（Ace7 165Hz）',
                  children: [
                    SegmentedButton<String>(
                      segments: const [
                        ButtonSegment(value: 'auto', label: Text('自动')),
                        ButtonSegment(value: 'max', label: Text('最高')),
                        ButtonSegment(value: 'system', label: Text('跟随系统')),
                      ],
                      selected: {_refreshMode},
                      onSelectionChanged: (s) async {
                        final m = s.first;
                        final st = await widget.channel.setRefreshMode(m);
                        if (mounted) setState(() => _state = st);
                      },
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '当前：${(_state['refresh'] as Map?)?['current'] ?? "?"} Hz · 设备最高：${(_state['refresh'] as Map?)?['max'] ?? "?"} Hz\n'
                      '模式列表见自检。钉具体 modeId（如 165）可在自检后用「钉模式」——本页先用三档；详细 modeId 在自检结果里。',
                      style: TextStyle(color: colors.textTertiary, fontSize: 12, height: 1.4),
                    ),
                    ..._modeIdTiles(),
                  ],
                ),
                _card(
                  title: 'MCP 服务',
                  children: [
                    Text(
                      '已挂载：${((_state['mcpMounted'] as List?) ?? const []).join(", ").ifEmpty("（无）")}\n'
                      'Root 工具在 Root 模式开且引擎重启后以 mcp__root__* 出现。',
                      style: TextStyle(color: colors.textTertiary, height: 1.4),
                    ),
                  ],
                ),
                _card(
                  title: 'Linux 增强（骨架）',
                  children: [
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('chroot + unshare -m（默认关）'),
                      subtitle: const Text('第一版仅存开关，路径未接线；官方 proot 照常可用'),
                      value: _state['chrootEnhance'] == true,
                      onChanged: (v) async {
                        final s = await widget.channel.setChrootEnhance(v);
                        if (mounted) setState(() => _state = s);
                      },
                    ),
                  ],
                ),
                TextButton(
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: _state.toString()));
                    if (!mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已复制状态')));
                  },
                  child: const Text('复制调试状态'),
                ),
              ],
            ),
    );
  }

  List<Widget> _modeIdTiles() {
    final details = ((_state['refresh'] as Map?)?['modeDetails'] as List?) ?? const [];
    if (details.isEmpty) return const [];
    return [
      const SizedBox(height: 8),
      const Text('钉设备真实模式（Ace7）：', style: TextStyle(fontWeight: FontWeight.w600)),
      for (final raw in details)
        if (raw is Map)
          ListTile(
            contentPadding: EdgeInsets.zero,
            dense: true,
            title: Text('${(raw['refreshRate'] as num?)?.toStringAsFixed(2) ?? "?"} Hz  ·  ${raw['width']}×${raw['height']}'),
            subtitle: Text('modeId=${raw['modeId']}${raw['current'] == true ? " · 当前" : ""}'),
            trailing: TextButton(
              onPressed: () async {
                final id = (raw['modeId'] as num?)?.toInt() ?? 0;
                final s = await widget.channel.setRefreshModeId(id);
                if (mounted) setState(() => _state = s);
              },
              child: const Text('钉住'),
            ),
          ),
      TextButton(
        onPressed: () async {
          final s = await widget.channel.setRefreshModeId(0);
          if (mounted) setState(() => _state = s);
        },
        child: const Text('清除钉住（回三档）'),
      ),
    ];
  }

  Widget _card({required String title, required List<Widget> children}) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
            const SizedBox(height: 8),
            ...children,
          ],
        ),
      ),
    );
  }

  String _formatSelfCheck(Map<String, Object?> r) {
    final modes = (r['modes'] as List?) ?? const [];
    final buf = StringBuffer();
    buf.writeln('rootMode=${r['rootMode']}  suVisible=${r['suVisible']}  maxHz=${r['maxHz']}');
    buf.writeln('hideBlacklist=${r['hideBlacklist']}');
    buf.writeln('modes:');
    for (final m in modes) {
      buf.writeln('  $m');
    }
    buf.writeln('rootCheck=${r['rootCheck']}');
    buf.writeln('note=${r['note']}');
    return buf.toString();
  }
}

extension on String {
  String ifEmpty(String alt) => isEmpty ? alt : this;
}
