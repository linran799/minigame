package com.minigame.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.resources.ResourceLocation;

/**
 * 获胜庆祝相关方法
 */
public class WinnerCelebration {

    private static final int CELEBRATION_DURATION = 20 * 10; // 10秒

    /**
     * 开始获胜庆祝
     */
    public static void startWinnerCelebration(MinecraftServer server, ServerPlayer winnerPlayer, BlockPos winnerPlatformPos) {
        ServerLevel overworld = server.overworld();

        // 构建获胜平台
        if (winnerPlatformPos == null) {
            winnerPlatformPos = new BlockPos(
                    (int) winnerPlayer.getX(),
                    200,
                    (int) winnerPlayer.getZ()
            );
        }

        buildWinnerPlatform(overworld, winnerPlatformPos);

        // 传送获胜者到平台顶部
        BlockPos topPos = winnerPlatformPos.above(3);
        winnerPlayer.teleportTo(overworld,
                topPos.getX() + 0.5, topPos.getY() + 1.0, topPos.getZ() + 0.5,
                winnerPlayer.getYRot(), winnerPlayer.getXRot());

        // 显示获胜标题
        GameUtils.executeTitleAll(server, "恭喜获胜！", winnerPlayer.getName().getString() + " 赢得了游戏！");
        GameUtils.broadcast(server, "§6§l恭喜 " + winnerPlayer.getName().getString() + " 获胜！");
    }

    /**
     * 构建获胜平台（使用结构文件）
     */
    public static void buildWinnerPlatform(ServerLevel level, BlockPos center) {
        try {
            // 尝试加载结构文件
            ResourceLocation structureId = new ResourceLocation("foundation", "winner_podium");
            StructureTemplate template = level.getStructureManager().get(structureId).orElse(null);

            if (template != null) {
                StructurePlaceSettings settings = new StructurePlaceSettings();
                template.placeInWorld(level, center, center, settings, level.getRandom(), 2);
                return;
            }
        } catch (Exception e) {
            // 结构加载失败，使用备用方案
        }

        // 备用方案：手动构建简单平台
        buildFallbackPlatform(level, center);
    }

    /**
     * 备用平台构建方案
     */
    private static void buildFallbackPlatform(ServerLevel level, BlockPos center) {
        // 25x25 钻石平台
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                level.setBlock(center.offset(dx, 0, dz), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            }
        }

        // 中心绿宝石+黄金结构
        level.setBlock(center, Blocks.EMERALD_BLOCK.defaultBlockState(), 2);
        level.setBlock(center.above(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(center.above(2), Blocks.GOLD_BLOCK.defaultBlockState(), 2);

        // 中心周围黄金块
        level.setBlock(center.north(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(center.south(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(center.east(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(center.west(), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
    }

    /**
     * 庆祝tick（放烟花）
     */
    public static void tickCelebration(MinecraftServer server, ServerPlayer winnerPlayer, int fireworkTimer) {
        if (winnerPlayer == null) return;

        if (fireworkTimer % 20 == 0) {
            // 在获胜者周围放烟花
            double x = winnerPlayer.getX() + (Math.random() - 0.5) * 10;
            double y = winnerPlayer.getY() + 5 + Math.random() * 5;
            double z = winnerPlayer.getZ() + (Math.random() - 0.5) * 10;
            GameUtils.spawnFirework(winnerPlayer.serverLevel(), x, y, z);
        }
    }
}
