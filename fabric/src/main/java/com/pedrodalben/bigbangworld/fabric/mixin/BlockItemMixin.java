package com.pedrodalben.bigbangworld.fabric.mixin;

import com.pedrodalben.bigbangworld.restriction.WorldRestrictionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(
        method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel() instanceof ServerLevel level) {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            BlockItem item = (BlockItem) (Object) this;
            BlockState state = item.getBlock().defaultBlockState();
            if (WorldRestrictionService.isPlacementBlocked(player, state, level)) {
                cir.setReturnValue(InteractionResult.FAIL);
            }
        }
    }
}
