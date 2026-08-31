package com.minigame.game;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

import java.util.*;

public class GameManager {
    public static final GameManager INSTANCE = new GameManager();

    private enum GameState {
        IDLE, WAITING_CONFIRM, WAITING_LIVES, WAITING_REGEN,
        WAITING_SWAP_TIME, WAITING_INVITE, WAITING_INVEE_CONFIRM,
        WAITING_HUNTER_FREEZE, WAITING_COMPASS_INTERVAL,
        WAITING_JOIN, HUNTER_FREEZE,
        WAITING_BROTHERLY_INVITE, WAITING_BROTHERLY_ACCEPT,
        COUNTDOWN, RUNNING, FINISHED
    }

    private GameState state = GameState.IDLE;
    private String currentGame = null;
    private UUID winner = null;
    private int tickCounter = 0;
    private int countdownTimer = 0;
    private int fireworkTimer = 0;
    private BlockPos winnerPlatformPos = null;

    // Deathrace settings
    private int maxLives = 64;
    private int regenIntervalSeconds = 60;

    // Brotherhood settings
    private int swapIntervalSeconds = 60;
    private UUID inviterUUID = null;
    private UUID inviteeUUID = null;
    private String inviteeName = null;

    // Hunter game settings
    private final Set<UUID> hunterGamePlayers = new HashSet<>();
    private UUID preyUUID = null;
    private final Set<UUID> hunterUUIDs = new HashSet<>();
    private int joinTimer = 0;
    private int freezeTimer = 0;
    private int hunterFreezeSeconds = 30;
    private int compassUpdateSeconds = 10;
    private BlockPos preyLastOverworldPos = null;
    private String preyDimension = "minecraft:overworld";

    // Brotherly love game settings
    private final Set<UUID> brotherlyPlayers = new HashSet<>();
    private UUID brotherlyHostUUID = null;
    private final Set<UUID> brotherlyPendingInvites = new HashSet<>();
    private final Set<UUID> brotherlyVisitedEnd = new HashSet<>();

    private static final String OBJECTIVE_NAME = "minigame_data";
    private final Map<UUID, Integer> playerLives = new HashMap<>();

