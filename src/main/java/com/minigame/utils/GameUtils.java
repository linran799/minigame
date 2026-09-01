package com.minigame.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.*;

/**
 * 通用游戏工具方法
 */
public class GameUtils {

    private static final String OBJECTIVE_NAME = "minigame_data";

    // 颜色名称映射
    private static final Map<Integer, String> COLOR_NAMES = new HashMap<>();
    static {
        COLOR_NAMES.put(1, "草绿");
        COLOR_NAMES.put(2, "沙黄");
        COLOR_NAMES.put(3, "白色");
        COLOR_NAMES.put(4, "红色");
        COLOR_NAMES.put(5, "淡紫");
        COLOR_NAMES.put(6, "冰蓝");
        COLOR_NAMES.put(7, "铁灰");
        COLOR_NAMES.put(8, "深绿");
        COLOR_NAMES.put(9, "白色");
        COLOR_NAMES.put(10, "天蓝");
        COLOR_NAMES.put(11, "棕色");
        COLOR_NAMES.put(12, "深灰");
        COLOR_NAMES.put(13, "红色");
        COLOR_NAMES.put(14, "橙色");
        COLOR_NAMES.put(15, "灰色");
        COLOR_NAMES.put(16, "淡绿");
        COLOR_NAMES.put(17, "红色");
        COLOR_NAMES.put(18, "紫色");
        COLOR_NAMES.put(19, "蓝色");
        COLOR_NAMES.put(20, "青色");
        COLOR_NAMES.put(21, "金色");
        COLOR_NAMES.put(22, "绿色");
        COLOR_NAMES.put(23, "深红");
        COLOR_NAMES.put(24, "深蓝");
        COLOR_NAMES.put(25, "深棕");
        COLOR_NAMES.put(26, "黑色");
        COLOR_NAMES.put(27, "深绿");
        COLOR_NAMES.put(28, "深灰");
        COLOR_NAMES.put(29, "深青");
        COLOR_NAMES.put(30, "深蓝");
        COLOR_NAMES.put(31, "深紫");
        COLOR_NAMES.put(32, "深红");
        COLOR_NAMES.put(33, "深橙");
        COLOR_NAMES.put(34, "深黄");
        COLOR_NAMES.put(35, "深绿");
        COLOR_NAMES.put(36, "深青");
        COLOR_NAMES.put(37, "深蓝");
        COLOR_NAMES.put(38, "深紫");
        COLOR_NAMES.put(39, "深红");
        COLOR_NAMES.put(40, "深橙");
        COLOR_NAMES.put(41, "深黄");
        COLOR_NAMES.put(42, "深绿");
        COLOR_NAMES.put(43, "深青");
    }

    /**
     * 广播消息给所有玩家
     */
    public static void broadcast(MinecraftServer server, String message) {
        Component msg = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    /**
     * 清空玩家物品栏（包括装备栏和副手）
     */
    public static void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().armor.clear();
        player.getInventory().offhand.clear();
    }

