package com.oogata.radioplugin.player;

import com.oogata.radioplugin.net.Messages;
import java.util.ArrayList;
import java.util.List;

/** プレーヤー画面に送るキューの状態(QUEUE)と、画面からの操作(CONTROL)をエンジンへつなぐ。Bukkit には依存しない。 */
public final class QueueView {

    private QueueView() {
    }

    /** エンジンの今の状態を QUEUE にする。label はラジオなら名前、それ以外は空。 */
    public static Messages.Queue of(Messages.Slot slot, String label, PlaybackEngine e) {
        byte state = switch (e.state()) {
            case STOPPED -> Messages.Queue.STOPPED;
            case PLAYING -> Messages.Queue.PLAYING;
            case PAUSED -> Messages.Queue.PAUSED;
        };
        byte loop = switch (e.loop()) {
            case NONE -> Messages.Queue.LOOP_NONE;
            case SINGLE -> Messages.Queue.LOOP_SINGLE;
            case PLAYLIST -> Messages.Queue.LOOP_PLAYLIST;
        };
        List<Messages.QueueEntry> entries = new ArrayList<>();
        for (Track t : e.queue()) {
            if (entries.size() >= Messages.Queue.MAX_ENTRIES) {
                break;
            }
            entries.add(new Messages.QueueEntry(t.title(), t.durationMs()));
        }
        int index = e.queue().isEmpty() ? -1 : e.index();
        return new Messages.Queue(slot, label == null ? "" : label, state, loop, index, entries);
    }

    /** 画面からの操作をエンジンに行う。REFRESH など、エンジンを変えない操作は何もしない。STOP は止めてキューを空にする。 */
    public static void apply(PlaybackEngine e, Messages.Control control) {
        switch (control.action()) {
            case Messages.Control.PAUSE -> e.pause();
            case Messages.Control.SKIP -> e.skip();
            case Messages.Control.PREVIOUS -> e.previous();
            case Messages.Control.STOP -> e.stop();
            case Messages.Control.JUMP -> e.jump(control.arg());
            case Messages.Control.LOOP -> e.cycleLoop();
            default -> {
            }
        }
    }
}
