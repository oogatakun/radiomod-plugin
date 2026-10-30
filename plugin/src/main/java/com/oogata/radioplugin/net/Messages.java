package com.oogata.radioplugin.net;

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
}
