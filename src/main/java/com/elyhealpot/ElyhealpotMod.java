package com.elyhealpot;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElyhealpotMod implements ModInitializer {
    public static final String MOD_ID = "elyhealpot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing elyhealpot mod!");
        ElyhealpotConfig.getInstance();
    }
}