    /**
     * 查找安全位置
     */
    public static BlockPos findSafeLocation(ServerLevel level, BlockPos center, int maxAttempts) {
        Random rand = new Random();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int dx = (int) ((Math.random() - 0.5) * 1000);
            int dz = (int) ((Math.random() - 0.5) * 1000);
            int targetX = center.getX() + dx;
            int targetZ = center.getZ() + dz;

            BlockPos safe = checkLocationSafety(level, targetX, targetZ);
            if (safe != null) return safe;
        }
        return center;
    }

    /**
     * 查找远离指定位置的安全位置
     */
    public static BlockPos findSafeLocationFar(ServerLevel level, BlockPos center, BlockPos awayFrom, int minDistance, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int dx = (int) ((Math.random() - 0.5) * 2000);
            int dz = (int) ((Math.random() - 0.5) * 2000);
            int targetX = center.getX() + dx;
            int targetZ = center.getZ() + dz;

            double dist = Math.sqrt(Math.pow(targetX - awayFrom.getX(), 2) + Math.pow(targetZ - awayFrom.getZ(), 2));
            if (dist < minDistance) continue;

            BlockPos safe = checkLocationSafety(level, targetX, targetZ);
            if (safe != null) return safe;
        }
        return center;
    }

    /**
     * 检查位置是否安全
     */
    public static BlockPos checkLocationSafety(ServerLevel level, int targetX, int targetZ) {
        int targetY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, targetX, targetZ);
        if (targetY <= 10) return null;

        BlockPos landingPos = new BlockPos(targetX, targetY, targetZ);
        BlockPos feetPos = landingPos.above();
        BlockPos headPos = landingPos.above(2);

        BlockState landingBlock = level.getBlockState(landingPos);
        BlockState feetBlock = level.getBlockState(feetPos);
        BlockState headBlock = level.getBlockState(headPos);

        boolean safe = !landingBlock.is(Blocks.LAVA)
                && !landingBlock.is(Blocks.FIRE)
                && !feetBlock.is(Blocks.LAVA)
                && !headBlock.is(Blocks.LAVA)
                && feetBlock.isAir()
                && headBlock.isAir()
                && targetY > 10 && targetY < 300;

        return safe ? landingPos : null;
    }

    /**
     * 生成烟花
     */
    public static void spawnFirework(ServerLevel level, double x, double y, double z) {
        ItemStack fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag tag = fireworkStack.getOrCreateTag();
        CompoundTag fireworksTag = new CompoundTag();
        fireworksTag.putByte("Flight", (byte) 1);

        ListTag explosionsTag = new ListTag();
        CompoundTag explosionTag = new CompoundTag();
        explosionTag.putByte("Type", (byte) 4);
        explosionTag.putIntArray("Colors", new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF});
        explosionTag.putIntArray("FadeColors", new int[]{0xFFFFFF});
        explosionTag.putBoolean("Flicker", true);
        explosionTag.putBoolean("Trail", true);
        explosionsTag.add(explosionTag);

        fireworksTag.put("Explosions", explosionsTag);
        tag.put("Fireworks", fireworksTag);

        FireworkRocketEntity firework = new FireworkRocketEntity(level, x, y, z, fireworkStack);
        level.addFreshEntity(firework);
    }

    /**
     * 给指定玩家显示标题
     */
    public static void executeTitleCommand(MinecraftServer server, String playerName, String title, String subtitle) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title " + playerName + " times 10 50 10");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title " + playerName + " title {\"text\":\"" + title + "\"}");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title " + playerName + " subtitle {\"text\":\"" + subtitle + "\"}");
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * 给所有玩家显示标题
     */
    public static void executeTitleAll(MinecraftServer server, String text) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a times 10 50 10");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a title {\"text\":\"" + text + "\"}");
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * 给所有玩家显示标题和副标题
     */
    public static void executeTitleAll(MinecraftServer server, String title, String subtitle) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a times 10 50 10");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a title {\"text\":\"" + title + "\"}");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a subtitle {\"text\":\"" + subtitle + "\"}");
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * 触发不死图腾效果
     */
    public static void triggerTotemEffect(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.fallDistance = 0;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
    }

    /**
     * 随机传送玩家到安全位置
     */
    public static void randomTeleport(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = level.getSharedSpawnPos();

        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = (int) ((Math.random() - 0.5) * 4000);
            int dz = (int) ((Math.random() - 0.5) * 4000);
            int targetX = center.getX() + dx;
            int targetZ = center.getZ() + dz;
            int targetY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, targetX, targetZ);

            if (targetY <= 10) continue;

            BlockPos landingPos = new BlockPos(targetX, targetY, targetZ);
            BlockPos feetPos = landingPos.above();
            BlockPos headPos = landingPos.above(2);

            BlockState landingBlock = level.getBlockState(landingPos);
            BlockState feetBlock = level.getBlockState(feetPos);
            BlockState headBlock = level.getBlockState(headPos);

            boolean safe = !landingBlock.is(Blocks.LAVA)
                    && !landingBlock.is(Blocks.FIRE)
                    && !feetBlock.is(Blocks.LAVA)
                    && !headBlock.is(Blocks.LAVA)
                    && feetBlock.isAir()
                    && headBlock.isAir()
                    && targetY > 10 && targetY < 300;

            if (safe) {
                player.teleportTo(level,
                        targetX + 0.5, targetY + 1.0, targetZ + 0.5,
                        player.getYRot(), player.getXRot());
                return;
            }
        }

        // Fallback: teleport to world spawn
        BlockPos spawn = level.getSharedSpawnPos();
        player.teleportTo(level,
                spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /**
     * 检查玩家是否已击杀末影龙
     */
    public static boolean hasKilledDragon(ServerPlayer player) {
        try {
            var advancements = player.server.getAdvancements();
            var resourceLoc = new net.minecraft.resources.ResourceLocation("minecraft", "end/kill_dragon");
            var adv = advancements.getAdvancement(resourceLoc);
            if (adv != null) {
                return player.getAdvancements().getOrStartProgress(adv).isDone();
            }
        } catch (Exception e) {
            // Fallback
        }
        return false;
    }

    /**
     * 获取方块颜色名称
     */
    public static String getBlockColorName(BlockState state, ServerLevel level, BlockPos pos) {
        try {
            MapColor mapColor = state.getMapColor(level, pos);
            int colorId = mapColor.id;
            return COLOR_NAMES.getOrDefault(colorId, "未知");
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 获取玩家脚下方块颜色
     */
    public static String getPlayerBlockColor(ServerPlayer player) {
        BlockPos feetPos = player.blockPosition().below();
        BlockState state = player.serverLevel().getBlockState(feetPos);
        return getBlockColorName(state, player.serverLevel(), feetPos);
    }

    /**
     * 获取随机颜色
     */
    public static String getRandomColor() {
        List<String> colors = new ArrayList<>(COLOR_NAMES.values());
        // 去重
        Set<String> uniqueColors = new LinkedHashSet<>(colors);
        List<String> colorList = new ArrayList<>(uniqueColors);
        return colorList.get((int) (Math.random() * colorList.size()));
    }
}
