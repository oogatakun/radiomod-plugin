package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radiomod.net.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotRouterTest {

    private static final long MS = 1_000_000L;

    private static final class FakeControl implements SlotControl {
        final List<String> calls = new ArrayList<>();
        LongSupplier desired;

        @Override
        public void play(String url, LongSupplier desiredPositionMs) {
            calls.add("play " + url);
            desired = desiredPositionMs;
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

    private final Map<Messages.Slot, FakeControl> controls = new HashMap<>();
    private final AtomicLong nano = new AtomicLong();
    private ServerClock clock;
    private long manualOffset;
    private SlotRouter router;

    @BeforeEach
    void setUp() {
        clock = new ServerClock(nano::get);
        // サーバー時刻 = 自分の nanoTime(ms) + 1_000_000
        clock.onResponse(0, 2 * MS, 1_000_001L);
        router = new SlotRouter(s -> controls.computeIfAbsent(s, k -> new FakeControl()), clock, () -> manualOffset);
    }

    private static Messages.Play play(Messages.Slot slot, long durationMs, long originMs) {
        Messages.RadioTarget radio = slot.slotType() == Messages.Slot.RADIO
                ? new Messages.RadioTarget("minecraft:overworld", 0, 0, 0, 24) : null;
        return new Messages.Play(slot, 7, "https://example.com/a.mp3", "a", durationMs, originMs, radio);
    }

    @Test
    void PLAYは個人と共有のスロットごとに独立して再生を始める() {
        router.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_000L));
        router.handle(play(Messages.Slot.SHARED_SLOT, 200_000, 1_000_000L));
        assertEquals(List.of("play https://example.com/a.mp3"), controls.get(Messages.Slot.PERSONAL_SLOT).calls);
        assertEquals(List.of("play https://example.com/a.mp3"), controls.get(Messages.Slot.SHARED_SLOT).calls);
    }

    @Test
    void 鳴らすべき位置は_いまのサーバー時刻から基準時刻を引いて手動の補正を足した値() {
        router.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_500L));
        LongSupplier desired = controls.get(Messages.Slot.PERSONAL_SLOT).desired;
        nano.set(0);
        assertEquals(-500, desired.getAsLong()); // 基準時刻より500ms前は負（待つ）
        nano.set(2500 * MS);
        assertEquals(2000, desired.getAsLong());
        manualOffset = 30;
        assertEquals(2030, desired.getAsLong());
    }

    @Test
    void 時計が合う前は負の値を返して待たせる() {
        ServerClock unsynced = new ServerClock(nano::get);
        SlotRouter r = new SlotRouter(s -> controls.computeIfAbsent(s, k -> new FakeControl()), unsynced, () -> 0);
        r.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_000L));
        assertTrue(controls.get(Messages.Slot.PERSONAL_SLOT).desired.getAsLong() < 0);
    }

    @Test
    void ライブは位置合わせをしない() {
        router.handle(play(Messages.Slot.SHARED_SLOT, -1, 1_000_000L));
        assertNull(controls.get(Messages.Slot.SHARED_SLOT).desired);
    }

    @Test
    void SYNCは基準時刻だけを変えて_再生には何もしない() {
        router.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_000L));
        FakeControl c = controls.get(Messages.Slot.PERSONAL_SLOT);
        nano.set(1000 * MS);
        assertEquals(1000, c.desired.getAsLong());
        router.handle(new Messages.Sync(Messages.Slot.PERSONAL_SLOT, 1_000_400L));
        assertEquals(600, c.desired.getAsLong());
        assertEquals(1, c.calls.size()); // play のみ
    }

    @Test
    void PAUSEとRESUMEとSTOPが対応する操作になる() {
        router.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_000L));
        FakeControl c = controls.get(Messages.Slot.PERSONAL_SLOT);
        router.handle(new Messages.Pause(Messages.Slot.PERSONAL_SLOT, 12_345));
        router.handle(new Messages.Resume(Messages.Slot.PERSONAL_SLOT, 1_000_900L));
        nano.set(1000 * MS);
        assertEquals(100, c.desired.getAsLong()); // RESUME の基準時刻に切り替わっている
        router.handle(new Messages.Stop(Messages.Slot.PERSONAL_SLOT));
        assertEquals(List.of("play https://example.com/a.mp3", "pause 12345", "resume", "stop"), c.calls);
        assertEquals(-1, router.trackIdOf(Messages.Slot.PERSONAL_SLOT));
    }

    @Test
    void 再生していないスロットへの操作は無視する() {
        router.handle(new Messages.Pause(Messages.Slot.SHARED_SLOT, 1));
        router.handle(new Messages.Resume(Messages.Slot.SHARED_SLOT, 1));
        router.handle(new Messages.Sync(Messages.Slot.SHARED_SLOT, 1));
        router.handle(new Messages.Stop(Messages.Slot.SHARED_SLOT));
        assertTrue(controls.isEmpty());
    }

    @Test
    void 同じスロットへの新しいPLAYは置き換える() {
        router.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_000L));
        router.handle(new Messages.Play(Messages.Slot.PERSONAL_SLOT, 8, "https://example.com/b.mp3", "b", 1000,
                1_000_000L, null));
        assertEquals(8, router.trackIdOf(Messages.Slot.PERSONAL_SLOT));
        assertEquals("play https://example.com/b.mp3", controls.get(Messages.Slot.PERSONAL_SLOT).calls.get(1));
    }

    @Test
    void ラジオスロットはこのステップでは鳴らさない() {
        Messages.Slot radio = Messages.Slot.radio(UUID.randomUUID());
        router.handle(play(radio, 180_000, 1_000_000L));
        router.handle(new Messages.Move(radio, "minecraft:overworld", 1, 2, 3));
        assertTrue(controls.isEmpty());
    }

    @Test
    void 切断で全スロットを止める() {
        router.handle(play(Messages.Slot.PERSONAL_SLOT, 180_000, 1_000_000L));
        router.handle(play(Messages.Slot.SHARED_SLOT, 180_000, 1_000_000L));
        router.stopAll();
        assertEquals("stop", controls.get(Messages.Slot.PERSONAL_SLOT).calls.get(1));
        assertEquals("stop", controls.get(Messages.Slot.SHARED_SLOT).calls.get(1));
        assertNull(router.originOf(Messages.Slot.PERSONAL_SLOT));
    }

    @Test
    void サーバー向けのメッセージが届いても例外にならない() {
        router.handle(new Messages.Hello(2, "x"));
        assertTrue(controls.isEmpty());
    }
}
