package omg.depchaivl.ryause.module.combat;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

public class ReduceModule extends Module {
    public static ReduceModule instance;
    public static SliderSetting attackReduction, chance;

    public ReduceModule(int key) {
        super("Reduce", Category.combat, key);
        instance = this;
    }

    public void link() {
        attackReduction = (SliderSetting) ModuleManager.setting(this, "Attack reduction");
        chance = (SliderSetting) ModuleManager.setting(this, "Chance");
    }

    @Override
    public void onAttackSlowdown(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        double d;
        if (chance.getInput() != 100.0 && (d = Math.random()) >= chance.getInput() / 100.0) {
            mc.thePlayer.motionX *= 0.6;
            mc.thePlayer.motionZ *= 0.6;
            return;
        }
        d = (100.0 - (double) ((float) attackReduction.getInput())) / 100.0;
        mc.thePlayer.motionX *= d;
        mc.thePlayer.motionZ *= d;
        mc.thePlayer.setSprinting(false);
    }
}
