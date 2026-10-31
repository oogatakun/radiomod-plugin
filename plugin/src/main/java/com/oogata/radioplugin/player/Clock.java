package com.oogata.radioplugin.player;

/** サーバー時刻（ミリ秒）の供給元。テストでは差し替えて時間を進める。 */
@FunctionalInterface
public interface Clock {

    Clock SYSTEM = System::currentTimeMillis;

    long nowMs();
}
