package omg.depchaivl.ryause.module.render;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;

public class AntiShuffleModule extends Module {
    public static AntiShuffleModule instance;
    public static final String OBFUSCATED = "§k";

    public AntiShuffleModule(int key) {
        super("AntiShuffle", Category.render, key);
        instance = this;
    }
}
