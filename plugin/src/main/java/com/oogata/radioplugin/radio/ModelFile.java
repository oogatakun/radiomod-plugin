package com.oogata.radioplugin.radio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Matrix4f;

/**
 * BDEngine が書き出した create.mcfunction。コマンドを上から順に取り出す。
 * コマンドで実行できないときの代わりに、summon の中身（SNBT）からパーツの設計も取り出せる。
 */
public final class ModelFile {

    private static final Pattern ROOT_TAGS = Pattern.compile("Tags:\\[\"([^\"]+)\",\"\\1_root\"\\]");
    private static final Pattern SUMMON = Pattern.compile("^(?:execute .* run )?summon block_display ");

    private final String name;
    private final List<String> commands;
    private final String tag;

    private ModelFile(String name, List<String> commands, String tag) {
        this.name = name;
        this.commands = commands;
        this.tag = tag;
    }

    public static ModelFile load(Path path) throws IOException {
        String file = path.getFileName().toString();
        String name = file.endsWith(".mcfunction") ? file.substring(0, file.length() - ".mcfunction".length()) : file;
        return parse(name, Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    public static ModelFile parse(String name, List<String> lines) {
        List<String> commands = new ArrayList<>();
        String tag = name;
        for (String line : lines) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.startsWith("/")) {
                t = t.substring(1);
            }
            commands.add(t);
            Matcher m = ROOT_TAGS.matcher(t);
            if (m.find()) {
                tag = m.group(1);
            }
        }
        return new ModelFile(name, List.copyOf(commands), tag);
    }

    public String name() {
        return name;
    }

    /** 上から順に実行するコマンド（コメントと空行は除く）。 */
    public List<String> commands() {
        return commands;
    }

    /** モデルの目印タグ。全パーツが持つ（例: luxuryboombox）。 */
    public String tag() {
        return tag;
    }

    /** summon の中身から取り出したパーツ。向きの回転は含まない。 */
    public List<PartSpec> parts() {
        List<PartSpec> out = new ArrayList<>();
        for (String c : commands) {
            if (!SUMMON.matcher(c).find()) {
                continue;
            }
            int brace = c.indexOf('{');
            if (brace < 0) {
                continue;
            }
            collect(Snbt.parse(c.substring(brace)), out);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object nbt, List<PartSpec> out) {
        if (!(nbt instanceof Map)) {
            return;
        }
        Map<String, Object> m = (Map<String, Object>) nbt;
        Object state = m.get("block_state");
        if (state instanceof Map) {
            out.add(new PartSpec(blockString((Map<String, Object>) state), matrix(m.get("transformation")), tags(m.get("Tags"))));
        }
        Object passengers = m.get("Passengers");
        if (passengers instanceof List) {
            for (Object p : (List<Object>) passengers) {
                collect(p, out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String blockString(Map<String, Object> state) {
        StringBuilder sb = new StringBuilder(String.valueOf(state.get("Name")));
        Object props = state.get("Properties");
        if (props instanceof Map && !((Map<String, Object>) props).isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) props).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    /** 行優先の16個の数を、行列にする。 */
    @SuppressWarnings("unchecked")
    private static Matrix4f matrix(Object t) {
        if (!(t instanceof List) || ((List<Object>) t).size() != 16) {
            return new Matrix4f();
        }
        float[] a = new float[16];
        int i = 0;
        for (Object o : (List<Object>) t) {
            a[i++] = ((Number) o).floatValue();
        }
        // JOML は列優先で読むので、転置して行優先にそろえる
        return new Matrix4f().set(a).transpose();
    }

    @SuppressWarnings("unchecked")
    private static List<String> tags(Object t) {
        List<String> out = new ArrayList<>();
        if (t instanceof List) {
            for (Object o : (List<Object>) t) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }
}
