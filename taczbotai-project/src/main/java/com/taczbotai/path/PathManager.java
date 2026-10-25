package com.taczbotai.path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taczbotai.TaczBotAi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 路径存储。
 *
 * 需求要求路径落在 {@code <存档文件夹>/taczbotaidata/} 下，且不跨存档串用。
 * Forge 的 {@code SavedData} 固定写在 {@code <存档>/data/} 里并且是 NBT，无法满足，
 * 因此这里改为自定义 JSON 文件读写：每个存档一个目录，互不干扰。
 *
 * 节点格式：
 * { "name":"CT", "dimension":"...", "mode":"cs",
 *   "points":[ {"x":1,"y":2,"z":3}, {"x":4,"y":5,"z":6,"stop":true,"stay":90} ] }
 * 旧文件（无 mode/stop/stay）兼容：默认 cs + 移动节点。
 */
public final class PathManager {
    public static final String FOLDER = "taczbotaidata";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 存档根路径 -> (路径名 -> 路径)。 */
    private static final Map<String, Map<String, BotPath>> CACHE = new ConcurrentHashMap<>();

    private PathManager() {
    }

    public static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FOLDER);
    }

    private static String key(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().toString();
    }

    private static Path fileFor(MinecraftServer server, String name) {
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return folder(server).resolve(safe + ".json");
    }

    public static Map<String, BotPath> all(ServerLevel level) {
        String cacheKey = key(level);
        Map<String, BotPath> cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        final Map<String, BotPath> loaded = new LinkedHashMap<>();
        Path dir = folder(level.getServer());
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                    BotPath path = read(p);
                    if (path != null) {
                        loaded.put(path.name(), path);
                    }
                });
            } catch (IOException e) {
                TaczBotAi.LOGGER.warn("[taczbotai] cannot list path folder: {}", e.toString());
            }
        }
        CACHE.put(cacheKey, loaded);
        return loaded;
    }

    public static List<PathPoint> getPoints(Level level, String name) {
        if (!(level instanceof ServerLevel serverLevel) || name == null || name.isEmpty()) {
            return List.of();
        }
        BotPath path = all(serverLevel).get(name);
        return path == null ? List.of() : path.points();
    }

    /** 路径录制时选择的模式（cs / bf），找不到返回 cs。 */
    public static String modeOf(Level level, String name) {
        if (!(level instanceof ServerLevel serverLevel) || name == null || name.isEmpty()) {
            return "cs";
        }
        BotPath path = all(serverLevel).get(name);
        return path == null || path.mode() == null || path.mode().isBlank() ? "cs" : path.mode();
    }

    public static boolean save(ServerLevel level, BotPath path) {
        Path file = fileFor(level.getServer(), path.name());
        try {
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("name", path.name());
            json.addProperty("dimension", path.dimension());
            json.addProperty("mode", path.mode() == null ? "cs" : path.mode());
            JsonArray points = new JsonArray();
            for (PathPoint point : path.points()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("x", round(point.pos().x));
                obj.addProperty("y", round(point.pos().y));
                obj.addProperty("z", round(point.pos().z));
                if (point.stop()) {
                    obj.addProperty("stop", true);
                    obj.addProperty("stay", point.staySeconds());
                }
                points.add(obj);
            }
            json.add("points", points);
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
            all(level).put(path.name(), path);
            return true;
        } catch (IOException e) {
            TaczBotAi.LOGGER.error("[taczbotai] failed to save path {}: {}", path.name(), e.toString());
            return false;
        }
    }

    public static boolean delete(ServerLevel level, String name) {
        Path file = fileFor(level.getServer(), name);
        boolean removed = all(level).remove(name) != null;
        try {
            removed |= Files.deleteIfExists(file);
        } catch (IOException e) {
            TaczBotAi.LOGGER.warn("[taczbotai] failed to delete path {}: {}", name, e.toString());
        }
        return removed;
    }

    public static void invalidate(ServerLevel level) {
        CACHE.remove(key(level));
    }

    public static void invalidateAll() {
        CACHE.clear();
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static BotPath read(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject json = root.getAsJsonObject();
            String name = json.has("name") ? json.get("name").getAsString()
                    : file.getFileName().toString().replace(".json", "");
            String dimension = json.has("dimension") ? json.get("dimension").getAsString() : "minecraft:overworld";
            String mode = json.has("mode") ? json.get("mode").getAsString() : "cs";
            List<PathPoint> points = new ArrayList<>();
            if (json.has("points") && json.get("points").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("points")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = element.getAsJsonObject();
                    Vec3 pos = new Vec3(obj.get("x").getAsDouble(), obj.get("y").getAsDouble(), obj.get("z").getAsDouble());
                    boolean stop = obj.has("stop") && obj.get("stop").getAsBoolean();
                    int stay = obj.has("stay") ? obj.get("stay").getAsInt() : 0;
                    points.add(new PathPoint(pos, stop, stay));
                }
            }
            return new BotPath(name, dimension, mode, List.copyOf(points));
        } catch (Exception e) {
            TaczBotAi.LOGGER.warn("[taczbotai] broken path file {}: {}", file, e.toString());
            return null;
        }
    }

    public static Collection<BotPath> paths(ServerLevel level) {
        return all(level).values();
    }
}
