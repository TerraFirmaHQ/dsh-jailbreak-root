// Rony fork v5.1: 活人感 in the in-app chat — split-send + typing delay. Both exist only while
// the ONE 活人感 switch ([ronyHumanlike]) is on; off = the stock single reply, streamed as before.
//
// A finished casual reply (no code, lists, tables or headings; short) whose paragraphs read like
// separate chat messages is shown as up to [kHumanlikeMaxBubbles] bubbles that appear one after
// another with a typing indicator in between, the delay growing with each bubble's length. While
// such a reply is still streaming it shows the typing indicator instead of half-written text.
// Anything that looks like real work (code, Markdown structure, long text) is never touched.

import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

const int kHumanlikeMaxBubbles = 4;
const int kHumanlikeMaxChars = 600;

final RegExp _structure = RegExp(r'(^|\n)\s*(```|~~~|#{1,6}\s|[-*+]\s|\d+[.)]\s|\||>\s|<dsh-ui)');

/// Whether [text] looks like casual chat (eligible for 活人感 treatment at all).
bool humanlikeCasual(String text) {
  final t = text.trim();
  if (t.isEmpty || t.length > kHumanlikeMaxChars) return false;
  if (_structure.hasMatch('\n$t')) return false;
  if (t.contains('`') || t.contains('](') || t.contains('\$\$')) return false;
  return true;
}

/// The bubbles for a finished reply, or null when it should render as one normal message.
List<String>? humanlikeSplit(String text) {
  if (!humanlikeCasual(text)) return null;
  final parts = text.trim().split(RegExp(r'\n\s*\n')).map((p) => p.trim()).where((p) => p.isNotEmpty).toList();
  if (parts.length < 2) return null;
  if (parts.length > kHumanlikeMaxBubbles) {
    final head = parts.take(kHumanlikeMaxBubbles - 1).toList();
    head.add(parts.skip(kHumanlikeMaxBubbles - 1).join('\n\n'));
    return head;
  }
  return parts;
}

/// Typing delay before bubble [index] (the first one shows at once): ~45 ms a character, 0.5–1.8 s.
Duration humanlikeDelay(String bubble, int index) =>
    index == 0 ? Duration.zero : Duration(milliseconds: math.min(1800, math.max(500, bubble.runes.length * 45)));

/// Reveals [bubbles] one by one ([animate]) or all at once (an old message, or reduced motion).
class HumanlikeBubbles extends StatefulWidget {
  const HumanlikeBubbles({super.key, required this.bubbles, required this.animate, required this.bubbleBuilder, required this.typing});

  final List<String> bubbles;
  final bool animate;
  final Widget Function(BuildContext context, String bubble) bubbleBuilder;
  final Widget typing;

  @override
  State<HumanlikeBubbles> createState() => _HumanlikeBubblesState();
}

class _HumanlikeBubblesState extends State<HumanlikeBubbles> {
  late int _shown = widget.animate ? 1 : widget.bubbles.length;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _next();
  }

  @override
  void didUpdateWidget(HumanlikeBubbles old) {
    super.didUpdateWidget(old);
    if (!widget.animate && _shown < widget.bubbles.length) {
      _timer?.cancel();
      _shown = widget.bubbles.length;
    }
  }

  void _next() {
    if (_shown >= widget.bubbles.length) return;
    _timer = Timer(humanlikeDelay(widget.bubbles[_shown], _shown), () {
      if (!mounted) return;
      setState(() => _shown++);
      _next();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final shown = math.min(_shown, widget.bubbles.length);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < shown; i++) ...[
          if (i > 0) const SizedBox(height: 10),
          KeyedSubtree(key: ValueKey('humanlike-bubble-$i'), child: widget.bubbleBuilder(context, widget.bubbles[i])),
        ],
        if (shown < widget.bubbles.length) ...[
          const SizedBox(height: 10),
          KeyedSubtree(key: const ValueKey('humanlike-typing'), child: widget.typing),
        ],
      ],
    );
  }
}
