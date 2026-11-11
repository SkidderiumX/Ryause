package omg.depchaivl.ryause.mixin;

import omg.depchaivl.ryause.module.ChatEmotesModule;
import net.minecraft.client.gui.GuiNewChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiNewChat.class)
public class MixinGuiNewChat {
    @Inject(method = "getChatScale", at = @At("RETURN"), cancellable = true)
    private void clickgui$chatSize(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * ChatEmotesModule.getChatSizeMultiplier());
    }
}
