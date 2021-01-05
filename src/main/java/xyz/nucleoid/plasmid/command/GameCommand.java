package xyz.nucleoid.plasmid.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import net.minecraft.command.argument.NbtCompoundTagArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import xyz.nucleoid.plasmid.Plasmid;
import xyz.nucleoid.plasmid.command.argument.GameChannelArgument;
import xyz.nucleoid.plasmid.command.argument.GameConfigArgument;
import xyz.nucleoid.plasmid.error.ErrorReporter;
import xyz.nucleoid.plasmid.game.ConfiguredGame;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.ManagedGameSpace;
import xyz.nucleoid.plasmid.game.channel.GameChannel;
import xyz.nucleoid.plasmid.game.channel.GameChannelManager;
import xyz.nucleoid.plasmid.game.config.GameConfigs;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.util.Scheduler;

import java.util.Comparator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GameCommand {
    public static final SimpleCommandExceptionType NO_GAME_OPEN = new SimpleCommandExceptionType(
            new LiteralText("No games are open!")
    );

    public static final SimpleCommandExceptionType NO_GAME_IN_WORLD = new SimpleCommandExceptionType(
            new LiteralText("No game is open in this world!")
    );

    public static final DynamicCommandExceptionType MALFORMED_CONFIG = new DynamicCommandExceptionType(error -> {
        return new TranslatableText("Malformed config: %s", error);
    });

    // @formatter:off
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("game")
                .then(literal("open")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(GameConfigArgument.argument("game_config")
                        .executes(GameCommand::openGame)
                    )
                    .then(argument("game_config_nbt", NbtCompoundTagArgumentType.nbtCompound())
                        .executes(GameCommand::openAnonymousGame)
                    )
                )
                .then(literal("propose")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(GameChannelArgument.argument("game_channel")
                    .executes(GameCommand::proposeGame)
                ))
                .then(literal("start")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(GameCommand::startGame)
                )
                .then(literal("stop")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(GameCommand::stopGame)
                )
                .then(literal("join")
                    .executes(GameCommand::joinGame)
                    .then(GameChannelArgument.argument("game_channel")
                        .executes(GameCommand::joinQualifiedGame)
                    )
                )
                .then(literal("joinall")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(GameCommand::joinAllGame)
                    .then(GameConfigArgument.argument("game_config")
                        .executes(GameCommand::joinAllQualifiedGame)
                    )
                )
                .then(literal("leave").executes(GameCommand::leaveGame))
                .then(literal("list").executes(GameCommand::listGames))
        );
    }
    // @formatter:on

    private static int openGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Pair<Identifier, ConfiguredGame<?>> game = GameConfigArgument.get(context, "game_config");
        return openGame(context, game.getFirst(), game.getSecond());
    }

    private static int openAnonymousGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        CompoundTag configNbt = NbtCompoundTagArgumentType.getCompoundTag(context, "game_config_nbt");
        DataResult<ConfiguredGame<?>> result = ConfiguredGame.CODEC.decode(NbtOps.INSTANCE, configNbt).map(Pair::getFirst);
        if (result.error().isPresent()) {
            throw MALFORMED_CONFIG.create(result.error().get());
        }

        ConfiguredGame<?> game = result.result().get();
        return openGame(context, new Identifier(Plasmid.ID, "anonymous"), game);
    }

    private static int openGame(CommandContext<ServerCommandSource> context, Identifier gameId, ConfiguredGame<?> game) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getMinecraftServer();
        PlayerManager playerManager = server.getPlayerManager();

        Entity entity = source.getEntity();
        ServerPlayerEntity player = entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null;

        server.submit(() -> {
            if (player != null) {
                ManagedGameSpace currentGameSpace = ManagedGameSpace.forWorld(player.world);
                if (currentGameSpace != null) {
                    currentGameSpace.removePlayer(player);
                }
            }

            GameChannelManager channelManager = GameChannelManager.get(server);

            try {
                channelManager.openOneshot(gameId, game).handleAsync((channel, throwable) -> {
                    if (throwable == null) {
                        if (player != null && ManagedGameSpace.forWorld(player.world) == null) {
                            channel.requestJoin(player);
                        }
                        onOpenSuccess(source, channel, game, playerManager);
                    } else {

                        onOpenError(playerManager, throwable, game);
                    }
                    return null;
                }, server);
            } catch (Throwable throwable) {
                onOpenError(playerManager, throwable, game);
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private static void onOpenSuccess(ServerCommandSource source, GameChannel channel, ConfiguredGame<?> game, PlayerManager playerManager) {
        Text openMessage = new TranslatableText("text.plasmid.game.open.opened", source.getDisplayName(), new LiteralText(game.getName()).formatted(Formatting.GRAY))
                .append(channel.createJoinLink());

        playerManager.broadcastChatMessage(openMessage, MessageType.SYSTEM, Util.NIL_UUID);
    }

    private static void onOpenError(PlayerManager playerManager, Throwable throwable, ConfiguredGame<?> game) {
        Plasmid.LOGGER.error("Failed to start game", throwable);
        ErrorReporter.open(game.getName() + " (" + game.getType().getIdentifier() + ")").report(throwable, "Starting game");

        MutableText message;
        if (throwable instanceof GameOpenException) {
            message = ((GameOpenException) throwable).getReason().shallowCopy();
        } else {
            message = new TranslatableText("text.plasmid.game.open.error");
        }

        playerManager.broadcastChatMessage(message.formatted(Formatting.RED), MessageType.SYSTEM, Util.NIL_UUID);
    }

    private static int proposeGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        GameChannel channel = GameChannelArgument.get(context, "game_channel");

        ServerCommandSource source = context.getSource();
        Text openMessage = new TranslatableText("text.plasmid.game.propose", source.getDisplayName(), channel.getName().shallowCopy().formatted(Formatting.GRAY))
                .append(channel.createJoinLink());

        PlayerManager playerManager = source.getMinecraftServer().getPlayerManager();
        playerManager.broadcastChatMessage(openMessage, MessageType.SYSTEM, Util.NIL_UUID);

        return Command.SINGLE_SUCCESS;
    }

    private static int joinGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        GameChannel channel = getJoinableChannel(context);
        channel.requestJoin(context.getSource().getPlayer());

        return Command.SINGLE_SUCCESS;
    }

    private static int joinQualifiedGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        GameChannel channel = GameChannelArgument.get(context, "game_channel");
        channel.requestJoin(context.getSource().getPlayer());

        return Command.SINGLE_SUCCESS;
    }

    private static int joinAllGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getMinecraftServer();
        GameChannelManager channelManager = GameChannelManager.get(server);

        GameChannel channel = null;

        Entity entity = context.getSource().getEntity();
        if (entity instanceof ServerPlayerEntity) {
            channel = channelManager.getChannelFor((ServerPlayerEntity) entity);
        }

        if (channel == null) {
            channel = getJoinableChannel(context);
        }

        joinAllPlayersToChannel(context, channel);

        return Command.SINGLE_SUCCESS;
    }

    private static int joinAllQualifiedGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        GameChannel channel = GameChannelArgument.get(context, "game_channel");
        joinAllPlayersToChannel(context, channel);

        return Command.SINGLE_SUCCESS;
    }

    private static void joinAllPlayersToChannel(CommandContext<ServerCommandSource> context, GameChannel channel) {
        PlayerManager playerManager = context.getSource().getMinecraftServer().getPlayerManager();
        for (ServerPlayerEntity player : playerManager.getPlayerList()) {
            if (ManagedGameSpace.forWorld(player.world) == null) {
                channel.requestJoin(player);
            }
        }
    }

    private static GameChannel getJoinableChannel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getMinecraftServer();
        GameChannelManager channelManager = GameChannelManager.get(server);

        return channelManager.getChannels().stream()
                .max(Comparator.comparingInt(GameChannel::getPlayerCount))
                .orElseThrow(NO_GAME_OPEN::create);
    }

    private static int leaveGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(source.getWorld());
        if (gameSpace == null) {
            throw NO_GAME_IN_WORLD.create();
        }

        Scheduler.INSTANCE.submit(server -> {
            gameSpace.removePlayer(player);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int startGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(source.getWorld());
        if (gameSpace == null) {
            throw NO_GAME_IN_WORLD.create();
        }

        gameSpace.requestStart().thenAccept(startResult -> {
            Text message;
            if (startResult.isError()) {
                Text error = startResult.getError();
                message = error.shallowCopy().formatted(Formatting.RED);
            } else {
                message = new TranslatableText("text.plasmid.game.started.player", source.getDisplayName())
                        .formatted(Formatting.GRAY);
            }

            gameSpace.getPlayers().sendMessage(message);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int stopGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(source.getWorld());
        if (gameSpace == null) {
            throw NO_GAME_IN_WORLD.create();
        }

        PlayerSet playerSet = gameSpace.getPlayers().copy();

        try {
            gameSpace.close(GameCloseReason.CANCELED);

            MutableText message = new TranslatableText("text.plasmid.game.stopped.player", source.getDisplayName());
            playerSet.sendMessage(message.formatted(Formatting.GRAY));
        } catch (Throwable throwable) {
            Plasmid.LOGGER.error("Failed to stop game", throwable);

            MutableText message = new TranslatableText("text.plasmid.game.stopped.error");
            playerSet.sendMessage(message.formatted(Formatting.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int listGames(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(new TranslatableText("text.plasmid.game.list").formatted(Formatting.BOLD), false);

        for (Identifier id : GameConfigs.getKeys()) {
            String command = "/game open " + id;

            ClickEvent linkClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
            HoverEvent linkHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(command));
            Style linkStyle = Style.EMPTY
                    .withFormatting(Formatting.UNDERLINE)
                    .withColor(Formatting.BLUE)
                    .withClickEvent(linkClick)
                    .withHoverEvent(linkHover);

            MutableText link = new LiteralText(id.toString()).setStyle(linkStyle);
            source.sendFeedback(new LiteralText(" - ").append(link), false);
        }

        return Command.SINGLE_SUCCESS;
    }
}
