package com.minigame.games;

import com.minigame.game.GameManager;
import com.minigame.utils.GameUtils;
import com.minigame.utils.ScoreboardHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * 死亡竞速游戏
 * 规则：每人有固定命数，最先死完的人获胜
 * 可配置命数和恢复时间
 */
public class DeathRaceGame extends GameBase {

    private int maxLives;
    private int regenIntervalSeconds;
    private final Map<UUID, Integer> playerLives;

    public DeathRaceGame(GameManager manager, MinecraftServer server, int maxLives, int regenIntervalSeconds) {
        super(manager, server);
        this.maxLives = maxLives;
        this.regenIntervalSeconds = regenIntervalSeconds;
        this.playerLives = new java.util.HashMap<>();
    }

    @Override
    public void start() {
        // 清空所有玩家物品栏并添加到游戏
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameUtils.clearInventory(player);
            playerLives.put(player.getUUID(), maxLives);
        }

        // 创建计分板
        ScoreboardHelper.createScoreboard(server, "剩余命数", playerLives);

        GameUtils.broadcast(server, "§6§l[死亡竞速] §e游戏开始！每人" + maxLives + "条命，最先死完的人获胜！");
    }

    @Override
    public void tick() {
        // 恢复生命（如果配置了恢复时间）
        if (regenIntervalSeconds > 0) {
            int regenTicks = regenIntervalSeconds * 20;
            if (manager.getTickCounter() % regenTicks == 0) {
                for (Map.Entry<UUID, Integer> entry : playerLives.entrySet()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        int lives = entry.getValue();
                        if (lives < maxLives) {
                            entry.setValue(lives + 1);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[死亡竞速] 恢复了1条命，当前" + (lives + 1) + "条命"));
                        }
                    }
                }
                ScoreboardHelper.updateScoreboard(server, playerLives);
            }
        }
    }

    @Override
    public boolean onPlayerAboutToDie(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!playerLives.containsKey(uuid)) return false;

        int lives = playerLives.get(uuid);
        if (lives <= 0) return false; // 已经死完了，正常死亡

        lives--;
        playerLives.put(uuid, lives);
        ScoreboardHelper.updateScoreboard(server, playerLives);

        if (lives <= 0) {
            // 命数耗尽，玩家获胜
            GameUtils.broadcast(server, "§6§l[死亡竞速] §a§l" + player.getName().getString() + " 命数耗尽，获胜了！");
            manager.setWinner(uuid);
            manager.finishGame(server);
            return true; // 取消这次死亡
        } else {
            // 还有命，触发不死图腾效果
            GameUtils.triggerTotemEffect(player);
            GameUtils.broadcast(server, "§c[死亡竞速] " + player.getName().getString() + " 死了！剩余" + lives + "条命");
            // 随机传送
            GameUtils.randomTeleport(player);
            return true; // 取消死亡
        }
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String message) {
        // 死亡竞速游戏进行中不需要特殊处理聊天
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        // 玩家断开连接，保留命数
    }

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (playerLives.containsKey(uuid)) {
            // 重新显示计分板
            var scoreboard = server.getScoreboard();
            var objective = scoreboard.getObjective("minigame_data");
            if (objective != null) {
                scoreboard.setDisplayObjective(1, objective);
                scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(playerLives.get(uuid));
            }
            // 恢复生命值
            if (playerLives.get(uuid) > 0) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    @Override
    public String getGameName() {
        return "deathrace";
    }

    public Map<UUID, Integer> getPlayerLives() {
        return playerLives;
    }
}
