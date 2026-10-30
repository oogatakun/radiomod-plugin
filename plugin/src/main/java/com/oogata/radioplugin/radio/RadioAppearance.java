package com.oogata.radioplugin.radio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * ラジオの見た目の生成と撤去（仕様書5章「見た目」）。見た目に関する処理はこのクラスにまとめる。
 *
 * <p>作り方は2通り。まず models/ のコマンドをコンソールから実行し、通らなければ summon の中身を
 * 読んで Bukkit の API でパーツを作る（radio-model-method で固定もできる）。どちらも、できたパーツを
 * 同じ手順で仕上げる: 乗り物から降ろして位置をそろえ、向きの回転をかけ、目印タグを外し、ラジオIDを持たせる。
 */
public final class RadioAppearance {

    /** 設置位置から、ラジオの全パーツの基準点（モデルの隅）までのずれ。 */
    private static final float ROOT_OFFSET = -0.5f;
    private static final Pattern PASSENGER_TAG = Pattern.compile("_p\\d+");

    private final JavaPlugin plugin;
    private final NamespacedKey idKey;

    private ModelFile cachedModel;
    private long cachedStamp = -1;
    private String cachedPath;

    public RadioAppearance(JavaPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "radio_id");
    }

    /** 作った結果。parts は名前表示を除くパーツ数。 */
    public record BuildResult(int parts, double height, String method) {
    }

    public BuildResult build(Radio r, String nowPlaying) {
        World world = Bukkit.getWorld(r.world());
        if (world == null) {
            throw new RadioException("ワールドが見つかりません: " + r.world());
        }
        Location pos = new Location(world, r.x(), r.y(), r.z());
        Location root = pos.clone().add(ROOT_OFFSET, 0, ROOT_OFFSET);

        String method = plugin.getConfig().getString("radio-model-method", "auto").toLowerCase(Locale.ROOT);
        String modelName = plugin.getConfig().getString("radio-model", "luxuryboombox");
        ModelFile model = loadModel(modelName);

        List<Display> displays = new ArrayList<>();
        String base;
        String used;
        List<PartSpec> specs;
        if (model != null) {
            base = model.tag();
            used = "command";
            if (!method.equals("api")) {
                displays = buildByCommands(model, world, pos, root);
            }
            if (displays.isEmpty() && !method.equals("command")) {
                if (!method.equals("api")) {
                    plugin.getLogger().warning("コマンドでラジオを作れなかったので、APIで作ります");
                }
                used = "api";
                specs = model.parts();
                displays = buildByApi(specs, root);
            }
        } else {
            base = SimpleModel.TAG;
            used = "simple";
            displays = buildByApi(SimpleModel.parts(), root);
        }
        if (displays.isEmpty()) {
            return new BuildResult(0, 0, used);
        }
        BuildResult result = finish(displays, r, root, base, used);
        spawnName(r, pos, result.height(), nowPlaying);
        return result;
    }

    private ModelFile loadModel(String name) {
        File file = new File(new File(plugin.getDataFolder(), "models"), name + ".mcfunction");
        if (!file.isFile()) {
            plugin.getLogger().warning("モデルのファイルがないので、簡易モデルを使います: " + file.getPath());
            return null;
        }
        String path = file.getPath();
        if (cachedModel != null && path.equals(cachedPath) && cachedStamp == file.lastModified()) {
            return cachedModel;
        }
        try {
            cachedModel = ModelFile.load(file.toPath());
            cachedPath = path;
            cachedStamp = file.lastModified();
            return cachedModel;
        } catch (IOException e) {
            plugin.getLogger().warning("モデルのファイルを読めないので、簡易モデルを使います: " + e.getMessage());
            return null;
        }
    }

    /** モデルのコマンドを、設置位置で上から順に実行する。出てきたパーツをタグで集めて返す。 */
    private List<Display> buildByCommands(ModelFile model, World world, Location pos, Location root) {
        // BDEngine のモデルは「基準点から ~-0.5 ~-0.5 ~-0.5」にルートを置く。底が設置位置に来るよう、半ブロック上で実行する
        String prefix = String.format(Locale.ROOT, "execute in %s positioned %.4f %.4f %.4f run ",
                world.getKey(), pos.getX(), pos.getY() - ROOT_OFFSET, pos.getZ());
        for (String command : model.commands()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prefix + command);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("モデルのコマンドを実行できませんでした: " + e);
                break;
            }
        }
        List<Display> found = collect(world, root, model.tag());
        if (found.isEmpty()) {
            plugin.getLogger().warning("モデルのコマンドを実行しましたが、パーツが出てきませんでした");
        }
        return found;
    }

    private List<Display> buildByApi(List<PartSpec> specs, Location root) {
        List<Display> out = new ArrayList<>();
        for (PartSpec spec : specs) {
            try {
                BlockDisplay d = root.getWorld().spawn(root, BlockDisplay.class, e -> {
                    e.setBlock(Bukkit.createBlockData(spec.block()));
                    e.setTransformationMatrix(new Matrix4f(spec.matrix()));
                    for (String t : spec.tags()) {
                        e.addScoreboardTag(t);
                    }
                });
                out.add(d);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("パーツを作れませんでした（" + spec.block() + "）: " + e);
            }
        }
        return out;
    }

    private static List<Display> collect(World world, Location root, String tag) {
        List<Display> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(root, 3, 4, 3)) {
            if (e instanceof Display d && d.getScoreboardTags().contains(tag)) {
                out.add(d);
            }
        }
        return out;
    }

    private static boolean isStructural(Display d, String base) {
        for (String t : d.getScoreboardTags()) {
            if (t.equals(base + "_root")) {
                return true;
            }
            if (t.startsWith(base) && PASSENGER_TAG.matcher(t.substring(base.length())).matches()) {
                return true;
            }
        }
        return false;
    }

    private BuildResult finish(List<Display> displays, Radio r, Location root, String base, String method) {
        List<Display> parts = new ArrayList<>();
        List<Display> structural = new ArrayList<>();
        for (Display d : displays) {
            (isStructural(d, base) ? structural : parts).add(d);
        }
        // 乗り物（ルートとまとまり用の入れ物）から降ろし、全パーツを同じ位置にそろえてから、入れ物を消す
        for (Display d : displays) {
            d.leaveVehicle();
        }
        for (Display d : parts) {
            d.teleport(root);
        }
        for (Display d : structural) {
            d.remove();
        }

        float viewRange = (float) plugin.getConfig().getDouble("radio-view-range", 0.5);
        double maxY = 0;
        for (Display d : parts) {
            maxY = Math.max(maxY, topOf(matrixOf(d)));
        }
        for (Display d : parts) {
            Matrix4f rotated = Orientation.rotateAboutCenter(matrixOf(d), r.rotation(), 0.5f, 0.5f);
            d.setTransformationMatrix(rotated);
            d.setViewRange(viewRange);
            for (String t : new ArrayList<>(d.getScoreboardTags())) {
                if (t.equals(base) || t.startsWith(base + "_")) {
                    d.removeScoreboardTag(t);
                }
            }
            d.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, r.id().toString());
        }
        return new BuildResult(parts.size(), maxY, method);
    }

    private static Matrix4f matrixOf(Display d) {
        Transformation t = d.getTransformation();
        return new Matrix4f()
                .translation(t.getTranslation())
                .rotate(t.getLeftRotation())
                .scale(t.getScale())
                .rotate(t.getRightRotation());
    }

    /** 単位立方体を m で変形したときの、いちばん高い Y。 */
    private static double topOf(Matrix4f m) {
        double max = Double.NEGATIVE_INFINITY;
        Vector3f v = new Vector3f();
        for (int i = 0; i < 8; i++) {
            m.transformPosition((i & 1), (i >> 1) & 1, (i >> 2) & 1, v);
            max = Math.max(max, v.y);
        }
        return max;
    }

    private void spawnName(Radio r, Location pos, double height, String nowPlaying) {
        Location at = pos.clone().add(0, height + 0.3, 0);
        float viewRange = (float) plugin.getConfig().getDouble("radio-view-range", 0.5);
        pos.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setText(nameText(r.name(), nowPlaying));
            t.setBillboard(Display.Billboard.CENTER);
            t.setViewRange(viewRange);
            t.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, r.id().toString());
        });
    }

    static String nameText(String name, String nowPlaying) {
        return nowPlaying == null || nowPlaying.isEmpty() ? name : name + "\n♪ " + nowPlaying;
    }

    // ---- 既にあるパーツの扱い ----

    /** このチャンクにある、ラジオのパーツ（名前表示を含む）。ラジオIDごとに見分けられる。 */
    public List<Display> partsIn(Chunk chunk) {
        List<Display> out = new ArrayList<>();
        if (!chunk.isLoaded() || !chunk.isEntitiesLoaded()) {
            return out;
        }
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Display d && idOf(d) != null) {
                out.add(d);
            }
        }
        return out;
    }

    public UUID idOf(Entity e) {
        String s = e.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** ラジオIDを持つパーツだけを消す（設置位置のチャンクにあるもの）。消せたら true。 */
    public boolean remove(Radio r) {
        World world = Bukkit.getWorld(r.world());
        if (world == null || !world.isChunkLoaded(r.chunkX(), r.chunkZ())) {
            return false;
        }
        Chunk chunk = world.getChunkAt(r.chunkX(), r.chunkZ());
        if (!chunk.isEntitiesLoaded()) {
            return false;
        }
        for (Display d : partsIn(chunk)) {
            if (r.id().equals(idOf(d))) {
                d.remove();
            }
        }
        return true;
    }

    /** 名前表示（と、あればランプ）を、再生中の曲名に合わせて更新する。 */
    public void updateNowPlaying(Radio r, String nowPlaying) {
        World world = Bukkit.getWorld(r.world());
        if (world == null || !world.isChunkLoaded(r.chunkX(), r.chunkZ())) {
            return;
        }
        for (Display d : partsIn(world.getChunkAt(r.chunkX(), r.chunkZ()))) {
            if (!r.id().equals(idOf(d))) {
                continue;
            }
            if (d instanceof TextDisplay t) {
                t.setText(nameText(r.name(), nowPlaying));
            } else if (d instanceof BlockDisplay b && b.getScoreboardTags().contains(SimpleModel.LAMP_TAG)) {
                b.setBlock(Bukkit.createBlockData("minecraft:redstone_lamp[lit=" + (nowPlaying != null) + "]"));
            }
        }
    }

    /** パーツ d が、ラジオ r の場所にあるか。移動前の古いパーツを見分けるのに使う。 */
    public static boolean isNear(Radio r, Entity d) {
        Location l = d.getLocation();
        if (l.getWorld() == null || !l.getWorld().getName().equals(r.world())) {
            return false;
        }
        double dy = l.getY() - r.y();
        return Math.abs(l.getX() - r.x()) <= 1.0
                && Math.abs(l.getZ() - r.z()) <= 1.0
                && dy >= -1.0 && dy <= r.height() + 1.5;
    }
}
