package omg.depchaivl.ryause.module.combat;

import omg.depchaivl.ryause.util.PlayerUtils;
import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.render.IndicatorsModule;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class VelocityModule extends Module {
    public static SliderSetting horizontal, vertical, chance;
    public static ButtonSetting onlyWhileTargeting, disableWhileHoldingS;

    public VelocityModule(int key) {
        super("Velocity", Category.combat, key);
    }

    public void link() {
        horizontal = (SliderSetting) ModuleManager.setting(this, "Horizontal");
        vertical = (SliderSetting) ModuleManager.setting(this, "Vertical");
        chance = (SliderSetting) ModuleManager.setting(this, "Chance");
        onlyWhileTargeting = (ButtonSetting) ModuleManager.setting(this, "Only while targeting");
        disableWhileHoldingS = (ButtonSetting) ModuleManager.setting(this, "Disable while holding S");
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.entity != mc.thePlayer || !Chat.inWorld()) {
            return;
        }
        if (ModuleManager.enabled("AntiKnockback") != null) {
            return;
        }
        if (mc.thePlayer.maxHurtTime <= 0 || mc.thePlayer.hurtTime != mc.thePlayer.maxHurtTime) {
            return;
        }
        if (Math.abs(System.currentTimeMillis() - PlayerUtils.lastFireballOnBlock) < 400L) {
            return;
        }
        if (onlyWhileTargeting.enabled && !PlayerUtils.targetMatches(1)) {
            return;
        }
        if (disableWhileHoldingS.enabled && GameSettings.isKeyDown(mc.gameSettings.keyBindBack)) {
            return;
        }
        if (chance.getInput() != 100.0 && !(Math.random() < chance.getInput() / 100.0)) {
            return;
        }
        if (horizontal.getInput() != 100.0) {
            double d = horizontal.getInput() / 100.0;
            mc.thePlayer.motionX *= d;
            mc.thePlayer.motionZ *= d;
        }
        if (vertical.getInput() != 100.0) {
            mc.thePlayer.motionY *= vertical.getInput() / 100.0;
        }
    }
}
