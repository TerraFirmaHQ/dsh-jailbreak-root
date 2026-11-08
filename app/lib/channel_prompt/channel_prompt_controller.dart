import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/host_root_state.dart';
import 'channel_prompt.dart';

/// The channel prompt file. A missing native side stays an empty document,
/// which is null/true for every channel.
class ChannelPromptController extends Notifier<ChannelPromptDocument> {
  @override
  ChannelPromptDocument build() {
    Future.microtask(reload);
    return ChannelPromptDocument.unloaded;
  }

  Future<void> reload() async {
    try {
      final raw = await HostRootController.methods.invokeMethod<String>('readChannelPrompts');
      if (!ref.mounted) return;
      state = ChannelPromptDocument.decode(raw ?? '');
    } on MissingPluginException {
      if (ref.mounted) state = ChannelPromptDocument.empty;
    } catch (_) {
      if (ref.mounted && !state.loaded) state = ChannelPromptDocument.empty;
    }
  }

  Future<void> put(String id, ChannelPromptRecord record) async {
    final next = state.put(id, record);
    final json = next.encode();
    await HostRootController.methods.invokeMethod<Object?>('writeChannelPrompts', {'json': json});
    if (ref.mounted) state = next;
  }
}

final channelPromptProvider = NotifierProvider<ChannelPromptController, ChannelPromptDocument>(
  ChannelPromptController.new,
);

final defaultPromptTextProvider = FutureProvider<String>((ref) async {
  final data = await rootBundle.load('assets/engine/hostprompt/prompt.md');
  return utf8.decode(data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes));
});
