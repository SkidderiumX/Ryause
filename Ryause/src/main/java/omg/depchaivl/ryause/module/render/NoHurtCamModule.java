package omg.depchaivl.ryause.module.render;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.SliderSetting;

public class NoHurtCamModule extends Module {
    public static NoHurtCamModule instance;
    public static SliderSetting multiplier;

    public NoHurtCamModule(int key) {
        super("NoHurtCam", Category.render, key);
        instance = this;
    }

    public void link() {
        multiplier = (SliderSetting) ModuleManager.setting(this, "Multiplier");
    }
}
