package com.elyhealpot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ElyhealpotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("elyhealpot.json").toFile();

    private static ElyhealpotConfig INSTANCE;

    public enum ServerMode {
        FUNTIME,
        SPACETIMES
    }

    public enum DecimalDisplay {
        LOW,
        ALWAYS,
        NONE
    }

    public ServerMode mode = ServerMode.FUNTIME;
    public float funtimeCooldown = 20.0f;
    public float spacetimesCooldown = 18.4f;
    public DecimalDisplay decimalDisplay = DecimalDisplay.LOW;
    public boolean showVanillaCooldownNumbers = true;

    public static ElyhealpotConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public float getCooldownSeconds() {
        return mode == ServerMode.SPACETIMES ? spacetimesCooldown : funtimeCooldown;
    }

    public void setMode(ServerMode newMode) {
        this.mode = newMode;
        save();
    }

    public static ElyhealpotConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ElyhealpotConfig config = GSON.fromJson(reader, ElyhealpotConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                ElyhealpotMod.LOGGER.error("Failed to load elyhealpot config", e);
            }
        }
        ElyhealpotConfig config = new ElyhealpotConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            ElyhealpotMod.LOGGER.error("Failed to save elyhealpot config", e);
        }
    }
}
