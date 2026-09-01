package com.minigame.games;

import com.minigame.game.GameManager;
import com.minigame.utils.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 剥蒜的情谊游戏
 * 规则：两人分开传送，每隔一段时间互换位置，想办法用环境杀死对方
 */
public class BrotherhoodGame extends GameBase {

    private final UUID inviterUUID;
    private final UUID inviteeUUID;
    private final String inviteeName;
    private int swapIntervalSeconds;

    public BrotherhoodGame(GameManager manager, MinecraftServer server,
                            UUID inviterUUID, UUID inviteeUUID, String inviteeName,
                            int swapIntervalSeconds) {
        super(manager, server);
        this.inviterUUID = inviterUUID;
        this.inviteeUUID = inviteeUUID;
        this.inviteeName = inviteeName;
        this.swapIntervalSeconds = swapIntervalSeconds;
    }

    @Override
    public void start() {
        ServerPlayer inviter = server.getPlayerList().getPlayer(inviterUUID);
        ServerPlayer invitee = server.getPlayerList().getPlayer(inviteeUUID);

        if (inviter != null) GameUtils.clearInventory(inviter);
        if (invitee != null) GameUtils.clearInventory(invitee);

        // 两人分开传送，距离1000格以上
        if (inviter != null && invitee != null) {
            teleportBothSeparated(inviter, invitee);
        }

        GameUtils.broadcast(server, "§6§l[剥蒜的情谊] §e游戏开始！每" + swapIntervalSeconds + "秒互换位置，想办法用环境杀死对方！");
    }

    @Override
    public void tick() {
        int swapTicks = swapIntervalSeconds * 20;
        if (manager.getTickCounter() % swapTicks == 0) {
            swapPlayers();
        }
    }

    @Override
    public boolean onPlayerAboutToDie(ServerPlayer player) {
        UUID deadUUID = player.getUUID();

        // 确定获胜者（杀死对方的人）
        UUID winnerUUID = deadUUID.equals(inviterUUID) ? inviteeUUID : inviterUUID;
        ServerPlayer winner = server.getPlayerList().getPlayer(winnerUUID);

        if (winner != null) {
            GameUtils.broadcast(server, "§6§l[剥蒜的情谊] §a§l" + winner.getName().getString() + " 获胜了！" + player.getName().getString() + " 被环境杀死了！");
        } else {
            GameUtils.broadcast(server, "§6§l[剥蒜的情谊] 游戏结束！");
        }

        manager.setWinner(winnerUUID);
        manager.finishGame(server);
        return true; // 取消死亡
    }

    @Override
    public void onPlayerChat(ServerPlayer player, String message) {
        // 游戏进行中不需要特殊处理聊天
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        // 任意一方离线，游戏取消
        if (player.getUUID().equals(inviterUUID) || player.getUUID().equals(inviteeUUID)) {
            GameUtils.broadcast(server, "§c[剥蒜的情谊] 玩家 " + player.getName().getString() + " 离线，游戏取消");
            manager.cancelGame();
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        // 玩家重连
    }

    @Override
    public String getGameName() {
        return "brotherhood";
    }

    /**
     * 两人分开传送，距离1000格以上
     */
    private void teleportBothSeparated(ServerPlayer p1, ServerPlayer p2) {
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        BlockPos pos1 = GameUtils.findSafeLocation(server.overworld(), spawn, 30);
        BlockPos pos2 = GameUtils.findSafeLocationFar(server.overworld(), spawn, pos1, 1000, 30);

        p1.teleportTo(server.overworld(), pos1.getX() + 0.5, pos1.getY() + 1.0, pos1.getZ() + 0.5, p1.getYRot(), p1.getXRot());
        p2.teleportTo(server.overworld(), pos2.getX() + 0.5, pos2.getY() + 1.0, pos2.getZ() + 0.5, p2.getYRot(), p2.getXRot());
    }

    /**
     * 互换两人位置
     */
    private void swapPlayers() {
        ServerPlayer p1 = server.getPlayerList().getPlayer(inviterUUID);
        ServerPlayer p2 = server.getPlayerList().getPlayer(inviteeUUID);
        if (p1 == null || p2 == null) return;

        double x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        float yaw1 = p1.getYRot(), pitch1 = p1.getXRot();
        double x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
        float yaw2 = p2.getYRot(), pitch2 = p2.getXRot();

        p1.teleportTo(p1.serverLevel(), x2, y2, z2, yaw2, pitch2);
        p2.teleportTo(p2.serverLevel(), x1, y1, z1, yaw1, pitch1);

        GameUtils.broadcast(server, "§e[剥蒜的情谊] 两人位置互换了！");
    }
}
