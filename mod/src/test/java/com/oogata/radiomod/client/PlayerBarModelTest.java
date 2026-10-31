package com.oogata.radiomod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radiomod.client.PlayerBarModel.Action;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.SlotRouter.NowPlaying;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerBarModelTest {

    private static NowPlaying np(Messages.Slot slot, long id, boolean paused, boolean audible) {
        return new NowPlaying(slot, id, "t" + id, 180_000, paused, 30_000, audible);
    }

    @Test
    void 鳴っていないスロットは出さない() {
        PlayerBarModel m = new PlayerBarModel();
        assertTrue(m.choose(List.of(np(Messages.Slot.PERSONAL_SLOT, 1, false, false))).isEmpty());
        assertTrue(m.choose(List.of()).isEmpty());
    }

    @Test
    void 止まっていないものを優先し_同じなら共有_個人_ラジオの順() {
        PlayerBarModel m = new PlayerBarModel();
        Messages.Slot radio = Messages.Slot.radio(UUID.randomUUID());
        List<NowPlaying> all = List.of(np(radio, 3, false, true), np(Messages.Slot.PERSONAL_SLOT, 1, false, true),
                np(Messages.Slot.SHARED_SLOT, 2, false, true));
        assertEquals(2, m.choose(all).get().trackId());
        // 共有が一時停止中なら、鳴っている個人を出す
        all = List.of(np(radio, 3, false, true), np(Messages.Slot.PERSONAL_SLOT, 1, false, true),
                np(Messages.Slot.SHARED_SLOT, 2, true, true));
        assertEquals(1, m.choose(all).get().trackId());
        // 全部止まっていたら、順番どおり
        all = List.of(np(radio, 3, true, true), np(Messages.Slot.PERSONAL_SLOT, 1, true, true));
        assertEquals(1, m.choose(all).get().trackId());
    }

    @Test
    void バツで隠した曲は出さず_曲が消えたら忘れる() {
        PlayerBarModel m = new PlayerBarModel();
        List<NowPlaying> all = List.of(np(Messages.Slot.PERSONAL_SLOT, 1, false, true));
        m.dismiss(1);
        assertTrue(m.choose(all).isEmpty());
        assertTrue(m.choose(List.of()).isEmpty()); // 曲が終わった(消えた)
        // 同じ trackId が再び現れることは通常ないが、忘れているので出る
        assertEquals(1, m.choose(all).get().trackId());
    }

    @Test
    void ボタンのコマンドは_個人と共有だけ() {
        assertEquals("ps pause", PlayerBarModel.command(Messages.Slot.PERSONAL_SLOT, Action.TOGGLE));
        assertEquals("ps skip", PlayerBarModel.command(Messages.Slot.PERSONAL_SLOT, Action.NEXT));
        assertEquals("ps previous", PlayerBarModel.command(Messages.Slot.PERSONAL_SLOT, Action.PREVIOUS));
        assertEquals("ps all pause", PlayerBarModel.command(Messages.Slot.SHARED_SLOT, Action.TOGGLE));
        assertEquals("ps all skip", PlayerBarModel.command(Messages.Slot.SHARED_SLOT, Action.NEXT));
        assertNull(PlayerBarModel.command(Messages.Slot.radio(UUID.randomUUID()), Action.TOGGLE));
    }

    @Test
    void 進行バーの割合と時間の表示() {
        assertEquals(0.5, PlayerBarModel.progress(new NowPlaying(Messages.Slot.PERSONAL_SLOT, 1, "t", 100_000, false,
                50_000, true)), 1e-9);
        assertEquals(-1, PlayerBarModel.progress(new NowPlaying(Messages.Slot.PERSONAL_SLOT, 1, "t", -1, false, -1, true)));
        assertEquals(1.0, PlayerBarModel.progress(new NowPlaying(Messages.Slot.PERSONAL_SLOT, 1, "t", 100, false,
                5_000, true)), 1e-9);
        assertEquals("3:05", PlayerBarModel.time(185_000));
        assertEquals("0:00", PlayerBarModel.time(-5));
    }
}
