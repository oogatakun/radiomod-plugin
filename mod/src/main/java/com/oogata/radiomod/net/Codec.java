package com.oogata.radiomod.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** docs/protocol.md のバイト形式（big-endian）のエンコードとデコード。 */
public final class Codec {

    public static final int MAX_URL_BYTES = 2048;
    public static final int MAX_TEXT_BYTES = 256;

    /** 未知の種別・長さ不足など、読めないメッセージ。呼び出し側は警告を出して無視する。 */
    public static final class DecodeException extends Exception {
        public DecodeException(String message) {
            super(message);
        }

        public DecodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private Codec() {
    }

    public static byte[] encode(Messages message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeByte(message.type());
            if (message instanceof Messages.Play m) {
                writeSlot(out, m.slot());
                out.writeLong(m.trackId());
                writeString(out, m.url(), MAX_URL_BYTES, false);
                writeString(out, m.title(), MAX_TEXT_BYTES, true);
                out.writeLong(m.durationMs());
                out.writeLong(m.originMs());
                if (m.slot().slotType() == Messages.Slot.RADIO) {
                    Messages.RadioTarget r = m.radio();
                    writeString(out, r.dimension(), MAX_TEXT_BYTES, false);
                    out.writeDouble(r.x());
                    out.writeDouble(r.y());
                    out.writeDouble(r.z());
                    out.writeFloat(r.range());
                }
            } else if (message instanceof Messages.Pause m) {
                writeSlot(out, m.slot());
                out.writeLong(m.positionMs());
            } else if (message instanceof Messages.Resume m) {
                writeSlot(out, m.slot());
                out.writeLong(m.originMs());
            } else if (message instanceof Messages.Stop m) {
                writeSlot(out, m.slot());
            } else if (message instanceof Messages.Sync m) {
                writeSlot(out, m.slot());
                out.writeLong(m.originMs());
            } else if (message instanceof Messages.Move m) {
                writeSlot(out, m.slot());
                writeString(out, m.dimension(), MAX_TEXT_BYTES, false);
                out.writeDouble(m.x());
                out.writeDouble(m.y());
                out.writeDouble(m.z());
            } else if (message instanceof Messages.Time m) {
                out.writeLong(m.clientNanos());
                out.writeLong(m.serverNowMs());
            } else if (message instanceof Messages.Hello m) {
                out.writeInt(m.protocolVersion());
                writeString(out, m.modVersion(), MAX_TEXT_BYTES, true);
            } else if (message instanceof Messages.Error m) {
                writeSlot(out, m.slot());
                out.writeLong(m.trackId());
                out.writeByte(m.reason());
                writeString(out, m.detail(), MAX_TEXT_BYTES, true);
            } else if (message instanceof Messages.TimeReq m) {
                out.writeLong(m.clientNanos());
            } else if (message instanceof Messages.Queue m) {
                writeSlot(out, m.slot());
                writeString(out, m.label(), MAX_TEXT_BYTES, true);
                out.writeByte(m.state());
                out.writeByte(m.loop());
                out.writeInt(m.index());
                int count = Math.min(m.entries().size(), Messages.Queue.MAX_ENTRIES);
                out.writeInt(count);
                for (int i = 0; i < count; i++) {
                    writeString(out, m.entries().get(i).title(), MAX_TEXT_BYTES, true);
                    out.writeLong(m.entries().get(i).durationMs());
                }
            } else if (message instanceof Messages.Open m) {
                writeSlot(out, m.slot());
            } else if (message instanceof Messages.Control m) {
                writeSlot(out, m.slot());
                out.writeByte(m.action());
                out.writeInt(m.arg());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // ByteArrayOutputStream では起きない
        }
        return bytes.toByteArray();
    }

