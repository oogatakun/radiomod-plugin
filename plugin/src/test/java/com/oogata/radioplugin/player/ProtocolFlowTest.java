package com.oogata.radioplugin.player;

import static com.oogata.radioplugin.player.Fakes.song;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radioplugin.net.Codec;
import com.oogata.radioplugin.net.Messages;
import com.oogata.radioplugin.net.ProtocolHandler;
import com.oogata.radioplugin.player.Fakes.FakeClock;
import com.oogata.radioplugin.player.Fakes.FakeHost;
import com.oogata.radioplugin.player.Fakes.FakeResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 偽の mod クライアントを相手に、docs/protocol.md の手順どおりにメッセージが流れることを確かめる統合テスト。 */
class ProtocolFlowTest {

    private static final String A_URL = "https://example.com/a.mp3";
    private static final String B_URL = "https://example.com/b.mp3";

    /** 受け取ったメッセージを記録するだけの mod。バイト列に直して読み戻すので、符号化も通る。 */
    private final class FakeMod {
        final UUID id;
        final String name;
        final List<Messages> received = new ArrayList<>();
        private int consumed;

        FakeMod(String name) {
            this.name = name;
            this.id = host.addPlayer(name, false);
        }

        void hello(int version) {
            toServer(new Messages.Hello(version, "test"));
        }

        void toServer(Messages message) {
            try {
                protocol.onMessage(id, name, Codec.decode(Codec.encode(message)));
            } catch (Codec.DecodeException e) {
                throw new AssertionError(e);
            }
        }

        /** 前回から増えた分を取り込み、その分を返す。 */
        List<Messages> poll() {
            List<Messages> all = host.sentTo(id);
            List<Messages> fresh = new ArrayList<>();
            for (int i = consumed; i < all.size(); i++) {
                try {
                    fresh.add(Codec.decode(Codec.encode(all.get(i))));
                } catch (Codec.DecodeException e) {
                    throw new AssertionError(e);
                }
            }
            consumed = all.size();
            received.addAll(fresh);
            return fresh;
        }
    }

