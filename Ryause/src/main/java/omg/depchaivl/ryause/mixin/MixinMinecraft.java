package omg.depchaivl.ryause.mixin;

import omg.depchaivl.ryause.hook.Hooks;
import omg.depchaivl.ryause.module.combat.KillAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V"))
    private void clickgui$getMouseOver(EntityRenderer renderer, float partialTicks) {
        Hooks.getMouseOver();
    }

    /** u20+ (KillAura, "Disable while mining" off): a left-click on a block starts no dig while KillAura has a target */
    @Redirect(method = "clickMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;clickBlock(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"))
    private boolean clickgui$clickBlock(PlayerControllerMP controller, BlockPos pos, EnumFacing side) {
        if (KillAuraModule.preventsMining()) {
            return false;
        }
        return controller.clickBlock(pos, side);
    }

    /** u20+ (KillAura, "Disable while mining" off): holding left-click does not continue a dig while KillAura has a target */
    @ModifyVariable(method = "sendClickBlockToController", at = @At("HEAD"), argsOnly = true)
    private boolean clickgui$holdBlock(boolean leftClick) {
        return leftClick && !KillAuraModule.preventsMining();
    }
}