    public static Messages decode(byte[] data) throws DecodeException {
        if (data.length == 0) {
            throw new DecodeException("空のメッセージ");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        try {
            int type = in.readUnsignedByte();
            return switch (type) {
                case Messages.Play.TYPE -> {
                    Messages.Slot slot = readSlot(in);
                    long trackId = in.readLong();
                    String url = readString(in);
                    String title = readString(in);
                    long duration = in.readLong();
                    long origin = in.readLong();
                    Messages.RadioTarget radio = null;
                    if (slot.slotType() == Messages.Slot.RADIO) {
                        radio = new Messages.RadioTarget(readString(in), in.readDouble(), in.readDouble(),
                                in.readDouble(), in.readFloat());
                    }
                    yield new Messages.Play(slot, trackId, url, title, duration, origin, radio);
                }
                case Messages.Pause.TYPE -> new Messages.Pause(readSlot(in), in.readLong());
                case Messages.Resume.TYPE -> new Messages.Resume(readSlot(in), in.readLong());
                case Messages.Stop.TYPE -> new Messages.Stop(readSlot(in));
                case Messages.Sync.TYPE -> new Messages.Sync(readSlot(in), in.readLong());
                case Messages.Move.TYPE -> {
                    Messages.Slot slot = readSlot(in);
                    yield new Messages.Move(slot, readString(in), in.readDouble(), in.readDouble(),
                            in.readDouble());
                }
                case Messages.Time.TYPE -> new Messages.Time(in.readLong(), in.readLong());
                case Messages.Hello.TYPE -> new Messages.Hello(in.readInt(), readString(in));
                case Messages.Error.TYPE -> {
                    Messages.Slot slot = readSlot(in);
                    long trackId = in.readLong();
                    byte reason = in.readByte();
                    yield new Messages.Error(slot, trackId, reason, readString(in));
                }
                case Messages.TimeReq.TYPE -> new Messages.TimeReq(in.readLong());
                case Messages.Queue.TYPE -> {
                    Messages.Slot slot = readSlot(in);
                    String label = readString(in);
                    byte state = in.readByte();
                    byte loop = in.readByte();
                    int index = in.readInt();
                    int count = in.readInt();
                    if (count < 0 || count > Messages.Queue.MAX_ENTRIES) {
                        throw new DecodeException("キューの曲数が不正: " + count);
                    }
                    List<Messages.QueueEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new Messages.QueueEntry(readString(in), in.readLong()));
                    }
                    yield new Messages.Queue(slot, label, state, loop, index, entries);
                }
                case Messages.Open.TYPE -> new Messages.Open(readSlot(in));
                case Messages.Control.TYPE -> new Messages.Control(readSlot(in), in.readByte(), in.readInt());
                default -> throw new DecodeException("未知の種別: 0x" + Integer.toHexString(type));
            };
        } catch (EOFException e) {
            throw new DecodeException("長さが足りない", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new DecodeException("読めないメッセージ", e);
        }
    }

    private static void writeSlot(DataOutputStream out, Messages.Slot slot) throws IOException {
        out.writeByte(slot.slotType());
        if (slot.slotType() == Messages.Slot.RADIO) {
            out.writeLong(slot.radioId().getMostSignificantBits());
            out.writeLong(slot.radioId().getLeastSignificantBits());
        }
    }

    private static Messages.Slot readSlot(DataInputStream in) throws IOException {
        byte type = in.readByte();
        if (type < Messages.Slot.PERSONAL || type > Messages.Slot.RADIO) {
            throw new IOException("未知のスロット種別: " + type);
        }
        if (type == Messages.Slot.RADIO) {
            return Messages.Slot.radio(new UUID(in.readLong(), in.readLong()));
        }
        return new Messages.Slot(type, null);
    }

    /** truncate が true なら上限で切り詰め、false で超えたら送らない（例外）。 */
    private static void writeString(DataOutputStream out, String value, int maxBytes, boolean truncate)
            throws IOException {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > maxBytes) {
            if (!truncate) {
                throw new IllegalArgumentException("文字列が上限(" + maxBytes + "バイト)を超えている");
            }
            int len = maxBytes;
            while (len > 0 && (utf8[len] & 0xC0) == 0x80) {
                len--; // UTF-8 の文字の途中で切らない
            }
            byte[] cut = new byte[len];
            System.arraycopy(utf8, 0, cut, 0, len);
            utf8 = cut;
        }
        out.writeShort(utf8.length);
        out.write(utf8);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] utf8 = new byte[len];
        in.readFully(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }
}
