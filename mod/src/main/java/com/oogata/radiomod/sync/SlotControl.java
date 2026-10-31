package com.oogata.radiomod.sync;

import java.util.function.LongSupplier;

/** 1つのスロットの再生の操作。{@code SlotPlayer} が実装し、テストでは偽物に差し替える。 */
public interface SlotControl {

    /**
     * 新しい曲を鳴らす。前の曲はすぐ止める。desiredPositionMs は「いま鳴らすべき位置」を返す
     * （負のあいだは無音で待つ）。null なら位置合わせをせず、取得した時点から鳴らす。
     */
    void play(String url, LongSupplier desiredPositionMs);

    /** 一時停止。positionMs は、サーバーが伝えた止まった位置。 */
    void pause(long positionMs);

    /** 再開。鳴らすべき位置は、play で渡した desiredPositionMs から読む。 */
    void resume();

    void stop();
}
