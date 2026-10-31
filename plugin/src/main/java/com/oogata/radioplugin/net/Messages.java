package com.oogata.radioplugin.net;

import java.util.List;
import java.util.UUID;

/** docs/protocol.md のメッセージ定義。 */
public sealed interface Messages {

    int PROTOCOL_VERSION = 2;
    String CHANNEL = "radiomod:main";

    int type();

    record Slot(byte slotType, UUID radioId) {
        public static final byte PERSONAL = 0;
        public static final byte SHARED = 1;
        public static final byte RADIO = 2;

        public static final Slot PERSONAL_SLOT = new Slot(PERSONAL, null);
        public static final Slot SHARED_SLOT = new Slot(SHARED, null);

        public static Slot radio(UUID id) {
            return new Slot(RADIO, id);
        }

        public Slot {
            if ((slotType == RADIO) != (radioId != null)) {
                throw new IllegalArgumentException("radioId は slotType が 2 のときだけ指定する");
            }
        }
    }

    record RadioTarget(String dimension, double x, double y, double z, float range) {
    }

    // サーバー → mod

    record Play(Slot slot, long trackId, String url, String title, long durationMs, long originMs,
                RadioTarget radio) implements Messages {
        public static final int TYPE = 0x01;

        public Play {
            if ((slot.slotType() == Slot.RADIO) != (radio != null)) {
                throw new IllegalArgumentException("radio はラジオスロットのときだけ指定する");
            }
        }

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Pause(Slot slot, long positionMs) implements Messages {
        public static final int TYPE = 0x02;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Resume(Slot slot, long originMs) implements Messages {
        public static final int TYPE = 0x03;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Stop(Slot slot) implements Messages {
        public static final int TYPE = 0x04;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Sync(Slot slot, long originMs) implements Messages {
        public static final int TYPE = 0x05;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Move(Slot slot, String dimension, double x, double y, double z) implements Messages {
        public static final int TYPE = 0x06;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Time(long clientNanos, long serverNowMs) implements Messages {
        public static final int TYPE = 0x07;

        @Override
        public int type() {
            return TYPE;
        }
    }

    /** プレーヤー画面に出す、キューの1曲ぶん。 */
    record QueueEntry(String title, long durationMs) {
    }

    /** キューの状態。REFRESH の返事と、OPEN のときに送る。label はラジオなら名前、それ以外は空。 */
    record Queue(Slot slot, String label, byte state, byte loop, int index, List<QueueEntry> entries)
            implements Messages {
        public static final int TYPE = 0x08;
        public static final byte STOPPED = 0;
        public static final byte PLAYING = 1;
        public static final byte PAUSED = 2;
        public static final byte LOOP_NONE = 0;
        public static final byte LOOP_SINGLE = 1;
        public static final byte LOOP_PLAYLIST = 2;
        /** 1つのメッセージに入れる曲数の上限(これを超えたぶんは送らない)。 */
        public static final int MAX_ENTRIES = 200;

        @Override
        public int type() {
            return TYPE;
        }
    }

    /** このスロットのプレーヤー画面を開く(/ps popup など)。 */
    record Open(Slot slot) implements Messages {
        public static final int TYPE = 0x09;

        @Override
        public int type() {
            return TYPE;
        }
    }

    // mod → サーバー

    record Hello(int protocolVersion, String modVersion) implements Messages {
        public static final int TYPE = 0x80;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record Error(Slot slot, long trackId, byte reason, String detail) implements Messages {
        public static final int TYPE = 0x81;
        public static final byte FETCH_FAILED = 0;
        public static final byte UNSUPPORTED_URL = 1;
        public static final byte DECODE_FAILED = 2;
        public static final byte OTHER = 3;

        @Override
        public int type() {
            return TYPE;
        }
    }

    record TimeReq(long clientNanos) implements Messages {
        public static final int TYPE = 0x82;

        @Override
        public int type() {
            return TYPE;
        }
    }

    /** プレーヤー画面の操作。arg は JUMP のときの曲の番号(0 始まり)で、それ以外は 0。 */
    record Control(Slot slot, byte action, int arg) implements Messages {
        public static final int TYPE = 0x83;
        /** 一時停止と再開の切り替え。 */
        public static final byte PAUSE = 0;
        public static final byte SKIP = 1;
        public static final byte PREVIOUS = 2;
        /** 止めて、キューを空にする(共有は、自分が聞くのをやめるだけ)。 */
        public static final byte STOP = 3;
        public static final byte JUMP = 4;
        /** なし → 単曲 → 全体 → なし の順に切り替える。 */
        public static final byte LOOP = 5;
        /** キューの状態(QUEUE)を返してもらう。画面を開いている間、約1秒ごとに送る。 */
        public static final byte REFRESH = 6;

        @Override
        public int type() {
            return TYPE;
        }
    }
}
