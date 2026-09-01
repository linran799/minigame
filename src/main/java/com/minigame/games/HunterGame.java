package com.minigame.games;

import com.minigame.game.GameManager;
import com.minigame.utils.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * 猎人游戏
 * 规则：所有玩家追杀猎物（发起者），猎物死亡则猎人胜利
 * 猎物杀龙并返回主世界则猎物胜利
 * 猎人有指南针指向猎物
 */
public class HunterGame extends GameBase {

    private final Set<UUID> hunterGamePlayers;
    private final UUID preyUUID;
    private final Set<UUID> hunterUUIDs;
    private int hunterFreezeSeconds;
    private int compassUpdateSeconds;
    private BlockPos preyLastOverworldPos;
    private String preyDimension;
    private int freezeTimer;

    public HunterGame(GameManager manager, MinecraftServer server,
                      Set<UUID> hunterGamePlayers, UUID preyUUID,
                      int hunterFreezeSeconds, int compassUpdateSeconds) {
        super(manager, server);
        this.hunterGamePlayers = new HashSet<>(hunterGamePlayers);
        this.preyUUID = preyUUID;
        this.hunterUUIDs = new HashSet<>();
        this.hunterFreezeSeconds = hunterFreezeSeconds;
        this.compassUpdateSeconds = compassUpdateSeconds;
        this.preyDimension = "minecraft:overworld";
        this.freezeTimer = hunterFreezeSeconds * 20;

        // 猎人 = 除了猎物之外的所有人
        for (UUID uuid : hunterGamePlayers) {
            if (!uuid.equals(preyUUID)) {
                hunterUUIDs.add(uuid);
            }
        }
    }

    @Override
    public void start() {
        BlockPos spawnPos = server.overworld().getSharedSpawnPos();
        BlockPos gamePos = GameUtils.findSafeLocation(server.overworld(), spawnPos, 30);

        // 传送所有玩家
        for (UUID uuid : hunterGamePlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                GameUtils.clearInventory(p);
                p.teleportTo(server.overworld(),
                        gamePos.getX() + 0.5 + (Math.random() - 0.5) * 10,
                        gamePos.getY() + 1.0,
                        gamePos.getZ() + 0.5 + (Math.random() - 0.5) * 10,
                        p.getYRot(), p.getXRot());
            }
        }

