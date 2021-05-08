package xyz.nucleoid.plasmid.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.party.Party;
import xyz.nucleoid.plasmid.party.PartyError;
import xyz.nucleoid.plasmid.party.PartyManager;
import xyz.nucleoid.plasmid.party.PartyResult;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PartyCommand {
    // @formatter:off
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("party")
                .then(literal("invite")
                    .then(argument("player", EntityArgumentType.player())
                    .executes(PartyCommand::invitePlayer)
                ))
                .then(literal("kick")
                    .then(argument("player", GameProfileArgumentType.gameProfile())
                    .executes(PartyCommand::kickPlayer)
                ))
                .then(literal("transfer")
                    .then(argument("player", EntityArgumentType.player())
                    .executes(PartyCommand::transferToPlayer)
                ))
                .then(literal("accept")
                    .then(argument("player", EntityArgumentType.player())
                    .executes(PartyCommand::acceptInvite)
                ))
                .then(literal("leave").executes(PartyCommand::leave))
                .then(literal("disband").executes(PartyCommand::disband))
        );
    }
    // @formatter:on

    private static Text displayError(PartyError error, ServerPlayerEntity player) {
        return displayError(error, player.getGameProfile().getName());
    }

    private static Text displayError(PartyError error, String player) {
        switch (error) {
            case DOES_NOT_EXIST: return new TranslatableText("text.plasmid.party.error.does_not_exist");
            case ALREADY_INVITED: return new TranslatableText("text.plasmid.party.error.already_invited", player);
            case ALREADY_IN_PARTY: return new TranslatableText("text.plasmid.party.error.already_in_party");
            case CANNOT_REMOVE_SELF: return new TranslatableText("text.plasmid.party.error.cannot_remove_self");
            case NOT_IN_PARTY: return new TranslatableText("text.plasmid.party.error.not_in_party", player);
            case NOT_INVITED: return new TranslatableText("text.plasmid.party.error.not_invited");
            default: throw new UnsupportedOperationException();
        }
    }

    private static int invitePlayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity owner = source.getPlayer();

        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");

        PartyManager partyManager = PartyManager.get(source.getMinecraftServer());
        PartyResult result = partyManager.invitePlayer(PlayerRef.of(owner), PlayerRef.of(player));
        if (result.isOk()) {
            MutableText message = new TranslatableText("text.plasmid.party.invited.sender", player.getDisplayName());

            source.sendFeedback(message.formatted(Formatting.GOLD), false);

            MutableText notificationLink = new TranslatableText("text.plasmid.party.invited.receiver.click")
                    .setStyle(Style.EMPTY
                            .withColor(Formatting.BLUE)
                            .withColor(Formatting.UNDERLINE)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/party accept " + owner.getGameProfile().getName()
                            ))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    new TranslatableText("text.plasmid.party.invited.receiver.hover", player.getDisplayName())
                            ))
                    );

            MutableText notification = new TranslatableText("text.plasmid.party.invited.receiver", owner.getDisplayName())
                    .formatted(Formatting.GOLD)
                    .append(notificationLink);

            player.sendMessage(notification, false);
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, player));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int kickPlayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getMinecraftServer();
        ServerPlayerEntity owner = source.getPlayer();

        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "player");

        for (GameProfile profile : profiles) {
            PartyManager partyManager = PartyManager.get(source.getMinecraftServer());
            PartyResult result = partyManager.kickPlayer(PlayerRef.of(owner), PlayerRef.of(profile));
            if (result.isOk()) {
                Party party = result.getParty();

                MutableText message = new TranslatableText("text.plasmid.party.kicked.sender", owner.getDisplayName());
                party.getMemberPlayers().sendMessage(message.formatted(Formatting.GOLD));

                PlayerRef.of(profile).ifOnline(server, player -> {
                    player.sendMessage(new TranslatableText("text.plasmid.party.kicked.receiver").formatted(Formatting.RED), false);
                });
            } else {
                PartyError error = result.getError();
                source.sendError(displayError(error, profile.getName()));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int transferToPlayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity owner = source.getPlayer();

        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");

        PartyManager partyManager = PartyManager.get(source.getMinecraftServer());
        PartyResult result = partyManager.transferParty(PlayerRef.of(owner), PlayerRef.of(player));
        if (result.isOk()) {
            source.sendFeedback(
                    new TranslatableText("text.plasmid.party.transferred.sender", player.getDisplayName())
                            .formatted(Formatting.GOLD),
                    false
            );

            player.sendMessage(
                    new TranslatableText("text.plasmid.party.transferred.receiver", owner.getDisplayName())
                            .formatted(Formatting.GOLD),
                    false
            );
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, player));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int acceptInvite(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();

        ServerPlayerEntity owner = EntityArgumentType.getPlayer(ctx, "player");

        PartyManager partyManager = PartyManager.get(source.getMinecraftServer());
        PartyResult result = partyManager.acceptInvite(PlayerRef.of(player), PlayerRef.of(owner));
        if (result.isOk()) {
            Party party = result.getParty();

            MutableText message = new TranslatableText("text.plasmid.party.join.success", player.getDisplayName());
            party.getMemberPlayers().sendMessage(message.formatted(Formatting.GOLD));
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, player));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int leave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();

        PartyManager partyManager = PartyManager.get(source.getMinecraftServer());
        PartyResult result = partyManager.leaveParty(PlayerRef.of(player));
        if (result.isOk()) {
            Party party = result.getParty();

            MutableText message = new TranslatableText("text.plasmid.party.leave.success", player.getDisplayName());
            party.getMemberPlayers().sendMessage(message.formatted(Formatting.GOLD));
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, player));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int disband(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity owner = source.getPlayer();

        PartyManager partyManager = PartyManager.get(source.getMinecraftServer());
        PartyResult result = partyManager.disbandParty(PlayerRef.of(owner));
        if (result.isOk()) {
            Party party = result.getParty();

            MutableText message = new TranslatableText("text.plasmid.party.disband.success");
            party.getMemberPlayers().sendMessage(message.formatted(Formatting.GOLD));
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, owner));
        }

        return Command.SINGLE_SUCCESS;
    }
}
