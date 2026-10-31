package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radiomod.net.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RadioSlotControlTest {

    private static final class FakeControl implements SlotControl {
        final List<String> calls = new ArrayList<>();

        @Override
        public void play(String url, LongSupplier desiredPositionMs) {
            calls.add("play " + url);
        }

        @Override
        public void pause(long positionMs) {
            calls.add("pause " + positionMs);
        }

        @Override
        public void resume() {
            calls.add("resume");
        }

        @Override
        public void stop() {
            calls.add("stop");
        }
    }

    private final FakeControl inner = new FakeControl();
    private RadioPlace.ListenerPos listener;
    private boolean enabled;
    private int disposed;
    private RadioSlotControl control;

    private void listenerAt(String dim, double x) {
        listener = new RadioPlace.ListenerPos(dim, x, 0, 0);
    }

    @BeforeEach
    void setUp() {
        enabled = true;
        disposed = 0;
        listenerAt("minecraft:overworld", 100);
        RadioPlace place = new RadioPlace(() -> listener);
        control = new RadioSlotControl(inner, place, () -> enabled, () -> disposed++);
        control.place(new Messages.RadioTarget("minecraft:overworld", 0, 0, 0, 24));
    }

    @Test
    void 範囲の外では取得しない() {
        control.play("u", () -> 0);
        assertEquals(List.of(), inner.calls);
        control.tick();
        assertEquals(List.of(), inner.calls);
    }

    @Test
    void 範囲に入ると再生を始め_範囲の外に出ると止める() {
        control.play("u", () -> 0);
        listenerAt("minecraft:overworld", 10);
        control.tick();
        assertEquals(List.of("play u"), inner.calls);
        control.tick(); // 入ったままなら何もしない
        assertEquals(List.of("play u"), inner.calls);
        listenerAt("minecraft:overworld", 100);
        control.tick();
        assertEquals(List.of("play u", "stop"), inner.calls);
    }

    @Test
    void 範囲の縁で行ったり来たりしない() {
        control.play("u", () -> 0);
        listenerAt("minecraft:overworld", 24 + RadioSlotControl.START_MARGIN - 0.1);
        control.tick();
        assertEquals(List.of("play u"), inner.calls);
        listenerAt("minecraft:overworld", 24 + RadioSlotControl.START_MARGIN + 1); // 始める線は越えたが、止める線の内側
        control.tick();
        assertEquals(List.of("play u"), inner.calls);
        listenerAt("minecraft:overworld", 24 + RadioSlotControl.STOP_MARGIN + 0.1);
        control.tick();
        assertEquals(List.of("play u", "stop"), inner.calls);
    }

    @Test
    void 別の次元にいるあいだは鳴らさない() {
        listenerAt("minecraft:the_nether", 0);
        control.play("u", () -> 0);
        control.tick();
        assertEquals(List.of(), inner.calls);
        listenerAt("minecraft:overworld", 0);
        control.tick();
        assertEquals(List.of("play u"), inner.calls);
    }

    @Test
    void 新しいPLAYは前の曲をすぐ止めて置き換える() {
        listenerAt("minecraft:overworld", 5);
        control.play("a", () -> 0);
        control.play("b", () -> 0);
        assertEquals(List.of("play a", "stop", "play b"), inner.calls);
    }

    @Test
    void 一時停止中に範囲に入ったら_止まった位置から取得して止まったまま待つ() {
        control.play("u", () -> 0);
        control.pause(12_000);
        assertEquals(List.of(), inner.calls);
        listenerAt("minecraft:overworld", 5);
        control.tick();
        assertEquals(List.of("play u", "pause 12000"), inner.calls);
        control.resume();
        assertEquals(List.of("play u", "pause 12000", "resume"), inner.calls);
    }

    @Test
    void 範囲の外での再開は_範囲に入ってから鳴らし始める() {
        control.play("u", () -> 0);
        control.pause(1000);
        control.resume();
        assertEquals(List.of(), inner.calls);
        listenerAt("minecraft:overworld", 5);
        control.tick();
        assertEquals(List.of("play u"), inner.calls);
    }

    @Test
    void MOVEで遠ざかると止まり_近づくと始まる() {
        listenerAt("minecraft:overworld", 5);
        control.play("u", () -> 0);
        control.move("minecraft:overworld", 500, 0, 0);
        assertEquals(List.of("play u", "stop"), inner.calls);
        control.move("minecraft:overworld", 0, 0, 0);
        assertEquals(List.of("play u", "stop", "play u"), inner.calls);
    }

    @Test
    void ラジオ音がオフなら範囲の中でも取得しない() {
        enabled = false;
        listenerAt("minecraft:overworld", 5);
        control.play("u", () -> 0);
        assertEquals(List.of(), inner.calls);
        enabled = true;
        control.tick();
        assertEquals(List.of("play u"), inner.calls);
    }

    @Test
    void STOPのあとは範囲に入っても鳴らさず_disposeで片付ける() {
        listenerAt("minecraft:overworld", 5);
        control.play("u", () -> 0);
        control.stop();
        control.tick();
        assertEquals(List.of("play u", "stop"), inner.calls);
        control.dispose();
        assertEquals(1, disposed);
        assertTrue(control.stateName().length() >= 0);
    }
}
