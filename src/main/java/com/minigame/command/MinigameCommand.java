package com.minigame.command;

import com.minigame.game.GameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MinigameCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minigame")
            .requires(source -> source.hasPermission(2))
            // List available games
            .then(Commands.literal("list")
                .executes(MinigameCommand::listGames)
            )
            // Start a game
            .then(Commands.literal("start")
                .then(Commands.argument("game", StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        for (String game : GameManager.INSTANCE.getAvailableGames()) {
                            builder.suggest(game);
                        }
                        return builder.buildFuture();
                    })
                    .executes(MinigameCommand::startGame)
                )
            )
            // Stop current game
            .then(Commands.literal("stop")
                .executes(MinigameCommand::stopGame)
            )
        );
    }

    private static int listGames(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== 可用小游戏 ==="), false);
        for (String game : GameManager.INSTANCE.getAvailableGames()) {
            String displayName = switch (game) {
                case "deathrace" -> "死亡竞速";
                case "brotherhood" -> "剥蒜的情谊";
                case "huntergame" -> "猎人游戏";
                case "brotherlylove" -> "情同手足";
                case "colorparty" -> "色盲派对";
                default -> game;
            };
            ctx.getSource().sendSuccess(() -> Component.literal("§e- " + displayName + " (" + game + ")"), false);
        }
        if (GameManager.INSTANCE.isGameRunning()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a当前游戏: " + GameManager.INSTANCE.getCurrentGame()), false);
        }
        return 1;
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        String game = StringArgumentType.getString(ctx, "game").toLowerCase();

        if (GameManager.INSTANCE.isGameActive()) {
            ctx.getSource().sendFailure(Component.literal("§c已有游戏流程正在进行中，请先停止"));
            return 0;
        }

        if (!GameManager.INSTANCE.getAvailableGames().contains(game)) {
            ctx.getSource().sendFailure(Component.literal("§c未知的小游戏: " + game));
            return 0;
        }

        GameManager.INSTANCE.requestStart(game, ctx.getSource().getServer());
        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> ctx) {
        if (!GameManager.INSTANCE.isGameActive()) {
            ctx.getSource().sendFailure(Component.literal("§c当前没有游戏正在进行"));
            return 0;
        }

        GameManager.INSTANCE.stopGame(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§a已停止游戏"), true);
        return 1;
    }
}
