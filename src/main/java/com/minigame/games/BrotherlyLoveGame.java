package com.minigame.games;

import com.minigame.game.GameManager;
import com.minigame.utils.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 情同手足游戏
 * 规则：所有玩家共享物品栏、装备栏、生命值、饥饿值
 * 杀死末影龙并返回主世界则所有玩家共同获胜
 */
public class BrotherlyLoveGame extends GameBase {

    private final Set<UUID> brotherlyPlayers;
    private final UUID brotherlyHostUUID;
    private final Set<UUID> brotherlyVisitedEnd;

    public BrotherlyLoveGame(GameManager manager, MinecraftServer server,
                             Set<UUID> brotherlyPlayers, UUID brotherlyHostUUID) {
        super(manager, server);
        this.brotherlyPlayers = new HashSet<>(brotherlyPlayers);
        this.brotherlyHostUUID = brotherlyHostUUID;
        this.brotherlyVisitedEnd = new HashSet<>();
    }

    @Override
    public void start() {
        BlockPos spawnPos = server.overworld().getSharedSpawnPos();
        BlockPos gamePos = GameUtils.findSafeLocation(server.overworld(), spawnPos, 30);

        for (UUID uuid : brotherlyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                GameUtils.clearInventory(p);
                p.teleportTo(server.overworld(),
                        gamePos.getX() + 0.5 + (Math.random() - 0.5) * 5,
                        gamePos.getY() + 1.0,
                        gamePos.getZ() + 0.5 + (Math.random() - 0.5) * 5,
                        p.getYRot(), p.getXRot());
            }
        }

        GameUtils.broadcast(server, "§6§l[情同手足] §e游戏开始！所有玩家共享物品栏、生命值、饥饿值，杀龙回家共同胜利！");
    }

    @Override
    public void tick() {
        // 同步共享状态
        syncBrotherlyState();
        // 检查胜利条件
        checkBrotherlyWin();
    }

    @Override
    public boolean onPlayerAboutToDie(ServerPlayer player) {
        // 共享生命，一个人死了所有人都死？或者触发不死图腾效果
        GameUtils.triggerTotemEffect(player);
        GameUtils.broadcast(server, "§e[情同手足] " + player.getName().getString() + " 差点死了，共享生命救了他！");
        return true;
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String message) {
        // 游戏进行中不需要特殊处理聊天
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        brotherlyPlayers.remove(uuid);
        brotherlyVisitedEnd.remove(uuid);
    }

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        // 玩家重连
    }

    @Override
    public String getGameName() {
        return "brotherlylove";
    }

    /**
     * 同步共享状态（真正的共享：任何玩家修改都生效）
     */
    private void syncBrotherlyState() {
        List<ServerPlayer> activePlayers = new ArrayList<>();
        for (UUID uuid : brotherlyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) activePlayers.add(p);
        }
        if (activePlayers.size() < 2) return;

        // 找到物品栏与其他人不同的玩家（修改了物品栏的那个）
        ServerPlayer sourcePlayer = activePlayers.get(0);
        for (int i = 1; i < activePlayers.size(); i++) {
            ServerPlayer p = activePlayers.get(i);
            boolean differs = false;
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                if (!ItemStack.matches(p.getInventory().getItem(slot), sourcePlayer.getInventory().getItem(slot))) {
                    differs = true;
                    break;
                }
            }
            if (differs) {
                sourcePlayer = p;
                break;
            }
        }

        // 同步物品栏给所有人
        for (ServerPlayer p : activePlayers) {
            if (p.equals(sourcePlayer)) continue;
            for (int slot = 0; slot < sourcePlayer.getInventory().getContainerSize(); slot++) {
                p.getInventory().setItem(slot, sourcePlayer.getInventory().getItem(slot).copy());
            }
        }

        // 同步生命值和饥饿值（取最低值，或者取第一个玩家的值）
        float minHealth = Float.MAX_VALUE;
        int minFood = Integer.MAX_VALUE;
        for (ServerPlayer p : activePlayers) {
            minHealth = Math.min(minHealth, p.getHealth());
            minFood = Math.min(minFood, p.getFoodData().getFoodLevel());
        }
        for (ServerPlayer p : activePlayers) {
            if (p.getHealth() != minHealth) {
                p.setHealth(minHealth);
            }
            if (p.getFoodData().getFoodLevel() != minFood) {
                p.getFoodData().setFoodLevel(minFood);
            }
        }
    }

    /**
     * 检查胜利条件
     */
    private void checkBrotherlyWin() {
        for (UUID uuid : brotherlyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;

            // 玩家在末地，记录
            if ("minecraft:the_end".equals(p.serverLevel().dimension().location().toString())) {
                brotherlyVisitedEnd.add(uuid);
            }

            // 玩家回到主世界，且击杀了末影龙
            if ("minecraft:overworld".equals(p.serverLevel().dimension().location().toString())) {
                if (brotherlyVisitedEnd.contains(uuid) && GameUtils.hasKilledDragon(p)) {
                    brotherlyGameWin();
                    return;
                }
            }
        }
    }

    /**
     * 游戏获胜
     */
    private void brotherlyGameWin() {
        GameUtils.broadcast(server, "§6§l[情同手足] §a§l所有玩家共同获胜！成功杀死末影龙并回到主世界！");
        manager.setWinner(brotherlyHostUUID);
        manager.finishGame(server);
    }

    public Set<UUID> getPlayers() {
        return brotherlyPlayers;
    }
}
