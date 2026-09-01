package com.minigame.games;

import com.minigame.game.GameManager;
import com.minigame.utils.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 色盲派对游戏
 * 规则：系统随机生成颜色，玩家需要站到对应颜色的方块上
 * 站错颜色损失生命，最终目标是合力杀死末影龙解除诅咒
 */
public class ColorPartyGame extends GameBase {

    private final Set<UUID> colorPartyPlayers;
    private final Map<UUID, Integer> colorPartyLives;
    private int colorPartyMaxLives; // 0 = 无限生命
    private int colorReactionSeconds;
    private int colorIntervalSeconds;
    private String currentTargetColor;
    private int colorTimer;
    private boolean reactionPhase;

    public ColorPartyGame(GameManager manager, MinecraftServer server,
                          Set<UUID> colorPartyPlayers, int colorPartyMaxLives,
                          int colorReactionSeconds, int colorIntervalSeconds) {
        super(manager, server);
        this.colorPartyPlayers = new HashSet<>(colorPartyPlayers);
        this.colorPartyLives = new HashMap<>();
        this.colorPartyMaxLives = colorPartyMaxLives;
        this.colorReactionSeconds = colorReactionSeconds;
        this.colorIntervalSeconds = colorIntervalSeconds;
        this.currentTargetColor = null;
        this.colorTimer = colorIntervalSeconds * 20;
        this.reactionPhase = false;

        // 初始化生命
        for (UUID uuid : colorPartyPlayers) {
            if (colorPartyMaxLives > 0) {
                colorPartyLives.put(uuid, colorPartyMaxLives);
            }
        }
    }

    @Override
    public void start() {
        BlockPos spawnPos = server.overworld().getSharedSpawnPos();
        Random rand = new Random();
        int x = spawnPos.getX() + rand.nextInt(1000) - 500;
        int z = spawnPos.getZ() + rand.nextInt(1000) - 500;
        int y = server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) + 1;

        for (UUID uuid : colorPartyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.teleportTo(server.overworld(), x, y, z, Collections.emptySet(), p.getYRot(), p.getXRot());
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFoodLevel(20);
            }
        }

        String livesText = colorPartyMaxLives == 0 ? "无限" : String.valueOf(colorPartyMaxLives);
        GameUtils.broadcast(server, "§6§l[色盲派对] 游戏开始！每人" + livesText + "条命，站到对应颜色的方块上！");
    }

    @Override
    public void tick() {
        colorTimer--;

        if (!reactionPhase) {
            // 间隔阶段，生成新颜色
            if (colorTimer <= 0) {
                currentTargetColor = GameUtils.getRandomColor();
                reactionPhase = true;
                colorTimer = colorReactionSeconds * 20;
                GameUtils.broadcast(server, "§6§l[色盲派对] 目标颜色：§e§l" + currentTargetColor + " §6§l！快站上去！");
                GameUtils.executeTitleAll(server, "颜色：" + currentTargetColor);
            }
        } else {
            // 反应阶段，检查玩家
            int secondsLeft = (colorTimer / 20) + 1;
            if (colorTimer % 20 == 0 && secondsLeft > 0 && secondsLeft <= 3) {
                GameUtils.broadcast(server, "§e[色盲派对] 还剩 " + secondsLeft + " 秒！");
            }

            // 显示当前脚下方块颜色
            for (UUID uuid : colorPartyPlayers) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    String blockColor = GameUtils.getPlayerBlockColor(p);
                    p.displayClientMessage(Component.literal("§e当前方块：" + blockColor + " | 目标：" + currentTargetColor), true);
                }
            }

            if (colorTimer <= 0) {
                // 反应时间结束，检查玩家
                for (UUID uuid : colorPartyPlayers) {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p == null) continue;
                    int lives = colorPartyLives.getOrDefault(uuid, 0);
                    if (lives <= 0 && colorPartyMaxLives > 0) continue;

                    String blockColor = GameUtils.getPlayerBlockColor(p);
                    if (!blockColor.equals(currentTargetColor)) {
                        // 站错颜色
                        if (colorPartyMaxLives > 0) {
                            lives--;
                            colorPartyLives.put(uuid, lives);
                        }

                        if (lives <= 0 && colorPartyMaxLives > 0) {
                            GameUtils.broadcast(server, "§c[色盲派对] " + p.getName().getString() + " 命数耗尽，被淘汰！");
                        } else {
                            String livesText = colorPartyMaxLives == 0 ? "无限" : String.valueOf(lives);
                            GameUtils.broadcast(server, "§c[色盲派对] " + p.getName().getString() + " 站错颜色！剩余" + livesText + "条命");
                        }
                        p.setHealth(0);
                    } else {
                        p.sendSystemMessage(Component.literal("§a[色盲派对] 站对颜色了！"));
                    }
                }

                checkColorPartyWin();

                reactionPhase = false;
                currentTargetColor = null;
                colorTimer = colorIntervalSeconds * 20;
            }
        }
    }

    @Override
    public boolean onPlayerAboutToDie(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!colorPartyPlayers.contains(uuid)) return false;

        int lives = colorPartyLives.getOrDefault(uuid, 0);

        if (colorPartyMaxLives > 0 && lives <= 0) {
            // 命数耗尽，正常死亡（被淘汰）
            return false;
        }

        // 还有命（或无限命），触发不死图腾效果
        GameUtils.triggerTotemEffect(player);
        return true;
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String message) {
        // 游戏进行中不需要特殊处理聊天
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        colorPartyPlayers.remove(uuid);
        colorPartyLives.remove(uuid);
    }

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        // 玩家重连
    }

    @Override
    public String getGameName() {
        return "colorparty";
    }

    /**
     * 检查游戏胜利条件
     */
    private void checkColorPartyWin() {
        // 检查是否有玩家击杀末影龙并返回主世界
        for (UUID uuid : colorPartyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;

            if ("minecraft:overworld".equals(p.serverLevel().dimension().location().toString())) {
                if (GameUtils.hasKilledDragon(p)) {
                    colorPartyWin();
                    return;
                }
            }
        }

        // 检查是否所有玩家都被淘汰
        if (colorPartyMaxLives > 0) {
            boolean allDead = true;
            for (UUID uuid : colorPartyPlayers) {
                int lives = colorPartyLives.getOrDefault(uuid, 0);
                if (lives > 0) {
                    allDead = false;
                    break;
                }
            }
            if (allDead && !colorPartyPlayers.isEmpty()) {
                GameUtils.broadcast(server, "§c[色盲派对] 所有玩家都被淘汰了，游戏结束！");
                manager.cancelGame();
            }
        }
    }

    /**
     * 游戏获胜
     */
    private void colorPartyWin() {
        GameUtils.broadcast(server, "§6§l[色盲派对] §a§l所有玩家共同获胜！成功杀死末影龙，色盲诅咒解除！");
        // 用第一个玩家作为代表
        UUID winnerUUID = colorPartyPlayers.isEmpty() ? null : colorPartyPlayers.iterator().next();
        manager.setWinner(winnerUUID);
        manager.finishGame(server);
    }

    public Set<UUID> getPlayers() {
        return colorPartyPlayers;
    }
}
