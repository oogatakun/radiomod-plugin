package com.oogata.radiomod.audio;

import com.oogata.radiomod.net.Messages;

/** 再生の出来事の通知。どのスレッドから呼ばれるか分からないので、受け側で必要ならスレッドを切り替える。 */
public interface PlaybackListener {

    /** 音が鳴り始めた。 */
    default void onStarted(Messages.Slot slot) {
    }

    /** 曲を最後まで鳴らし終えた。 */
    default void onFinished(Messages.Slot slot) {
    }

    /** 取得や再生に失敗した。そのスロットは無音になっている。 */
    default void onError(Messages.Slot slot, AudioFailure failure) {
    }
}
