package com.minigame.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Map;
import java.util.UUID;

/**
 * 计分板工具方法
 */
public class ScoreboardHelper {

    private static final String OBJECTIVE_NAME = "minigame_data";

    /**
     * 创建计分板
     */
    public static void createScoreboard(MinecraftServer server, String title, Map<UUID, Integer> playerLives) {
        Scoreboard scoreboard = server.getScoreboard();

        // 移除已存在的目标
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            scoreboard.removeObjective(existing);
        }

        // 创建新目标
        Objective objective = scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal(title),
                ObjectiveCriteria.RenderType.INTEGER
        );

        scoreboard.setDisplayObjective(1, objective);

        // 设置玩家分数
        for (Map.Entry<UUID, Integer> entry : playerLives.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(entry.getValue());
            }
        }
    }

    /**
     * 更新计分板
     */
    public static void updateScoreboard(MinecraftServer server, Map<UUID, Integer> playerLives) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        for (Map.Entry<UUID, Integer> entry : playerLives.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(entry.getValue());
            }
        }
    }

    /**
     * 移除计分板
     */
    public static void removeScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }
}
