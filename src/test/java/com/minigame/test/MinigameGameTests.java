package com.minigame.test;

import com.minigame.game.GameManager;
import com.minigame.utils.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.UUID;

/**
 * Minigame mod functional tests.
 * Run with: /test runall minigame
 * Run single: /test run minigame.testName
 */
public class MinigameGameTests {

    // ==================== GameManager State Tests ====================

    @GameTest
    public void testGameManagerInitialState(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;
        context.assertTrue(!manager.isGameRunning(), "Initial state should have no running game");
        context.assertTrue(!manager.isGameActive(), "Initial state should have no active game");
        context.assertTrue(manager.getCurrentGame() == null, "Current game should be null initially");
    }

    @GameTest
    public void testAvailableGamesList(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;
        var games = manager.getAvailableGames();
        context.assertTrue(games.contains("deathrace"), "Should contain deathrace");
        context.assertTrue(games.contains("brotherhood"), "Should contain brotherhood");
        context.assertTrue(games.contains("huntergame"), "Should contain huntergame");
        context.assertTrue(games.contains("brotherlylove"), "Should contain brotherlylove");
        context.assertTrue(games.contains("colorparty"), "Should contain colorparty");
        context.assertTrue(games.size() == 5, "Should have 5 games, got: " + games.size());
    }

    // ==================== Utility Tests ====================

    @GameTest
    public void testBlockColorName(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos pos = new BlockPos(0, 0, 0);

        // Test grass block
        context.setBlock(pos, Blocks.GRASS_BLOCK);
        BlockState grassState = level.getBlockState(pos);
        String grassColor = GameUtils.getBlockColorName(grassState, level, pos);
        context.assertTrue(grassColor != null && !grassColor.equals("unknown"), "Grass color should not be unknown, got: " + grassColor);

        // Test stone
        context.setBlock(pos, Blocks.STONE);
        BlockState stoneState = level.getBlockState(pos);
        String stoneColor = GameUtils.getBlockColorName(stoneState, level, pos);
        context.assertTrue(stoneColor != null && !stoneColor.equals("unknown"), "Stone color should not be unknown, got: " + stoneColor);

        // Test diamond block
        context.setBlock(pos, Blocks.DIAMOND_BLOCK);
        BlockState diamondState = level.getBlockState(pos);
        String diamondColor = GameUtils.getBlockColorName(diamondState, level, pos);
        context.assertTrue(diamondColor != null && !diamondColor.equals("unknown"), "Diamond color should not be unknown, got: " + diamondColor);

        // Test gold block
        context.setBlock(pos, Blocks.GOLD_BLOCK);
        BlockState goldState = level.getBlockState(pos);
        String goldColor = GameUtils.getBlockColorName(goldState, level, pos);
        context.assertTrue(goldColor != null && !goldColor.equals("unknown"), "Gold color should not be unknown, got: " + goldColor);
    }

    @GameTest
    public void testRandomColor(GameTestHelper context) {
        for (int i = 0; i < 10; i++) {
            String color = GameUtils.getRandomColor();
            context.assertTrue(color != null && !color.isEmpty(), "Random color should not be empty");
            context.assertTrue(!color.equals("unknown"), "Random color should not be unknown");
        }
    }

    @GameTest
    public void testSafeLocationFinding(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos center = new BlockPos(0, 64, 0);

        BlockPos safePos = GameUtils.findSafeLocation(level, center, 10);
        context.assertTrue(safePos != null, "Should find a safe location");

        BlockState landingBlock = level.getBlockState(safePos);
        context.assertTrue(!landingBlock.is(Blocks.LAVA), "Safe location should not be lava");
        context.assertTrue(!landingBlock.is(Blocks.FIRE), "Safe location should not be fire");

        BlockPos safeCheck = GameUtils.checkLocationSafety(level, safePos.getX(), safePos.getZ());
        context.assertTrue(safeCheck != null, "Safe location check should pass");
    }

    // ==================== Game Start Flow Tests ====================

    @GameTest
    public void testRequestStartDeathRace(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;

        manager.requestStart("deathrace", context.getLevel().getServer());

        context.assertTrue(manager.isGameActive(), "Should have active game after request");
        context.assertTrue(manager.isInSetupFlow(), "Should be in setup flow");
        context.assertTrue("deathrace".equals(manager.getCurrentGame()), "Current game should be deathrace");

        manager.stopGame(context.getLevel().getServer());
    }

