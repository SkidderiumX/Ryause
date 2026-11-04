package omg.depchaivl.ryause.module.render;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;

public class NoCameraClipModule extends Module {
    public static NoCameraClipModule instance;

    public NoCameraClipModule(int key) {
        super("NoCameraClip", Category.render, key);
        instance = this;
    }
}
