package com.minigame.games;

import com.minigame.game.GameManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 游戏基类
 * 所有小游戏都继承这个类
 */
public abstract class GameBase {

    protected final GameManager manager;
    protected final MinecraftServer server;

    public GameBase(GameManager manager, MinecraftServer server) {
        this.manager = manager;
        this.server = server;
    }

    /**
     * 游戏开始时调用
     */
    public abstract void start();

    /**
     * 每tick调用
     */
    public abstract void tick();

    /**
     * 玩家即将死亡时调用
     * @return true表示取消死亡，false表示正常死亡
     */
    public abstract boolean onPlayerAboutToDie(ServerPlayer player);

    /**
     * 玩家聊天时调用
     */
    public abstract void onPlayerChat(ServerPlayer player, String message);

    /**
     * 玩家断开连接时调用
     */
    public abstract void onPlayerDisconnect(ServerPlayer player);

    /**
     * 玩家加入时调用
     */
    public abstract void onPlayerJoin(ServerPlayer player);

    /**
     * 游戏名称
     */
    public abstract String getGameName();
}
