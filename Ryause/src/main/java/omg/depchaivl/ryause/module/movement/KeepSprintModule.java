package omg.depchaivl.ryause.module.movement;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

public class KeepSprintModule extends Module {
    public static KeepSprintModule instance;
    public static SliderSetting slow;
    public static ButtonSetting disableWhileJumping, disableSprinting, onlyReduceReachHits;

    public KeepSprintModule(int key) {
        super("KeepSprint", Category.movement, key);
        instance = this;
    }

    public void link() {
        slow = (SliderSetting) ModuleManager.setting(this, "Slow");
        disableWhileJumping = (ButtonSetting) ModuleManager.setting(this, "Disable while jumping");
        disableSprinting = (ButtonSetting) ModuleManager.setting(this, "Disable sprinting");
        onlyReduceReachHits = (ButtonSetting) ModuleManager.setting(this, "Only reduce reach hits");
    }

    @Override
    public void onAttackSlowdown(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean vanilla = false;
        if (disableWhileJumping.enabled && !mc.thePlayer.onGround) {
            vanilla = true;
        } else if (onlyReduceReachHits.enabled && !mc.thePlayer.capabilities.isCreativeMode) {
            double d = -1.0;
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
            if (omg.depchaivl.ryause.module.ModuleManager.enabled("KillAura") != null && omg.depchaivl.ryause.module.combat.KillAuraModule.hasTarget()) {
                d = eyes.distanceTo(omg.depchaivl.ryause.module.combat.KillAuraModule.target.entity.getPositionEyes(1.0f));
            } else if (omg.depchaivl.ryause.module.combat.ReachModule.instance != null && omg.depchaivl.ryause.module.combat.ReachModule.instance.isEnabled() && mc.objectMouseOver != null) {
                d = eyes.distanceTo(mc.objectMouseOver.hitVec);
            }
            if (d != -1.0 && d <= 3.0) {
                vanilla = true;
            }
        }
        if (vanilla) {
            mc.thePlayer.motionX *= 0.6;
            mc.thePlayer.motionZ *= 0.6;
        } else {
            float f = (100.0f - (float) slow.getInput()) / 100.0f;
            mc.thePlayer.motionX *= (double) f;
            mc.thePlayer.motionZ *= (double) f;
        }
        mc.thePlayer.setSprinting(!vanilla && !disableSprinting.enabled);
    }
}