        // 给猎人指南针
        for (UUID uuid : hunterUUIDs) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                giveHunterCompass(p);
            }
        }

        // 初始化猎物位置追踪
        ServerPlayer prey = server.getPlayerList().getPlayer(preyUUID);
        if (prey != null) {
            preyLastOverworldPos = prey.blockPosition();
            preyDimension = prey.serverLevel().dimension().location().toString();
        }

        GameUtils.broadcast(server, "§6§l[猎人游戏] §e游戏开始！猎人们冻结" + hunterFreezeSeconds + "秒，猎物快跑！");
    }

    @Override
    public void tick() {
        // 冻结阶段
        if (freezeTimer > 0) {
            tickHunterFreeze();
            return;
        }

        // 更新指南针
        int compassTicks = compassUpdateSeconds * 20;
        if (manager.getTickCounter() % compassTicks == 0) {
            updateHunterCompasses();
        }

        // 检查猎物胜利条件
        checkPreyWinCondition();
    }

    @Override
    public boolean onPlayerAboutToDie(ServerPlayer player) {
        UUID deadUUID = player.getUUID();

        if (deadUUID.equals(preyUUID)) {
            // 猎物死亡，猎人胜利
            GameUtils.broadcast(server, "§6§l[猎人游戏] §c猎物 " + player.getName().getString() + " 死亡！§a猎人们获胜了！");
            // 用第一个猎人作为代表
            UUID winnerUUID = hunterUUIDs.iterator().next();
            manager.setWinner(winnerUUID);
            manager.finishGame(server);
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            return true;
        } else {
            // 猎人死亡，复活并重新给指南针
            GameUtils.broadcast(server, "§e[猎人游戏] 猎人 " + player.getName().getString() + " 死亡，复活！");
            GameUtils.triggerTotemEffect(player);
            giveHunterCompass(player);
            return true;
        }
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String message) {
        // 游戏进行中不需要特殊处理聊天
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        hunterGamePlayers.remove(uuid);
        hunterUUIDs.remove(uuid);

        // 猎物离线，猎人胜利
        if (uuid.equals(preyUUID)) {
            GameUtils.broadcast(server, "§c[猎人游戏] 猎物离线，猎人获胜！");
            UUID winnerUUID = hunterUUIDs.isEmpty() ? null : hunterUUIDs.iterator().next();
            manager.setWinner(winnerUUID);
            manager.finishGame(server);
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // 猎人重连，重新给指南针
        if (hunterUUIDs.contains(uuid)) {
            giveHunterCompass(player);
        }
    }

    @Override
    public String getGameName() {
        return "huntergame";
    }

    /**
     * 猎人冻结阶段tick
     */
    private void tickHunterFreeze() {
        freezeTimer--;
        int secondsLeft = (freezeTimer / 20) + 1;

        // 通过传送回原位防止移动
        for (UUID uuid : hunterUUIDs) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            }
        }

        if (freezeTimer % 20 == 0 && secondsLeft > 0 && secondsLeft <= 10) {
            GameUtils.broadcast(server, "§e猎人还有 " + secondsLeft + " 秒解冻！");
        }

        if (freezeTimer <= 0) {
            GameUtils.broadcast(server, "§6§l[猎人游戏] §c猎人们解冻了！开始追杀！");
        }
    }

    /**
     * 给猎人指南针
     */
    private void giveHunterCompass(ServerPlayer player) {
        ItemStack compass = new ItemStack(Items.COMPASS);
        CompoundTag tag = compass.getOrCreateTag();
        tag.putBoolean("LodestoneTracked", false);
        player.getInventory().add(compass);
    }

    /**
     * 更新所有猎人的指南针
     */
    private void updateHunterCompasses() {
        ServerPlayer prey = server.getPlayerList().getPlayer(preyUUID);
        if (prey == null) return;

        String preyDim = prey.serverLevel().dimension().location().toString();
        BlockPos preyPos = prey.blockPosition();

        // 更新主世界最后位置
        if ("minecraft:overworld".equals(preyDim)) {
            preyLastOverworldPos = preyPos;
        }
        preyDimension = preyDim;

        // 确定指南针目标
        BlockPos targetPos;
        String targetDim;
        if ("minecraft:overworld".equals(preyDim)) {
            targetPos = preyPos;
            targetDim = preyDim;
        } else {
            // 猎物在其他维度，指向最后在主世界的位置
            targetPos = preyLastOverworldPos != null ? preyLastOverworldPos : preyPos;
            targetDim = "minecraft:overworld";
        }

        // 更新所有猎人的指南针
        for (UUID uuid : hunterUUIDs) {
            ServerPlayer hunter = server.getPlayerList().getPlayer(uuid);
            if (hunter == null) continue;

            // 检查猎人是否靠近猎物（同维度20格内失效）
            boolean tooClose = false;
            if (hunter.serverLevel().dimension().equals(prey.serverLevel().dimension())) {
                double dist = Math.sqrt(
                        Math.pow(hunter.getX() - prey.getX(), 2) +
                        Math.pow(hunter.getZ() - prey.getZ(), 2));
                tooClose = dist <= 20;
            }

            for (int i = 0; i < hunter.getInventory().getContainerSize(); i++) {
                ItemStack stack = hunter.getInventory().getItem(i);
                if (stack.is(Items.COMPASS)) {
                    CompoundTag tag = stack.getOrCreateTag();
                    if (tooClose) {
                        // 20格内失效，移除磁石数据
                        tag.remove("LodestonePos");
                        tag.remove("LodestoneDimension");
                        tag.remove("LodestoneTracked");
                    } else {
                        // 更新指南针指向猎物
                        CompoundTag posTag = new CompoundTag();
                        posTag.putInt("X", targetPos.getX());
                        posTag.putInt("Y", targetPos.getY());
                        posTag.putInt("Z", targetPos.getZ());
                        tag.put("LodestonePos", posTag);
                        tag.putString("LodestoneDimension", targetDim);
                        tag.putBoolean("LodestoneTracked", false);
                    }
                }
            }
        }
    }

    /**
     * 检查猎物胜利条件
     */
    private void checkPreyWinCondition() {
        ServerPlayer prey = server.getPlayerList().getPlayer(preyUUID);
        if (prey == null) return;

        // 猎物必须击杀末影龙并返回主世界
        if ("minecraft:overworld".equals(prey.serverLevel().dimension().location().toString())) {
            if (GameUtils.hasKilledDragon(prey)) {
                hunterGamePreyWins(prey);
            }
        }
    }

    /**
     * 猎物获胜
     */
    private void hunterGamePreyWins(ServerPlayer prey) {
        GameUtils.broadcast(server, "§6§l[猎人游戏] §a§l" + prey.getName().getString() + " §e§l（猎物）获胜了！成功杀龙并返回主世界！");
        manager.setWinner(preyUUID);
        manager.finishGame(server);
    }

    public boolean isFrozen() {
        return freezeTimer > 0;
    }
}
