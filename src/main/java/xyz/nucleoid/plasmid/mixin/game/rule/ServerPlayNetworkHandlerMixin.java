package xyz.nucleoid.plasmid.mixin.game.rule;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.plasmid.game.ManagedGameSpace;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.ItemStackSanitizer;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Redirect(
            method = "onCreativeInventoryAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/c2s/play/CreativeInventoryActionC2SPacket;getItemStack()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack modifyCreativeActionStack(CreativeInventoryActionC2SPacket packet) {
        if (!this.player.getEntityWorld().isClient()) {
            ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(this.player.getEntityWorld());
            if (gameSpace != null && gameSpace.containsPlayer(this.player)) {
                RuleResult result = gameSpace.testRule(GameRule.SANITIZE_CREATIVE_ACTIONS);
                if (result != RuleResult.DENY) {
                    return ItemStackSanitizer.sanitize(packet.getItemStack());
                }
            }
        }

        return packet.getItemStack();
    }
}