    private FakeClock clock;
    private FakeHost host;
    private FakeResolver resolver;
    private PlayerSoundService ps;
    private ProtocolHandler protocol;
    private final List<String> warnings = new ArrayList<>();
    private FakeMod alice;
    private FakeMod bob;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        host = new FakeHost();
        resolver = new FakeResolver();
        ps = new PlayerSoundService(host, resolver, clock, PlayerSoundService.Config.defaults());
        protocol = new ProtocolHandler(
                (id, message) -> host.send(id, message),
                clock::nowMs, s -> { }, warnings::add,
                new ProtocolHandler.Events() {
                    @Override
                    public void hello(UUID player) {
                        host.mods.add(player);
                        ps.onHello(player);
                    }

                    @Override
                    public void error(UUID player, Messages.Error error) {
                        ps.onError(player, error);
                    }
                });
        resolver.results.put(A_URL, TrackResolver.Result.single(new Track(A_URL, "曲A", 60_000)));
        resolver.results.put(B_URL, TrackResolver.Result.single(new Track(B_URL, "曲B", 60_000)));
        alice = new FakeMod("alice");
        bob = new FakeMod("bob");
    }

    private void cmd(FakeMod who, String sub, String... args) {
        ps.command(who.id, false, sub, List.of(args));
    }

    private void all(FakeMod who, String sub, String... args) {
        ps.command(who.id, true, sub, List.of(args));
    }

    @Test
    void HELLOの前には何も送らず_HELLOだけでは何も流れない() {
        cmd(alice, "play", A_URL);
        assertTrue(alice.poll().isEmpty());
        alice.hello(Messages.PROTOCOL_VERSION);
        // HELLO の時点で個人再生が進んでいたので、その状態が届く(ダミーの PLAY は無い)
        List<Messages> got = alice.poll();
        assertEquals(1, got.size());
        assertEquals("曲A", assertInstanceOf(Messages.Play.class, got.get(0)).title());
        assertTrue(bob.poll().isEmpty());
    }

    @Test
    void 再生中のHELLOには今のoriginMsでPLAYを送る() {
        alice.hello(Messages.PROTOCOL_VERSION);
        cmd(alice, "play", A_URL);
        Messages.Play first = assertInstanceOf(Messages.Play.class, alice.poll().get(0));
        assertEquals(clock.nowMs() + 500, first.originMs()); // 手順3: 今 + start-lead-ms
        assertEquals(Messages.Slot.PERSONAL, first.slot().slotType());

        clock.advance(10_000);
        alice.hello(Messages.PROTOCOL_VERSION); // 再接続の想定
        Messages.Play again = assertInstanceOf(Messages.Play.class, alice.poll().get(0));
        assertEquals(first.originMs(), again.originMs());
        assertEquals(first.trackId(), again.trackId());
    }

    @Test
    void 一時停止中のHELLOにはPLAYのあとにPAUSEを送る() {
        alice.hello(Messages.PROTOCOL_VERSION);
        cmd(alice, "play", A_URL);
        clock.advance(500 + 7_000);
        cmd(alice, "pause");
        alice.poll();

        clock.advance(20_000);
        alice.hello(Messages.PROTOCOL_VERSION);
        List<Messages> got = alice.poll();
        assertEquals(2, got.size());
        Messages.Play play = assertInstanceOf(Messages.Play.class, got.get(0));
        Messages.Pause pause = assertInstanceOf(Messages.Pause.class, got.get(1));
        assertEquals(7_000, pause.positionMs());
        assertEquals(clock.nowMs() + 500 - 7_000, play.originMs());
    }

    @Test
    void protocolVersionが違うmodには何も送らない() {
        FakeMod old = new FakeMod("old");
        cmd(old, "play", A_URL);
        old.hello(Messages.PROTOCOL_VERSION + 1);
        old.toServer(new Messages.TimeReq(1L));
        assertTrue(old.poll().isEmpty());
        assertFalse(protocol.hasMod(old.id));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("old"));
    }

    @Test
    void TIME_REQにはclientNanosをそのまま返し_その時点のサーバー時刻を入れる() {
        alice.hello(Messages.PROTOCOL_VERSION);
        alice.poll();
        clock.advance(1234);
        alice.toServer(new Messages.TimeReq(987_654_321L));
        Messages.Time time = assertInstanceOf(Messages.Time.class, alice.poll().get(0));
        assertEquals(987_654_321L, time.clientNanos());
        assertEquals(clock.nowMs(), time.serverNowMs());
    }

    @Test
    void HELLO前のTIME_REQには返さない() {
        alice.toServer(new Messages.TimeReq(1L));
        assertTrue(alice.poll().isEmpty());
    }

    @Test
    void 共有を聞くと個人再生にPAUSE_やめるとRESUME_共有はSHAREDスロットで流れる() {
        alice.hello(Messages.PROTOCOL_VERSION);
        bob.hello(Messages.PROTOCOL_VERSION);
        cmd(alice, "play", A_URL);
        clock.advance(500 + 5_000);
        alice.poll();
        bob.poll();

        cmd(bob, "play", B_URL);
        clock.advance(500 + 3_000);
        bob.poll();
        all(bob, "");
        assertTrue(bob.poll().stream().anyMatch(m -> m instanceof Messages.Play p
                && p.slot().slotType() == Messages.Slot.SHARED));

        // alice が共有を聞き始める
        cmd(alice, "listen");
        List<Messages> got = alice.poll();
        Messages.Pause personalPause = assertInstanceOf(Messages.Pause.class, got.get(0));
        assertEquals(Messages.Slot.PERSONAL, personalPause.slot().slotType());
        assertEquals(8_500, personalPause.positionMs()); // 5秒 + bob が待った 3.5秒
        Messages.Play shared = assertInstanceOf(Messages.Play.class, got.get(1));
        assertEquals(Messages.Slot.SHARED, shared.slot().slotType());
        assertEquals("曲B", shared.title());

        // やめる
        clock.advance(2_000);
        cmd(alice, "leave");
        got = alice.poll();
        assertEquals(Messages.Slot.SHARED, assertInstanceOf(Messages.Stop.class, got.get(0)).slot().slotType());
        Messages.Resume resume = assertInstanceOf(Messages.Resume.class, got.get(1));
        assertEquals(Messages.Slot.PERSONAL, resume.slot().slotType());
        assertEquals(clock.nowMs() + 500 - 8_500, resume.originMs()); // 再開時刻 + lead - 止まっていた位置
    }

    @Test
    void SYNCは設定の間隔で各スロットのoriginMsを再送する() {
        alice.hello(Messages.PROTOCOL_VERSION);
        cmd(alice, "play", A_URL);
        long origin = assertInstanceOf(Messages.Play.class, alice.poll().get(0)).originMs();
        clock.advance(29_000);
        ps.tick();
        assertTrue(alice.poll().isEmpty());
        clock.advance(1_500);
        ps.tick();
        Messages.Sync sync = assertInstanceOf(Messages.Sync.class, alice.poll().get(0));
        assertEquals(origin, sync.originMs());
        assertEquals(Messages.Slot.PERSONAL, sync.slot().slotType());
    }

    @Test
    void 退出して再接続しても記録と送信が壊れない() {
        alice.hello(Messages.PROTOCOL_VERSION);
        cmd(alice, "play", A_URL);
        alice.poll();

        protocol.remove(alice.id);
        host.mods.remove(alice.id);
        ps.onQuit(alice.id);
        ps.tick();
        assertTrue(alice.poll().isEmpty());

        alice.hello(Messages.PROTOCOL_VERSION); // 再接続: 前の再生は残っていない
        assertTrue(alice.poll().isEmpty());
        cmd(alice, "play", B_URL);
        assertEquals("曲B", assertInstanceOf(Messages.Play.class, alice.poll().get(0)).title());
    }

    @Test
    void mod側のERRORは曲を飛ばす() {
        resolver.results.put("https://example.com/list", TrackResolver.Result.playlist(
                List.of(song("l1", 10_000), song("l2", 10_000)), false));
        alice.hello(Messages.PROTOCOL_VERSION);
        cmd(alice, "playlist", "https://example.com/list");
        Messages.Play first = assertInstanceOf(Messages.Play.class, alice.poll().get(0));
        alice.toServer(new Messages.Error(Messages.Slot.PERSONAL_SLOT, first.trackId(),
                Messages.Error.FETCH_FAILED, "x"));
        assertEquals("l2", assertInstanceOf(Messages.Play.class, alice.poll().get(0)).title());
    }
}
