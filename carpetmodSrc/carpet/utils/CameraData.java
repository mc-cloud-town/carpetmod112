package carpet.utils;

import com.google.common.base.Charsets;
import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CameraData {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final JsonParser JSON_PARSER = new JsonParser();
    private static long lastModifiedTime = 0L;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3d.class, new Vec3dAdapter())
            .setPrettyPrinting()
            .create();

    private static final Map<UUID, CameraData> cameraDataMap = new HashMap<>();
    private static final Object SAVE_LOCK = new Object();

    private final Vec3d storedPos;
    private final Vec3d storedMotion;
    private final float storeYaw;
    private final float storePitch;
    private final int storedDim;
    private final List<EffectData> effects;

    public CameraData(EntityPlayerMP player) {
        this.storedPos = player.getPositionVector();
        this.storedMotion = new Vec3d(player.motionX, player.motionY, player.motionZ);
        this.storeYaw = player.rotationYaw;
        this.storePitch = player.rotationPitch;
        this.storedDim = player.dimension;

        this.effects = player.getActivePotionEffects()
                .stream()
                .map(EffectData::new)
                .collect(Collectors.toList());
    }

    // Gson Vec3d Adapter
    private static class Vec3dAdapter implements JsonSerializer<Vec3d>, JsonDeserializer<Vec3d> {
        @Override
        public JsonElement serialize(Vec3d src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.x);
            obj.addProperty("y", src.y);
            obj.addProperty("z", src.z);
            return obj;
        }

        @Override
        public Vec3d deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            double x = obj.get("x").getAsDouble();
            double y = obj.get("y").getAsDouble();
            double z = obj.get("z").getAsDouble();
            return new Vec3d(x, y, z);
        }
    }

    private static File getSaveFile(MinecraftServer server) {
        return new File(server.getFolderName(), "camData.json");
    }

    public static void load(MinecraftServer server) {
        File file = getSaveFile(server);
        if (!file.isFile()) return;

        long fileModified = file.lastModified();
        if (lastModifiedTime == fileModified) {
            return;
        }

        try {
            String json = FileUtils.readFileToString(file, Charsets.UTF_8);
            JsonObject root = JSON_PARSER.parse(json).getAsJsonObject();

            cameraDataMap.clear();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                UUID playerUUID = UUID.fromString(entry.getKey());
                CameraData data = GSON.fromJson(entry.getValue(), CameraData.class);
                cameraDataMap.put(playerUUID, data);
            }

            lastModifiedTime = fileModified;
            LOGGER.info("Loaded camera data for {} players.", cameraDataMap.size());
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to load camera data", e);
        }
    }

    public static void save(MinecraftServer server) {
        File file = getSaveFile(server);
        synchronized (SAVE_LOCK) {
            try {
                JsonObject root = new JsonObject();
                for (Map.Entry<UUID, CameraData> entry : cameraDataMap.entrySet()) {
                    root.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
                }
                FileUtils.writeStringToFile(file, GSON.toJson(root), Charsets.UTF_8);
                LOGGER.info("Saved camera data for {} players.", cameraDataMap.size());
            } catch (IOException e) {
                LOGGER.error("Failed to save camera data", e);
            }
        }
    }

    public static void storeForPlayer(EntityPlayerMP player) {
        CameraData data = new CameraData(player);
        cameraDataMap.put(player.getUniqueID(), data);
        save(player.getServer());
    }

    public static void removeForPlayer(EntityPlayerMP player) {
        cameraDataMap.remove(player.getUniqueID());
        save(player.getServer());
    }

    @Nullable
    public static CameraData getForPlayer(EntityPlayerMP player) {
        load(player.getServer());
        return cameraDataMap.get(player.getUniqueID());
    }

    public List<EffectData> getEffects() {
        return effects;
    }

    public Vec3d getStoredPos() {
        return storedPos;
    }

    public Vec3d getStoredMotion() {
        return storedMotion;
    }

    public float getStoreYaw() {
        return storeYaw;
    }

    public float getStorePitch() {
        return storePitch;
    }

    public int getStoredDim() {
        return storedDim;
    }

    public void applyEffectsToPlayer(EntityPlayerMP player) {
        player.clearActivePotions();
        for (EffectData effectData : effects) {
            Potion potion = Potion.getPotionFromResourceLocation(effectData.getName());
            if (potion == null) {
                try {
                    int id = Integer.parseInt(effectData.getName());
                    potion = Potion.getPotionById(id);
                } catch (NumberFormatException ignored) {}
            }

            if (potion != null) {
                PotionEffect effect = new PotionEffect(potion, effectData.getDuration(), effectData.getAmplifier());
                player.addPotionEffect(effect);
            } else {
                LOGGER.warn("Unknown potion effect '{}', skipping", effectData.getName());
            }
        }
    }

    public static class EffectData {
        private String name;
        private int amplifier;
        private int duration;

        // Gson
        public EffectData() {}

        public EffectData(PotionEffect effect) {
            this.name = effect.getPotion().getName();
            this.amplifier = effect.getAmplifier();
            this.duration = effect.getDuration();
        }

        public String getName() {
            return name;
        }

        public int getAmplifier() {
            return amplifier;
        }

        public int getDuration() {
            return duration;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setAmplifier(int amplifier) {
            this.amplifier = amplifier;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }
}
