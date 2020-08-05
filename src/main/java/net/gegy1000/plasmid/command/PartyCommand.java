package net.gegy1000.plasmid.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.gegy1000.plasmid.party.Party;
import net.gegy1000.plasmid.party.PartyError;
import net.gegy1000.plasmid.party.PartyManager;
import net.gegy1000.plasmid.party.PartyResult;
import net.gegy1000.plasmid.util.PlayerRef;
import net.minecraft.command.arguments.EntityArgumentType;
import net.minecraft.command.arguments.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
            case DOES_NOT_EXIST: return new LiteralText("You do not control any party!");
            case ALREADY_INVITED: return new LiteralText(player + " is already invited to this party!");
            case ALREADY_IN_PARTY: return new LiteralText("You are already in this party!");
            case CANNOT_REMOVE_SELF: return new LiteralText("Cannot remove yourself from the party!");
            case NOT_IN_PARTY: return new LiteralText(player + " is not in this party!");
            case NOT_INVITED: return new LiteralText("You are not invited to this party!");
            default: throw new UnsupportedOperationException();
        }
    }

    private static int invitePlayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity owner = source.getPlayer();

        ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");

        PartyResult result = PartyManager.INSTANCE.invitePlayer(PlayerRef.of(owner), PlayerRef.of(player));
        if (result.isOk()) {
            MutableText message = new LiteralText("Invited ")
                    .append(player.getDisplayName())
                    .append(" to the party");

            source.sendFeedback(message.formatted(Formatting.GOLD), false);

            MutableText notificationLink = new LiteralText("Click here to join")
                    .setStyle(Style.EMPTY
                            .withColor(Formatting.BLUE)
                            .withColor(Formatting.UNDERLINE)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/party accept " + owner.getGameProfile().getName()
                            ))
                            .setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    new LiteralText("Join ").append(owner.getDisplayName()).append("'s party")
                            ))
                    );

            MutableText notification = new LiteralText("You have been invited to join ")
                    .append(owner.getDisplayName())
                    .append("'s party! ")
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
            PartyResult result = PartyManager.INSTANCE.kickPlayer(PlayerRef.of(owner), PlayerRef.of(profile));
            if (result.isOk()) {
                Party party = result.getParty();

                MutableText message = new LiteralText(profile.getName() + " has been kicked from the party");
                party.broadcastMessage(server, message.formatted(Formatting.GOLD));

                PlayerRef.of(profile).ifOnline(server, player -> {
                    player.sendMessage(new LiteralText("You have been kicked from the party").formatted(Formatting.RED), false);
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

        PartyResult result = PartyManager.INSTANCE.transferParty(PlayerRef.of(owner), PlayerRef.of(player));
        if (result.isOk()) {
            source.sendFeedback(
                    new LiteralText("Your party has been transferred to ").append(player.getDisplayName())
                            .formatted(Formatting.GOLD),
                    false
            );

            player.sendMessage(
                    owner.getDisplayName().shallowCopy().append("'s party has been transferred to you")
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

        PartyResult result = PartyManager.INSTANCE.acceptInvite(PlayerRef.of(player), PlayerRef.of(owner));
        if (result.isOk()) {
            Party party = result.getParty();

            MutableText message = player.getDisplayName().shallowCopy().append(" has joined the party!");
            party.broadcastMessage(source.getMinecraftServer(), message.formatted(Formatting.GOLD));
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, player));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int leave(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();

        PartyResult result = PartyManager.INSTANCE.leaveParty(PlayerRef.of(player));
        if (result.isOk()) {
            Party party = result.getParty();

            MutableText message = player.getDisplayName().shallowCopy().append(" has left the party!");
            party.broadcastMessage(source.getMinecraftServer(), message.formatted(Formatting.GOLD));
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, player));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int disband(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity owner = source.getPlayer();

        PartyResult result = PartyManager.INSTANCE.disbandParty(PlayerRef.of(owner));
        if (result.isOk()) {
            Party party = result.getParty();

            LiteralText message = new LiteralText("Your party has been disbanded!");
            party.broadcastMessage(source.getMinecraftServer(), message.formatted(Formatting.GOLD));
        } else {
            PartyError error = result.getError();
            source.sendError(displayError(error, owner));
        }

        return Command.SINGLE_SUCCESS;
    }
}
