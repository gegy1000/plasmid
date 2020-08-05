package net.gegy1000.plasmid.game.event;

import net.gegy1000.plasmid.game.Game;
import net.gegy1000.plasmid.game.JoinResult;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;

public interface OfferPlayersListener {
    EventType<OfferPlayersListener> EVENT = EventType.create(OfferPlayersListener.class, listeners -> {
        return (game, players) -> {
            for (OfferPlayersListener listener : listeners) {
                JoinResult result = listener.offerPlayers(game, players);
                if (result.isErr()) {
                    return result;
                }
            }
            return JoinResult.ok();
        };
    });

    JoinResult offerPlayers(Game game, Collection<ServerPlayerEntity> players);
}