    private GameManager() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
    }

    public boolean isGameRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isGameActive() {
        return state != GameState.IDLE;
    }

    public boolean isInSetupFlow() {
        return state == GameState.WAITING_CONFIRM
                || state == GameState.WAITING_LIVES
                || state == GameState.WAITING_REGEN
                || state == GameState.WAITING_SWAP_TIME
                || state == GameState.WAITING_INVITE
                || state == GameState.WAITING_INVEE_CONFIRM
                || state == GameState.WAITING_HUNTER_FREEZE
                || state == GameState.WAITING_COMPASS_INTERVAL
                || state == GameState.WAITING_JOIN
                || state == GameState.WAITING_BROTHERLY_INVITE
                || state == GameState.WAITING_BROTHERLY_ACCEPT;
    }

    public String getCurrentGame() {
        return currentGame;
    }

    // === Game Start Flow ===

    public void requestStart(String gameName, MinecraftServer server) {
        if (state != GameState.IDLE) {
            broadcast(server, "§c[小游戏] 当前有游戏流程正在进行，请先完成或取消");
            return;
        }
        currentGame = gameName;
        state = GameState.WAITING_CONFIRM;

        if ("deathrace".equals(gameName)) {
            broadcast(server, "§6§l=== 死亡竞速 ===");
            broadcast(server, "§e确认开始游戏？输入 §a§ly §e确认，§c§ln §e取消");
            broadcast(server, "§c注意：游戏开始后会清空所有玩家的物品栏和装备栏！");
        } else if ("brotherhood".equals(gameName)) {
            broadcast(server, "§6§l=== 剥蒜的情谊 ===");
            broadcast(server, "§e确认开始游戏？输入 §a§ly §e确认，§c§ln §e取消");
            broadcast(server, "§c注意：游戏开始后会清空你和对手的物品栏和装备栏！");
        } else if ("huntergame".equals(gameName)) {
            broadcast(server, "§6§l=== 猎人游戏 ===");
            broadcast(server, "§e确认开始游戏？输入 §a§ly §e确认，§c§ln §e取消");
            broadcast(server, "§c注意：发起者为猎物，其他加入者为猎人！游戏开始后会清空所有玩家物品栏！");
        } else if ("brotherlylove".equals(gameName)) {
            broadcast(server, "§6§l=== 情同手足 ===");
            broadcast(server, "§e确认开始游戏？输入 §a§ly §e确认，§c§ln §e取消");
            broadcast(server, "§c注意：所有玩家共享物品栏、生命值、饥饿值！");
        }
    }

    public void onPlayerChat(ServerPlayer player, String message) {
        String msg = message.trim().toLowerCase();
        String rawMsg = message.trim();
        MinecraftServer server = player.server;

        switch (state) {
            case WAITING_CONFIRM -> handleConfirm(server, player, msg);
            case WAITING_LIVES -> handleLivesInput(server, player, msg);
            case WAITING_REGEN -> handleRegenInput(server, player, msg);
            case WAITING_SWAP_TIME -> handleSwapTimeInput(server, player, msg);
            case WAITING_INVITE -> handleInviteInput(server, player, rawMsg);
            case WAITING_INVEE_CONFIRM -> handleInviteeConfirm(server, player, msg);
            case WAITING_HUNTER_FREEZE -> handleHunterFreezeInput(server, player, msg);
            case WAITING_COMPASS_INTERVAL -> handleCompassIntervalInput(server, player, msg);
            case WAITING_BROTHERLY_INVITE -> handleBrotherlyInvite(server, player, rawMsg);
            case WAITING_BROTHERLY_ACCEPT -> handleBrotherlyAccept(server, player, msg);
            case WAITING_JOIN -> handleHunterJoin(server, player, msg);
            default -> {}
        }
    }

    private void handleConfirm(MinecraftServer server, ServerPlayer player, String msg) {
        if (msg.equals("y")) {
            if ("deathrace".equals(currentGame)) {
                state = GameState.WAITING_LIVES;
                broadcast(server, "§a确认开始！ §e请输入本次游戏每人的命数（数字，例如 64）：");
            } else if ("brotherhood".equals(currentGame)) {
                inviterUUID = player.getUUID();
                state = GameState.WAITING_SWAP_TIME;
                broadcast(server, "§a确认开始！ §e请输入互换位置的时间间隔（秒，例如 60）：");
            } else if ("huntergame".equals(currentGame)) {
                preyUUID = player.getUUID();
                hunterGamePlayers.clear();
                hunterGamePlayers.add(player.getUUID());
                state = GameState.WAITING_HUNTER_FREEZE;
                broadcast(server, "§a确认开始！ §e请输入猎人冻结时间（秒，默认30）：");
            } else if ("brotherlylove".equals(currentGame)) {
                brotherlyHostUUID = player.getUUID();
                brotherlyPlayers.add(player.getUUID());
                state = GameState.WAITING_BROTHERLY_INVITE;
                broadcast(server, "§a确认开始！ §e请输入要邀请的玩家名字（输入 done 结束邀请）：");
            }
        } else if (msg.equals("n")) {
            cancelStart(server);
        }
    }

    private void handleLivesInput(MinecraftServer server, ServerPlayer player, String msg) {
        try {
            int lives = Integer.parseInt(msg);
            if (lives < 1) {
                broadcast(server, "§c命数必须大于0，请重新输入");
                return;
            }
            maxLives = lives;
            state = GameState.WAITING_REGEN;
            broadcast(server, "§a命数设置为：" + lives + " §e请输入恢复一条命的时间（秒，例如 60）：");
        } catch (NumberFormatException e) {
            broadcast(server, "§c请输入有效的数字");
        }
    }

    private void handleRegenInput(MinecraftServer server, ServerPlayer player, String msg) {
        try {
            int seconds = Integer.parseInt(msg);
            if (seconds < 1) {
                broadcast(server, "§c时间必须大于0，请重新输入");
                return;
            }
            regenIntervalSeconds = seconds;
            broadcast(server, "§a恢复时间设置为：每" + seconds + "秒恢复一条命");
            startCountdown(server);
        } catch (NumberFormatException e) {
            broadcast(server, "§c请输入有效的数字");
        }
    }

    // === Brotherhood Game Flow ===

    private void handleSwapTimeInput(MinecraftServer server, ServerPlayer player, String msg) {
        try {
            int seconds = Integer.parseInt(msg);
            if (seconds < 1) {
                broadcast(server, "§c时间必须大于0，请重新输入");
                return;
            }
            swapIntervalSeconds = seconds;
            state = GameState.WAITING_INVITE;
            broadcast(server, "§a互换时间设置为：每" + seconds + "秒互换一次位置");
            broadcast(server, "§e请输入你要邀请的玩家名字：");
        } catch (NumberFormatException e) {
            broadcast(server, "§c请输入有效的数字");
        }
    }

    private void handleInviteInput(MinecraftServer server, ServerPlayer player, String rawMsg) {
        // Player name is case-sensitive, don't lowercase
        String targetName = rawMsg;
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            broadcast(server, "§c找不到玩家：" + targetName + "，请重新输入");
            return;
        }
        if (target.getUUID().equals(player.getUUID())) {
            broadcast(server, "§c不能邀请自己！请重新输入");
            return;
        }

        inviteeUUID = target.getUUID();
        inviteeName = targetName;
        state = GameState.WAITING_INVEE_CONFIRM;

        String inviterName = player.getName().getString();
        // Send invite to the target player
        target.sendSystemMessage(Component.literal("§6§l=== 剥蒜的情谊 ==="));
        target.sendSystemMessage(Component.literal("§e玩家 §a" + inviterName + " §e邀请你参加游戏！"));
        target.sendSystemMessage(Component.literal("§e互换时间：每" + swapIntervalSeconds + "秒互换一次位置"));
        target.sendSystemMessage(Component.literal("§e游戏规则：在互换前想办法用环境把对方杀死，对方死亡则你获胜！"));
        target.sendSystemMessage(Component.literal("§e输入 §a§ly §e接受，§c§ln §e拒绝"));

        broadcast(server, "§a已向 " + targetName + " 发送邀请，等待对方确认...");
    }

    private void handleHunterFreezeInput(MinecraftServer server, ServerPlayer player, String msg) {
        try {
            int seconds = Integer.parseInt(msg);
            if (seconds < 1) {
                broadcast(server, "§c时间必须大于0，请重新输入");
                return;
            }
            hunterFreezeSeconds = seconds;
            state = GameState.WAITING_COMPASS_INTERVAL;
            broadcast(server, "§a猎人冻结时间设置为：" + seconds + "秒");
            broadcast(server, "§e请输入指南针更新间隔（秒，默认10）：");
        } catch (NumberFormatException e) {
            broadcast(server, "§c请输入有效的数字");
        }
    }

    private void handleCompassIntervalInput(MinecraftServer server, ServerPlayer player, String msg) {
        try {
            int seconds = Integer.parseInt(msg);
            if (seconds < 1) {
                broadcast(server, "§c时间必须大于0，请重新输入");
                return;
            }
            compassUpdateSeconds = seconds;
            state = GameState.WAITING_JOIN;
            joinTimer = 60 * 20; // 60 seconds to join
            broadcast(server, "§a指南针更新间隔设置为：每" + seconds + "秒更新一次");
            broadcast(server, "§e所有玩家有60秒时间输入 §a§ly §e加入游戏！");
            broadcast(server, "§e发起者 §a" + player.getName().getString() + " §e为猎物，其他加入者为猎人！");
        } catch (NumberFormatException e) {
            broadcast(server, "§c请输入有效的数字");
        }
    }

    private void handleHunterJoin(MinecraftServer server, ServerPlayer player, String msg) {
        if (msg.equals("y")) {
            if (!hunterGamePlayers.contains(player.getUUID())) {
                hunterGamePlayers.add(player.getUUID());
                broadcast(server, "§a" + player.getName().getString() + " 加入了猎人游戏！");
            }
        }
    }

    private void handleBrotherlyInvite(MinecraftServer server, ServerPlayer player, String rawMsg) {
        // Only host can invite
        if (!player.getUUID().equals(brotherlyHostUUID)) return;

        if (rawMsg.equalsIgnoreCase("done")) {
            if (brotherlyPendingInvites.isEmpty()) {
                broadcast(server, "§c至少需要邀请一名玩家！");
                return;
            }
            state = GameState.WAITING_BROTHERLY_ACCEPT;
            broadcast(server, "§a邀请完成！等待所有玩家接受邀请...");
            for (UUID uuid : brotherlyPendingInvites) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.sendSystemMessage(Component.literal("§6§l=== 情同手足 ==="));
                    p.sendSystemMessage(Component.literal("§e你被邀请参加情同手足游戏！"));
                    p.sendSystemMessage(Component.literal("§e所有玩家共享物品栏、生命值、饥饿值！"));
                    p.sendSystemMessage(Component.literal("§e杀死末影龙并回到主世界则共同胜利！"));
                    p.sendSystemMessage(Component.literal("§e输入 §a§ly §e接受，§c§ln §e拒绝"));
                }
            }
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(rawMsg);
        if (target == null) {
            broadcast(server, "§c找不到玩家：" + rawMsg + "，请重新输入");
            return;
        }
        if (target.getUUID().equals(player.getUUID())) {
            broadcast(server, "§c不能邀请自己！");
            return;
        }
        if (brotherlyPendingInvites.contains(target.getUUID())) {
            broadcast(server, "§c该玩家已被邀请！");
            return;
        }

        brotherlyPendingInvites.add(target.getUUID());
        broadcast(server, "§a已邀请 " + target.getName().getString() + "！（当前邀请" + brotherlyPendingInvites.size() + "人，输入 done 结束邀请）");
    }

    private void handleBrotherlyAccept(MinecraftServer server, ServerPlayer player, String msg) {
        if (!brotherlyPendingInvites.contains(player.getUUID())) return;

        if (msg.equals("y")) {
            brotherlyPlayers.add(player.getUUID());
            brotherlyPendingInvites.remove(player.getUUID());
            broadcast(server, "§a" + player.getName().getString() + " 接受了邀请！");

            if (brotherlyPendingInvites.isEmpty()) {
                startBrotherlyGame(server);
            }
        } else if (msg.equals("n")) {
            brotherlyPendingInvites.remove(player.getUUID());
            broadcast(server, "§c" + player.getName().getString() + " 拒绝了邀请，游戏取消");
            cancelStart(server);
        }
    }

    private void startBrotherlyGame(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        BlockPos spawnPos = overworld.getSharedSpawnPos();
        BlockPos gamePos = findHunterGameLocation(overworld, spawnPos);

        for (UUID uuid : brotherlyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                clearInventory(p);
                p.teleportTo(overworld,
                        gamePos.getX() + 0.5 + (Math.random() - 0.5) * 5,
                        gamePos.getY() + 1.0,
                        gamePos.getZ() + 0.5 + (Math.random() - 0.5) * 5,
                        p.getYRot(), p.getXRot());
            }
        }

        state = GameState.RUNNING;
        tickCounter = 0;
        broadcast(server, "§6§l[情同手足] §e游戏开始！所有玩家共享物品栏、生命值、饥饿值，杀龙回家共同胜利！");
    }

    private void syncBrotherlyState(MinecraftServer server) {
        ServerPlayer host = server.getPlayerList().getPlayer(brotherlyHostUUID);
        if (host == null) return;

        for (UUID uuid : brotherlyPlayers) {
            if (uuid.equals(brotherlyHostUUID)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;

            // Sync health and hunger
            p.setHealth(host.getHealth());
            p.getFoodData().setFoodLevel(host.getFoodData().getFoodLevel());
            p.getFoodData().setSaturation(host.getFoodData().getSaturationLevel());

            // Sync inventory
            for (int i = 0; i < host.getInventory().getContainerSize(); i++) {
                ItemStack hostStack = host.getInventory().getItem(i);
                ItemStack copy = hostStack.copy();
                p.getInventory().setItem(i, copy);
            }

            // Sync armor
            for (int i = 0; i < host.getInventory().armor.size(); i++) {
                ItemStack hostStack = host.getInventory().armor.get(i);
                ItemStack copy = hostStack.copy();
                p.getInventory().armor.set(i, copy);
            }

            // Sync offhand
            for (int i = 0; i < host.getInventory().offhand.size(); i++) {
                ItemStack hostStack = host.getInventory().offhand.get(i);
                ItemStack copy = hostStack.copy();
                p.getInventory().offhand.set(i, copy);
            }
        }
    }

    private void checkBrotherlyWin(MinecraftServer server) {
        // Track if any player has been to the_end and returned to overworld
        for (UUID uuid : brotherlyPlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;

            String dim = p.serverLevel().dimension().location().toString();
            if ("minecraft:overworld".equals(dim) && brotherlyVisitedEnd.contains(uuid)) {
                brotherlyGameWin(server);
                return;
            } else if ("minecraft:the_end".equals(dim)) {
                brotherlyVisitedEnd.add(uuid);
            }
        }
    }

    private void brotherlyGameWin(MinecraftServer server) {
        state = GameState.FINISHED;
        winner = brotherlyHostUUID; // Representative winner
        broadcast(server, "§6§l[情同手足] §a§l所有玩家共同获胜！成功杀死末影龙并回到主世界！");

        // Celebrate with all players
        ServerPlayer host = server.getPlayerList().getPlayer(brotherlyHostUUID);
        if (host != null) {
            startWinnerCelebration(server, host);
        }
    }


    private void handleInviteeConfirm(MinecraftServer server, ServerPlayer player, String msg) {
        // Only the invitee can confirm
        if (!player.getUUID().equals(inviteeUUID)) {
            return;
        }

        if (msg.equals("y")) {
            broadcast(server, "§a" + inviteeName + " 接受了邀请！");
            // Teleport both players to a random location together
            ServerPlayer inviter = server.getPlayerList().getPlayer(inviterUUID);
            ServerPlayer invitee = server.getPlayerList().getPlayer(inviteeUUID);
            if (inviter != null && invitee != null) {
                teleportBothSeparated(inviter, invitee);
            }
            startCountdown(server);
        } else if (msg.equals("n")) {
            broadcast(server, "§c" + inviteeName + " 拒绝了邀请，游戏取消");
            cancelStart(server);
        }
    }

    private void teleportBothSeparated(ServerPlayer p1, ServerPlayer p2) {
        ServerLevel level = p1.serverLevel();
        BlockPos spawnPos = level.getSharedSpawnPos();

        // Find safe location for player 1
        BlockPos pos1 = findSafeLocation(level, spawnPos, 30);
        // Find safe location for player 2, at least 1000 blocks away from pos1
        BlockPos pos2 = findSafeLocationFar(level, spawnPos, pos1, 1000, 50);

        if (pos1 != null) {
            p1.teleportTo(level, pos1.getX() + 0.5, pos1.getY() + 1.0, pos1.getZ() + 0.5, 0, 0);
        }
        if (pos2 != null) {
            p2.teleportTo(level, pos2.getX() + 0.5, pos2.getY() + 1.0, pos2.getZ() + 0.5, 180, 0);
        }
    }

    private BlockPos findSafeLocation(ServerLevel level, BlockPos center, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int dx = (int) ((Math.random() - 0.5) * 6000);
            int dz = (int) ((Math.random() - 0.5) * 6000);
            int targetX = center.getX() + dx;
            int targetZ = center.getZ() + dz;

            BlockPos safe = checkLocationSafety(level, targetX, targetZ);
            if (safe != null) return safe;
        }
        return null;
    }

    private BlockPos findSafeLocationFar(ServerLevel level, BlockPos center, BlockPos awayFrom, int minDistance, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int dx = (int) ((Math.random() - 0.5) * 6000);
            int dz = (int) ((Math.random() - 0.5) * 6000);
            int targetX = center.getX() + dx;
            int targetZ = center.getZ() + dz;

            // Check distance from awayFrom
            double dist = Math.sqrt(Math.pow(targetX - awayFrom.getX(), 2) + Math.pow(targetZ - awayFrom.getZ(), 2));
            if (dist < minDistance) continue;

            BlockPos safe = checkLocationSafety(level, targetX, targetZ);
            if (safe != null) return safe;
        }
        return null;
    }

    private BlockPos checkLocationSafety(ServerLevel level, int targetX, int targetZ) {
        int targetY = -1;
        for (int y = 319; y >= 10; y--) {
            BlockPos checkPos = new BlockPos(targetX, y, targetZ);
            BlockState bs = level.getBlockState(checkPos);
            if (!bs.isAir() && bs.getFluidState().isEmpty()) {
                targetY = y;
                break;
            }
        }

        if (targetY <= 10) return null;

        BlockPos landingPos = new BlockPos(targetX, targetY, targetZ);
        BlockState landingBlock = level.getBlockState(landingPos);
        BlockState feetBlock = level.getBlockState(landingPos.above());
        BlockState headBlock = level.getBlockState(landingPos.above(2));

        boolean safe = !landingBlock.is(Blocks.LAVA)
                && !landingBlock.is(Blocks.FIRE)
                && !feetBlock.is(Blocks.LAVA)
                && !headBlock.is(Blocks.LAVA)
                && feetBlock.isAir()
                && headBlock.isAir()
                && targetY > 10 && targetY < 300;

        return safe ? landingPos : null;
    }

    private void cancelStart(MinecraftServer server) {
        broadcast(server, "§c已取消开始游戏");
        reset();
    }

    private void startCountdown(MinecraftServer server) {
        state = GameState.COUNTDOWN;
        countdownTimer = 3 * 20; // 3 seconds
        broadcast(server, "§e游戏即将开始...");
    }

    private void actuallyStartGame(MinecraftServer server) {
        state = GameState.RUNNING;
        winner = null;
        tickCounter = 0;
        playerLives.clear();

        if ("deathrace".equals(currentGame)) {
            // Clear inventories and add all players
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                clearInventory(player);
                playerLives.put(player.getUUID(), maxLives);
            }
            createScoreboard(server);
            broadcast(server, "§6§l[死亡竞速] §e游戏开始！每人" + maxLives + "条命，最先死完的人获胜！");
        } else if ("brotherhood".equals(currentGame)) {
            // Clear inventories for both players
            ServerPlayer inviter = server.getPlayerList().getPlayer(inviterUUID);
            ServerPlayer invitee = server.getPlayerList().getPlayer(inviteeUUID);
            if (inviter != null) clearInventory(inviter);
            if (invitee != null) clearInventory(invitee);
            broadcast(server, "§6§l[剥蒜的情谊] §e游戏开始！每" + swapIntervalSeconds + "秒互换位置，想办法用环境杀死对方！");
        }
    }

    private void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().armor.clear();
        player.getInventory().offhand.clear();
    }

    public void stopGame(MinecraftServer server) {
        if (state == GameState.IDLE) {
            broadcast(server, "§c当前没有游戏正在进行");
            return;
        }
        removeScoreboard(server);
        broadcast(server, "§c[小游戏] 游戏已停止");
        reset();
    }

    private void reset() {
        state = GameState.IDLE;
        currentGame = null;
        winner = null;
        tickCounter = 0;
        countdownTimer = 0;
        fireworkTimer = 0;
        maxLives = 64;
        regenIntervalSeconds = 60;
        swapIntervalSeconds = 60;
        inviterUUID = null;
        inviteeUUID = null;
        inviteeName = null;
        hunterGamePlayers.clear();
        preyUUID = null;
        hunterUUIDs.clear();
        joinTimer = 0;
        freezeTimer = 0;
        hunterFreezeSeconds = 30;
        compassUpdateSeconds = 10;
        preyLastOverworldPos = null;
        preyDimension = "minecraft:overworld";
        brotherlyPlayers.clear();
        brotherlyHostUUID = null;
        brotherlyPendingInvites.clear();
        brotherlyVisitedEnd.clear();
        winnerPlatformPos = null;
        playerLives.clear();
    }

    // === Death Handling ===

    public boolean onPlayerAboutToDie(ServerPlayer player) {
        if (state != GameState.RUNNING || winner != null) {
            return false;
        }

        if ("deathrace".equals(currentGame)) {
            return handleDeathraceDeath(player);
        } else if ("brotherhood".equals(currentGame)) {
            return handleBrotherhoodDeath(player);
        } else if ("huntergame".equals(currentGame)) {
            return handleHunterGameDeath(player);
        }
        return false;
    }

    private boolean handleHunterGameDeath(ServerPlayer player) {
        UUID deadUUID = player.getUUID();

        if (deadUUID.equals(preyUUID)) {
            // Prey died, hunters win
            winner = hunterUUIDs.iterator().next(); // First hunter as representative
            state = GameState.FINISHED;
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            broadcast(player.server, "§6§l[猎人游戏] §c猎物 " + player.getName().getString() + " 死亡！§a猎人们获胜了！");
            ServerPlayer winnerPlayer = player.server.getPlayerList().getPlayer(winner);
            if (winnerPlayer != null) {
                startWinnerCelebration(player.server, winnerPlayer);
            }
            return true;
        } else if (hunterUUIDs.contains(deadUUID)) {
            // Hunter died, revive and give compass
            player.setHealth(player.getMaxHealth());
            player.clearFire();
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
            // Give compass after respawn
            giveHunterCompass(player);
            broadcast(player.server, "§e猎人 " + player.getName().getString() + " 死亡，已复活并获得指南针！");
            return true;
        }
        return false;
    }

    private boolean handleDeathraceDeath(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!playerLives.containsKey(uuid)) {
            return false;
        }

        int lives = playerLives.get(uuid);

        if (lives > 0) {
            playerLives.put(uuid, lives - 1);
            triggerTotemEffect(player);
            updateScoreboard(player.server);
            return true;
        } else {
            // Player wins! Cancel death so player stays alive
            winner = uuid;
            String winnerName = player.getName().getString();
            removeScoreboard(player.server);
            state = GameState.FINISHED;

            player.setHealth(player.getMaxHealth());
            player.clearFire();

            broadcast(player.server, "§6§l[死亡竞速] §a§l" + winnerName + " §e§l获胜了！");
            startWinnerCelebration(player.server, player);
            return true;
        }
    }

    private boolean handleBrotherhoodDeath(ServerPlayer player) {
        UUID deadUUID = player.getUUID();
        UUID winnerUUID = null;
        String winnerName = null;

        // The other player wins
        if (deadUUID.equals(inviterUUID)) {
            winnerUUID = inviteeUUID;
            winnerName = inviteeName;
        } else if (deadUUID.equals(inviteeUUID)) {
            winnerUUID = inviterUUID;
            ServerPlayer inviter = player.server.getPlayerList().getPlayer(inviterUUID);
            winnerName = inviter != null ? inviter.getName().getString() : "玩家";
        } else {
            return false; // Not a participant
        }

        winner = winnerUUID;
        state = GameState.FINISHED;

        // Heal the dead player so they don't actually die
        player.setHealth(player.getMaxHealth());
        player.clearFire();

        broadcast(player.server, "§6§l[剥蒜的情谊] §a§l" + winnerName + " §e§l获胜了！");

        ServerPlayer winnerPlayer = player.server.getPlayerList().getPlayer(winnerUUID);
        if (winnerPlayer != null) {
            startWinnerCelebration(player.server, winnerPlayer);
        }
        return true;
    }

    // === Winner Celebration ===

    private void startWinnerCelebration(MinecraftServer server, ServerPlayer winnerPlayer) {
        ServerLevel level = server.overworld();
        BlockPos spawnPos = level.getSharedSpawnPos();
        BlockPos basePos = new BlockPos(spawnPos.getX(), 200, spawnPos.getZ());

        // Load structure from data/foundation/structures/winner_podium.nbt
        Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> templateOpt =
                level.getStructureManager().get(new net.minecraft.resources.ResourceLocation("foundation", "winner_podium"));

        if (templateOpt.isPresent()) {
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = templateOpt.get();
            net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings settings =
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setIgnoreEntities(true);
            template.placeInWorld(level, basePos, basePos, settings, level.getRandom(), 3);

            // Get structure size
            net.minecraft.core.Vec3i size = template.getSize();
            int structureHeight = size.getY();
            int centerX = basePos.getX() + size.getX() / 2;
            int centerZ = basePos.getZ() + size.getZ() / 2;

            winnerPlatformPos = new BlockPos(centerX, basePos.getY(), centerZ);

            // Teleport winner to the top of structure
            winnerPlayer.teleportTo(level,
                    centerX + 0.5,
                    basePos.getY() + structureHeight + 0.5,
                    centerZ + 0.5,
                    winnerPlayer.getYRot(), winnerPlayer.getXRot());

            // Teleport other players to the base of structure
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != winnerPlayer) {
                    p.teleportTo(level,
                            centerX + 0.5 + (Math.random() - 0.5) * 4,
                            basePos.getY() + 1,
                            centerZ + 0.5 + (Math.random() - 0.5) * 4,
                            p.getYRot(), p.getXRot());
                }
            }
        } else {
            // Fallback: simple platform if structure not found
            winnerPlatformPos = basePos;
            buildWinnerPlatform(level, basePos);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.teleportTo(level, basePos.getX() + 0.5, basePos.getY() + 1, basePos.getZ() + 0.5,
                        p.getYRot(), p.getXRot());
            }
        }

        // Show title to winner
        winnerPlayer.sendSystemMessage(Component.literal("§6§l恭喜获胜！"), true);
        String subtitle = switch (currentGame) {
            case "deathrace" -> "§e你是第一个死完的人！";
            case "brotherhood" -> "§e你成功杀死了对手！";
            case "huntergame" -> "§e恭喜获得游戏胜利！";
            case "brotherlylove" -> "§e所有玩家共同获胜！";
            default -> "§e恭喜获胜！";
        };
        executeTitleCommand(server, winnerPlayer.getName().getString(), "§6§l恭喜获胜！", subtitle);

        // Start fireworks at winner position
        fireworkTimer = 10 * 20; // 10 seconds of fireworks
    }

    private void buildWinnerPlatform(ServerLevel level, BlockPos center) {
        int diamondRadius = 12; // Outer diamond ring expanded to 25x25
        // Build base platform with layers: diamond center -> emerald -> gold -> diamond outer
        for (int dx = -diamondRadius; dx <= diamondRadius; dx++) {
            for (int dz = -diamondRadius; dz <= diamondRadius; dz++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState block;
                if (dist == 0) {
                    block = Blocks.DIAMOND_BLOCK.defaultBlockState();   // Center: diamond
                } else if (dist <= 1) {
                    block = Blocks.EMERALD_BLOCK.defaultBlockState();   // 3x3 ring: emerald
                } else if (dist <= 2) {
                    block = Blocks.GOLD_BLOCK.defaultBlockState();      // 5x5 ring: gold
                } else {
                    block = Blocks.DIAMOND_BLOCK.defaultBlockState();   // Expanded outer ring: diamond
                }
                level.setBlock(pos, block, 3);
            }
        }
        // Center structure above diamond:
        // y=1 center: empty (air)
        // y=1 left/right: gold blocks
        level.setBlock(center.offset(-1, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(center.offset(1, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        // y=2 and y=3 center: gold pillar (two blocks above the empty space)
        level.setBlock(center.offset(0, 2, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(center.offset(0, 3, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
    }

    private void spawnFirework(ServerLevel level, double x, double y, double z) {
        ItemStack fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag tag = fireworkStack.getOrCreateTag();
        CompoundTag fireworksTag = new CompoundTag();
        fireworksTag.putByte("Flight", (byte) 1);

        ListTag explosionsTag = new ListTag();
        CompoundTag explosionTag = new CompoundTag();
        explosionTag.putByte("Type", (byte) 4); // Large ball
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

    private void executeTitleCommand(MinecraftServer server, String playerName, String title, String subtitle) {
        try {
            // Set title display times: fadeIn 10 ticks, stay 20 ticks (1s), fadeOut 10 ticks
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title " + playerName + " times 10 20 10");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title " + playerName + " title {\"text\":\"" + title + "\"}");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title " + playerName + " subtitle {\"text\":\"" + subtitle + "\"}");
        } catch (Exception e) {
            // Ignore
        }
    }

    private void executeTitleAll(MinecraftServer server, String text) {
        try {
            // Set title display times: fadeIn 10 ticks, stay 20 ticks (1s), fadeOut 10 ticks
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a times 10 20 10");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "title @a title {\"text\":\"" + text + "\",\"color\":\"gold\",\"bold\":true}");
        } catch (Exception e) {
            // Ignore
        }
    }

    // === Totem Effect ===

    private void triggerTotemEffect(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1)); // 10 seconds
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0)); // 30 seconds

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);

        for (int i = 0; i < 32; i++) {
            double angle = (i / 32.0) * Math.PI * 2;
            double x = player.getX() + Math.cos(angle) * 0.5;
            double y = player.getY() + 1.0 + (i % 3) * 0.3;
            double z = player.getZ() + Math.sin(angle) * 0.5;
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Random teleport after revival
        randomTeleport(player);
    }

    private void randomTeleport(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos currentPos = player.blockPosition();

        for (int attempt = 0; attempt < 20; attempt++) {
            // Random offset within ±2000 blocks
            int dx = (int) ((Math.random() - 0.5) * 4000);
            int dz = (int) ((Math.random() - 0.5) * 4000);
            int targetX = currentPos.getX() + dx;
            int targetZ = currentPos.getZ() + dz;

            // Find highest solid block at this x,z
            int targetY = -1;
            for (int y = 319; y >= 10; y--) {
                BlockPos checkPos = new BlockPos(targetX, y, targetZ);
                BlockState state = level.getBlockState(checkPos);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    targetY = y;
                    break;
                }
            }

            if (targetY <= 10) continue;

            BlockPos landingPos = new BlockPos(targetX, targetY, targetZ);
            BlockPos feetPos = landingPos.above();
            BlockPos headPos = landingPos.above(2);

            // Safety checks
            BlockState landingBlock = level.getBlockState(landingPos);
            BlockState feetBlock = level.getBlockState(feetPos);
            BlockState headBlock = level.getBlockState(headPos);

            // Not lava, not fire, has air above
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

    // === Tick ===

    public void tick(MinecraftServer server) {
        switch (state) {
            case COUNTDOWN -> tickCountdown(server);
            case RUNNING -> tickGame(server);
            case FINISHED -> tickCelebration(server);
            case WAITING_JOIN -> tickHunterJoin(server);
            case HUNTER_FREEZE -> tickHunterFreeze(server);
            default -> {}
        }
    }

    private void tickCountdown(MinecraftServer server) {
        countdownTimer--;
        int second = (countdownTimer / 20) + 1;
        if (countdownTimer % 20 == 0 && second > 0) {
            executeTitleAll(server, String.valueOf(second));
            broadcast(server, "§e" + second + "...");
        }
        if (countdownTimer <= 0) {
            executeTitleAll(server, "开始！");
            actuallyStartGame(server);
        }
    }

    private void tickGame(MinecraftServer server) {
        tickCounter++;

        if ("deathrace".equals(currentGame)) {
            int regenTicks = regenIntervalSeconds * 20;
            if (tickCounter % regenTicks == 0) {
                for (Map.Entry<UUID, Integer> entry : playerLives.entrySet()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        int lives = entry.getValue();
                        if (lives < maxLives) {
                            entry.setValue(lives + 1);
                            player.sendSystemMessage(Component.literal("§a[死亡竞速] 恢复了1条命，当前" + (lives + 1) + "条命"));
                        }
                    }
                }
                updateScoreboard(server);
            }
        } else if ("brotherhood".equals(currentGame)) {
            int swapTicks = swapIntervalSeconds * 20;
            if (tickCounter % swapTicks == 0) {
                swapPlayers(server);
            }
        } else if ("huntergame".equals(currentGame)) {
            // Update compasses at configured interval
            int compassTicks = compassUpdateSeconds * 20;
            if (tickCounter % compassTicks == 0) {
                updateHunterCompasses(server);
            }
            // Check prey win condition (killed dragon and returned to overworld)
            checkPreyWinCondition(server);
        } else if ("brotherlylove".equals(currentGame)) {
            // Sync shared state every tick
            syncBrotherlyState(server);
            // Check win condition
            checkBrotherlyWin(server);
        }
    }

    private void checkPreyWinCondition(MinecraftServer server) {
        ServerPlayer prey = server.getPlayerList().getPlayer(preyUUID);
        if (prey == null) return;

        String dim = prey.serverLevel().dimension().location().toString();
        // Prey wins if they are in overworld and have killed the dragon
        // Simple check: if prey was in the_end and is now in overworld
        if ("minecraft:overworld".equals(dim) && "minecraft:the_end".equals(preyDimension)) {
            // Prey returned from the_end to overworld (likely killed dragon)
            hunterGamePreyWins(server, prey);
        }
    }

    private void hunterGamePreyWins(MinecraftServer server, ServerPlayer prey) {
        winner = preyUUID;
        state = GameState.FINISHED;
        broadcast(server, "§6§l[猎人游戏] §a§l" + prey.getName().getString() + " §e§l（猎物）获胜了！成功杀龙并返回主世界！");
        startWinnerCelebration(server, prey);
    }

    private void tickHunterJoin(MinecraftServer server) {
        joinTimer--;
        int secondsLeft = (joinTimer / 20) + 1;

        if (joinTimer % 20 == 0 && secondsLeft > 0 && (secondsLeft <= 10 || secondsLeft % 10 == 0)) {
            broadcast(server, "§e猎人游戏还有 " + secondsLeft + " 秒开始，输入 §a§ly §e加入！");
        }

        if (joinTimer <= 0) {
            startHunterGame(server);
        }
    }

    private void startHunterGame(MinecraftServer server) {
        // Set up hunters (everyone except prey)
        hunterUUIDs.clear();
        for (UUID uuid : hunterGamePlayers) {
            if (!uuid.equals(preyUUID)) {
                hunterUUIDs.add(uuid);
            }
        }

        if (hunterUUIDs.isEmpty()) {
            broadcast(server, "§c没有猎人加入，游戏取消！");
            cancelStart(server);
            return;
        }

        // Teleport all players to a random location within 500 blocks of spawn
        ServerLevel overworld = server.overworld();
        BlockPos spawnPos = overworld.getSharedSpawnPos();
        BlockPos gamePos = findHunterGameLocation(overworld, spawnPos);

        for (UUID uuid : hunterGamePlayers) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                clearInventory(p);
                p.teleportTo(overworld,
                        gamePos.getX() + 0.5 + (Math.random() - 0.5) * 10,
                        gamePos.getY() + 1.0,
                        gamePos.getZ() + 0.5 + (Math.random() - 0.5) * 10,
                        p.getYRot(), p.getXRot());
            }
        }

        // Give hunters a compass
        for (UUID uuid : hunterUUIDs) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                giveHunterCompass(p);
            }
        }

        // Initialize prey tracking
        ServerPlayer prey = server.getPlayerList().getPlayer(preyUUID);
        if (prey != null) {
            preyLastOverworldPos = prey.blockPosition();
            preyDimension = prey.serverLevel().dimension().location().toString();
        }

        // Start freeze phase
        state = GameState.HUNTER_FREEZE;
        freezeTimer = hunterFreezeSeconds * 20;
        broadcast(server, "§6§l[猎人游戏] §e游戏开始！猎人们冻结" + hunterFreezeSeconds + "秒，猎物快跑！");
    }

    private BlockPos findHunterGameLocation(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = (int) ((Math.random() - 0.5) * 1000);
            int dz = (int) ((Math.random() - 0.5) * 1000);
            int targetX = center.getX() + dx;
            int targetZ = center.getZ() + dz;

            BlockPos safe = checkLocationSafety(level, targetX, targetZ);
            if (safe != null) return safe;
        }
        return center;
    }

    private void tickHunterFreeze(MinecraftServer server) {
        freezeTimer--;
        int secondsLeft = (freezeTimer / 20) + 1;

        // Freeze hunters by teleporting them back to their position
        for (UUID uuid : hunterUUIDs) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                // Store and restore position to prevent movement
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            }
        }

        if (freezeTimer % 20 == 0 && secondsLeft > 0 && secondsLeft <= 10) {
            broadcast(server, "§e猎人还有 " + secondsLeft + " 秒解冻！");
        }

        if (freezeTimer <= 0) {
            state = GameState.RUNNING;
            tickCounter = 0;
            broadcast(server, "§6§l[猎人游戏] §c猎人们解冻了！开始追杀！");
        }
    }

    private void giveHunterCompass(ServerPlayer player) {
        ItemStack compass = new ItemStack(Items.COMPASS);
        CompoundTag tag = compass.getOrCreateTag();
        tag.putBoolean("LodestoneTracked", false);
        player.getInventory().add(compass);
    }

    private void updateHunterCompasses(MinecraftServer server) {
        ServerPlayer prey = server.getPlayerList().getPlayer(preyUUID);
        if (prey == null) return;

        String preyDim = prey.serverLevel().dimension().location().toString();
        BlockPos preyPos = prey.blockPosition();

        // Update last overworld position
        if ("minecraft:overworld".equals(preyDim)) {
            preyLastOverworldPos = preyPos;
        }
        preyDimension = preyDim;

        // Determine compass target
        BlockPos targetPos;
        String targetDim;
        if ("minecraft:overworld".equals(preyDim)) {
            targetPos = preyPos;
            targetDim = preyDim;
        } else {
            // Prey is in another dimension, point to last overworld position
            targetPos = preyLastOverworldPos != null ? preyLastOverworldPos : preyPos;
            targetDim = "minecraft:overworld";
        }

        // Update all hunters' compasses
        for (UUID uuid : hunterUUIDs) {
            ServerPlayer hunter = server.getPlayerList().getPlayer(uuid);
            if (hunter == null) continue;

            // Check if hunter is within 20 blocks of prey (same dimension)
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
                        // Compass disabled within 20 blocks - remove lodestone data
                        tag.remove("LodestonePos");
                        tag.remove("LodestoneDimension");
                        tag.remove("LodestoneTracked");
                    } else {
                        // Update compass to point at prey
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

    private void swapPlayers(MinecraftServer server) {
        ServerPlayer p1 = server.getPlayerList().getPlayer(inviterUUID);
        ServerPlayer p2 = server.getPlayerList().getPlayer(inviteeUUID);
        if (p1 == null || p2 == null) return;

        // Swap positions
        double x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
        float yaw1 = p1.getYRot(), pitch1 = p1.getXRot();
        double x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
        float yaw2 = p2.getYRot(), pitch2 = p2.getXRot();

        p1.teleportTo(p1.serverLevel(), x2, y2, z2, yaw2, pitch2);
        p2.teleportTo(p2.serverLevel(), x1, y1, z1, yaw1, pitch1);

        broadcast(server, "§e[剥蒜的情谊] 位置互换！");
    }

    private void tickCelebration(MinecraftServer server) {
        if (fireworkTimer > 0 && winner != null) {
            fireworkTimer--;
            if (fireworkTimer % 10 == 0) {
                ServerPlayer winnerPlayer = server.getPlayerList().getPlayer(winner);
                if (winnerPlayer != null) {
                    // Spawn fireworks around winner
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double radius = 1 + Math.random() * 2;
                        spawnFirework(server.overworld(),
                                winnerPlayer.getX() + Math.cos(angle) * radius,
                                winnerPlayer.getY() + 1,
                                winnerPlayer.getZ() + Math.sin(angle) * radius);
                    }
                }
            }
        } else if (fireworkTimer <= 0 && state == GameState.FINISHED) {
            // Celebration ended, reset game state so a new game can start
            broadcast(server, "§e[小游戏] 游戏结束，可以开始新游戏了");
            reset();
        }
    }

    // === Scoreboard ===

    private void createScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) scoreboard.removeObjective(existing);

        var objective = scoreboard.addObjective(
                OBJECTIVE_NAME,
                net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                Component.literal("§6§l死亡竞速 - 剩余命数"),
                net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);

        scoreboard.setDisplayObjective(1, objective);

        for (Map.Entry<UUID, Integer> entry : playerLives.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(entry.getValue());
            }
        }
    }

    private void updateScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) return;

        for (Map.Entry<UUID, Integer> entry : playerLives.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(entry.getValue());
            }
        }
    }

    private void removeScoreboard(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) scoreboard.removeObjective(objective);
    }

    // === Utils ===

    private void broadcast(MinecraftServer server, String message) {
        Component msg = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    public List<String> getAvailableGames() {
        return List.of("deathrace", "brotherhood", "huntergame", "brotherlylove");
    }
}
