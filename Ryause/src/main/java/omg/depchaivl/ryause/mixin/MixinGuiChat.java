package omg.depchaivl.ryause.mixin;

import omg.depchaivl.ryause.module.ChatEmotesModule;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiChat.class)
public class MixinGuiChat {
    @Shadow protected GuiTextField inputField;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void clickgui$textBoxSize(CallbackInfo ci) {
        if (inputField == null) {
            return;
        }

        int left = inputField.xPosition;
        int availableWidth = Math.max(1, left + inputField.width - 2);
        float size = ChatEmotesModule.getTextBoxSizeMultiplier();
        inputField.width = Math.max(1, Math.min(availableWidth,
                Math.round(inputField.width * size)));
        inputField.xPosition = left + (availableWidth - inputField.width) / 2;
    }
}
