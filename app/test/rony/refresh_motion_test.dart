import 'package:dsh_app/rony/refresh_motion.dart';
import 'package:dsh_app/ui/theme/glass.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

Duration ms(int v) => Duration(milliseconds: v);

void main() {
  test('a stray frame does not boost; sustained frames do, throttled', () {
    var sent = 0;
    final m = RefreshMotion(report: () => sent++);
    expect(m.onFrame(ms(0)), isFalse);
    expect(m.onFrame(ms(500)), isFalse); // isolated frames (cursor blink, mode change)
    expect(m.onFrame(ms(1000)), isFalse);
    expect(sent, 0);
    // 60 fps scrolling for half a second
    for (var t = 2000; t < 2500; t += 16) {
      m.onFrame(ms(t));
    }
    expect(sent, inInclusiveRange(2, 3), reason: 'one report per 250 ms while moving');
  });

  testWidgets('GlassBlurPause turns glass blur off below it', (tester) async {
    late bool paused;
    await tester.pumpWidget(GlassBlurPause(
      paused: true,
      child: Builder(builder: (context) {
        paused = GlassBlurPause.of(context);
        return const SizedBox();
      }),
    ));
    expect(paused, isTrue);
    await tester.pumpWidget(GlassBlurPause(
      paused: false,
      child: Builder(builder: (context) {
        paused = GlassBlurPause.of(context);
        return const SizedBox();
      }),
    ));
    expect(paused, isFalse);
  });

  testWidgets('GlassScrollPause: a user drag pauses blur, it comes back after the scroll settles; programmatic scrolls do not', (tester) async {
    final seen = <bool>[];
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(Directionality(
      textDirection: TextDirection.ltr,
      child: GlassScrollPause(
        child: Stack(children: [
          ListView.builder(controller: controller, itemExtent: 50, itemCount: 200, itemBuilder: (_, i) => Text('$i')),
          Builder(builder: (context) {
            seen.add(GlassBlurPause.of(context));
            return const SizedBox();
          }),
        ]),
      ),
    ));
    expect(seen.last, isFalse);
    // A streaming reply following the bottom: no drag, no pause.
    controller.jumpTo(400);
    await tester.pump();
    expect(seen.last, isFalse);
    final g = await tester.startGesture(const Offset(100, 300));
    await g.moveBy(const Offset(0, -40));
    await tester.pump();
    expect(seen.last, isTrue);
    await g.moveBy(const Offset(0, -40));
    await g.up();
    await tester.pumpAndSettle();
    await tester.pump(const Duration(milliseconds: 300));
    expect(seen.last, isFalse);
  });
}