    @GameTest
    public void testRequestStartHunterGame(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;

        manager.requestStart("huntergame", context.getLevel().getServer());

        context.assertTrue(manager.isGameActive(), "Should have active game after request");
        context.assertTrue("huntergame".equals(manager.getCurrentGame()), "Current game should be huntergame");

        manager.stopGame(context.getLevel().getServer());
    }

    @GameTest
    public void testRequestStartColorParty(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;

        manager.requestStart("colorparty", context.getLevel().getServer());

        context.assertTrue(manager.isGameActive(), "Should have active game after request");
        context.assertTrue("colorparty".equals(manager.getCurrentGame()), "Current game should be colorparty");

        manager.stopGame(context.getLevel().getServer());
    }

    // ==================== Game Stop Tests ====================

    @GameTest
    public void testStopGame(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;

        manager.requestStart("deathrace", context.getLevel().getServer());
        context.assertTrue(manager.isGameActive(), "Should have active game after start");

        manager.stopGame(context.getLevel().getServer());
        context.assertTrue(!manager.isGameActive(), "Should have no active game after stop");
        context.assertTrue(!manager.isGameRunning(), "Should have no running game after stop");
    }

    // ==================== Public Method Tests ====================

    @GameTest
    public void testTickCounter(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;
        int initial = manager.getTickCounter();
        context.assertTrue(initial >= 0, "Tick counter should be >= 0");
    }

    @GameTest
    public void testWinnerManagement(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;

        context.assertTrue(manager.getWinner() == null, "Winner should be null initially");

        UUID testUUID = UUID.randomUUID();
        manager.setWinner(testUUID);
        context.assertTrue(testUUID.equals(manager.getWinner()), "Should be able to set and get winner");

        manager.setWinner(null);
    }

    // ==================== Block Color Mapping Tests ====================

    @GameTest
    public void testCommonBlockColors(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos pos = new BlockPos(0, 0, 0);

        BlockState[] testBlocks = {
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.SAND.defaultBlockState(),
                Blocks.GRAVEL.defaultBlockState(),
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                Blocks.GOLD_BLOCK.defaultBlockState(),
                Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.EMERALD_BLOCK.defaultBlockState(),
                Blocks.REDSTONE_BLOCK.defaultBlockState(),
                Blocks.LAPIS_BLOCK.defaultBlockState(),
                Blocks.COAL_BLOCK.defaultBlockState(),
                Blocks.WHITE_WOOL.defaultBlockState(),
                Blocks.RED_WOOL.defaultBlockState(),
                Blocks.BLUE_WOOL.defaultBlockState(),
                Blocks.GREEN_WOOL.defaultBlockState(),
                Blocks.YELLOW_WOOL.defaultBlockState(),
        };

        int successCount = 0;
        for (BlockState state : testBlocks) {
            String color = GameUtils.getBlockColorName(state, level, pos);
            if (color != null && !color.equals("unknown")) {
                successCount++;
            }
        }

        context.assertTrue(successCount >= testBlocks.length * 0.8,
                "At least 80% of common blocks should have color mapping, success: " + successCount + "/" + testBlocks.length);
    }

    // ==================== Game Class Creation Tests ====================

    @GameTest
    public void testGameClassCreation(GameTestHelper context) {
        GameManager manager = GameManager.INSTANCE;
        var server = context.getLevel().getServer();

        // Test DeathRaceGame creation
        var deathRace = new com.minigame.games.DeathRaceGame(manager, server, 64, 60);
        context.assertTrue("deathrace".equals(deathRace.getGameName()), "DeathRace game name should be deathrace");

        // Test BrotherhoodGame creation
        var uuid1 = UUID.randomUUID();
        var uuid2 = UUID.randomUUID();
        var brotherhood = new com.minigame.games.BrotherhoodGame(manager, server, uuid1, uuid2, "test", 60);
        context.assertTrue("brotherhood".equals(brotherhood.getGameName()), "Brotherhood game name should be brotherhood");

        // Test ColorPartyGame creation
        var players = new HashSet<UUID>();
        players.add(uuid1);
        var colorParty = new com.minigame.games.ColorPartyGame(manager, server, players, 0, 5, 10);
        context.assertTrue("colorparty".equals(colorParty.getGameName()), "ColorParty game name should be colorparty");
    }
}
