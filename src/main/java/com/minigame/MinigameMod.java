package com.minigame;

import com.minigame.command.MinigameCommand;
import com.minigame.event.PlayerEventHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinigameMod implements ModInitializer {
    public static final String MOD_ID = "minigame";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MinigameCommand.register(dispatcher);
        });

        // Register player death handler (cancels death, triggers totem effect)
        PlayerEventHandler.register();

        // Register server tick event for game logic (regen lives)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.minigame.game.GameManager.INSTANCE.tick(server);
        });

        LOGGER.info("Minigame Mod initialized!");
    }
}
