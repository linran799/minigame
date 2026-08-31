package com.minigame.event;

import com.minigame.game.GameManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class PlayerEventHandler {

    public static void register() {
        // Death cancellation
        ServerLivingEntityEvents.ALLOW_DEATH.register(PlayerEventHandler::onAllowDeath);

        // Chat message handling for game setup flow
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, type) -> {
            if (sender != null) {
                String text = message.signedContent();
                // If player is in setup flow, intercept and hide their input
                if (GameManager.INSTANCE.isInSetupFlow()) {
                    GameManager.INSTANCE.onPlayerChat(sender, text);
                    return false; // Hide the message from chat
                }
            }
            return true;
        });

        // Player disconnect handling
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                GameManager.INSTANCE.onPlayerDisconnect(player);
            }
        });

        // Player join handling (for reconnects)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                GameManager.INSTANCE.onPlayerJoin(player);
            }
        });
    }

    private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float damageAmount) {
        if (entity instanceof ServerPlayer player) {
            boolean cancelled = GameManager.INSTANCE.onPlayerAboutToDie(player);
            return !cancelled;
        }
        return true;
    }
}
