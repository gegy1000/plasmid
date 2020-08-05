package net.gegy1000.plasmid.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.gegy1000.plasmid.Plasmid;
import net.gegy1000.plasmid.game.ConfiguredGame;
import net.gegy1000.plasmid.game.GameManager;
import net.gegy1000.plasmid.game.JoinResult;
import net.gegy1000.plasmid.game.StartResult;
import net.gegy1000.plasmid.game.config.GameConfigs;
import net.gegy1000.plasmid.party.Party;
import net.gegy1000.plasmid.party.PartyManager;
import net.gegy1000.plasmid.util.PlayerRef;
import net.minecraft.command.arguments.IdentifierArgumentType;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GameCommand {
    public static final DynamicCommandExceptionType GAME_NOT_FOUND = new DynamicCommandExceptionType(arg -> {
        return new TranslatableText("Game with id '%s' was not found!", arg);
    });

    public static final SimpleCommandExceptionType ALREADY_OPEN = new SimpleCommandExceptionType(
            new LiteralText("A game is already open!")
    );

    public static final SimpleCommandExceptionType NOT_RECRUITING = new SimpleCommandExceptionType(
            new LiteralText("Game not recruiting!")
    );

    // @formatter:off
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("game")
                .then(literal("open")
                    .then(argument("game_type", IdentifierArgumentType.identifier()).suggests(gameSuggestions())
                    .executes(GameCommand::openGame)
                ))
                .then(literal("join").executes(GameCommand::joinGame))
                .then(literal("start").executes(GameCommand::startGame))
                .then(literal("stop").executes(GameCommand::stopGame))
                .then(literal("list").executes(GameCommand::listGames))
        );
    }
    // @formatter:on

    private static int openGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getMinecraftServer();

        Identifier gameTypeId = IdentifierArgumentType.getIdentifier(context, "game_type");

        ConfiguredGame<?> game = GameConfigs.get(gameTypeId);
        if (game == null) {
            throw GAME_NOT_FOUND.create(gameTypeId);
        }

        GameManager.Closed closed = GameManager.closed();
        if (closed == null) {
            throw ALREADY_OPEN.create();
        }

        PlayerManager playerManager = server.getPlayerManager();
        playerManager.broadcastChatMessage(new LiteralText("Game is opening! Hold tight.."), MessageType.SYSTEM, Util.NIL_UUID);

        closed.open(server, game)
                .handle((gameAndConfig, throwable) -> {
                    if (throwable == null) {
                        onOpenSuccess(game, playerManager);
                    } else {
                        onOpenError(playerManager, throwable);
                    }
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void onOpenSuccess(ConfiguredGame<?> game, PlayerManager playerManager) {
        ClickEvent joinClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/game join");
        HoverEvent joinHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("/game join"));
        Style joinStyle = Style.EMPTY
                .withFormatting(Formatting.UNDERLINE)
                .withColor(Formatting.BLUE)
                .withClickEvent(joinClick)
                .setHoverEvent(joinHover);

        Text openMessage = new LiteralText("Game has opened! ")
                .formatted(Formatting.GRAY)
                .append(new LiteralText("Click here to join").setStyle(joinStyle));
        playerManager.broadcastChatMessage(openMessage, MessageType.SYSTEM, Util.NIL_UUID);
    }

    private static void onOpenError(PlayerManager playerManager, Throwable throwable) {
        Plasmid.LOGGER.error("Failed to start game", throwable);

        Text message = new LiteralText("An exception occurred while trying to start game").formatted(Formatting.RED);
        playerManager.broadcastChatMessage(message, MessageType.SYSTEM, Util.NIL_UUID);
    }

    private static int joinGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        PlayerManager playerManager = source.getMinecraftServer().getPlayerManager();

        GameManager.Open open = GameManager.open();
        if (open == null) {
            throw NOT_RECRUITING.create();
        }

        Collection<ServerPlayerEntity> players = Collections.singleton(player);

        Party party = PartyManager.INSTANCE.getOwnParty(PlayerRef.of(player));
        if (party != null) {
            players = party.onlinePlayers(source.getMinecraftServer())
                    .collect(Collectors.toList());
        }

        JoinResult joinResult = open.offerPlayers(players);
        if (joinResult.isErr()) {
            Text error = joinResult.getError();
            throw new SimpleCommandExceptionType(error).create();
        }

        for (ServerPlayerEntity member : players) {
            Text joinMessage = member.getDisplayName().shallowCopy()
                    .append(" has joined the game lobby!")
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW));

            // TODO: broadcast to game only
            playerManager.broadcastChatMessage(joinMessage, MessageType.SYSTEM, Util.NIL_UUID);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int startGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        GameManager.Open open = GameManager.open();
        if (open == null) {
            throw NOT_RECRUITING.create();
        }

        StartResult startResult = open.requestStart();
        if (startResult.isErr()) {
            Text error = startResult.getError();
            throw new SimpleCommandExceptionType(error).create();
        }

        MinecraftServer server = source.getMinecraftServer();
        PlayerManager playerManager = server.getPlayerManager();
        Text message = new LiteralText("Game is starting!").formatted(Formatting.GRAY);
        playerManager.broadcastChatMessage(message, MessageType.SYSTEM, Util.NIL_UUID);

        return Command.SINGLE_SUCCESS;
    }

    private static int stopGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        GameManager.Open open = GameManager.open();
        if (open == null) {
            throw NOT_RECRUITING.create();
        }

        open.close();

        MinecraftServer server = context.getSource().getMinecraftServer();

        Text message = new LiteralText("Game has been stopped").formatted(Formatting.GRAY);
        server.getPlayerManager().broadcastChatMessage(message, MessageType.SYSTEM, Util.NIL_UUID);

        return Command.SINGLE_SUCCESS;
    }

    private static int listGames(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(new LiteralText("Registered games:").formatted(Formatting.BOLD), false);
        for (Identifier id : GameConfigs.getKeys()) {
            String command = "/game open " + id;

            ClickEvent linkClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
            HoverEvent linkHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(command));
            Style linkStyle = Style.EMPTY
                    .withFormatting(Formatting.UNDERLINE)
                    .withColor(Formatting.BLUE)
                    .withClickEvent(linkClick)
                    .setHoverEvent(linkHover);

            MutableText link = new LiteralText(id.toString()).setStyle(linkStyle);
            source.sendFeedback(new LiteralText(" - ").append(link), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static SuggestionProvider<ServerCommandSource> gameSuggestions() {
        return (ctx, builder) -> {
            return CommandSource.suggestMatching(
                    GameConfigs.getKeys().stream().map(Identifier::toString),
                    builder
            );
        };
    }
}
