package omg.depchaivl.ryause.module;

import omg.depchaivl.ryause.gui.Console;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.util.Animation;

public class CommandLineModule extends Module {
    public static boolean open;
    public static boolean closing;
    public static ButtonSetting animate;
    public static Animation animation;

    public CommandLineModule(int key) {
        super("Command Line", Category.client, key);
    }

    public void link() {
        animate = (ButtonSetting) ModuleManager.setting(this, "Animate");
    }

    @Override
    public void onEnable() {
        Console.pickBackground();
        open = true;
        closing = false;
        if (animate.isToggled()) {
            animation = new Animation(500.0f);
            animation.start();
        }
    }

    @Override
    public void onDisable() {
        closing = true;
        if (animation != null) {
            animation.start();
        }
    }
}
