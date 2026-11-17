package omg.depchaivl.ryause.module.movement;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.player.ScaffoldModule;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class ClutchModule extends Module {
    private static final long KNOCKBACK_WINDOW_MS = 500L;

    public static ClutchModule instance;
    public static SliderSetting minFall, delayVal;
    public static ButtonSetting onKb, voidOnly;

    private volatile long lastKnockbackAt;
    private long conditionSince;
    private boolean enabledScaffold;

    public ClutchModule(int key) {
        super("Clutch", Category.movement, key);
        instance = this;
    }

    @Override
    public void link() {
        minFall = (SliderSetting) ModuleManager.setting(this, "Minimum fall distance");
        delayVal = (SliderSetting) ModuleManager.setting(this, "Enable delay");
        onKb = (ButtonSetting) ModuleManager.setting(this, "On-kb");
        voidOnly = (ButtonSetting) ModuleManager.setting(this, "Void-only");
    }

    @Override
    public void onKnockback(S12PacketEntityVelocity packet, long now) {
        this.lastKnockbackAt = now;
    }

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            releaseScaffold();
            return;
        }

        ScaffoldModule scaffold = ScaffoldModule.instance;
        if (scaffold == null) {
            throw new IllegalStateException("Clutch requires the Scaffold module");
        }

        long now = System.currentTimeMillis();
        double blockDistance = distanceToNearestBlockBelow();
        boolean fallCondition = blockDistance >= minFall.getInput();
        boolean voidCondition = !voidOnly.isToggled() || Double.isInfinite(blockDistance);
        boolean baseCondition = fallCondition && voidCondition;

        if (!baseCondition) {
            releaseScaffold();
            return;
        }

        if (this.enabledScaffold && scaffold.isEnabled()) {
            return;
        }

        boolean knockbackCondition = !onKb.isToggled()
                || now - this.lastKnockbackAt <= KNOCKBACK_WINDOW_MS;
        if (!knockbackCondition || scaffold.isEnabled()) {
            this.conditionSince = 0L;
            return;
        }

        if (this.conditionSince == 0L) {
            this.conditionSince = now;
        }
        if (now - this.conditionSince < (long) delayVal.getInput()) {
            return;
        }

        scaffold.enable();
        this.enabledScaffold = scaffold.isEnabled();
        this.conditionSince = 0L;
    }

    @Override
    public void onDisable() {
        releaseScaffold();
        this.lastKnockbackAt = 0L;
    }

    private void releaseScaffold() {
        this.conditionSince = 0L;
        if (this.enabledScaffold) {
            ScaffoldModule scaffold = ScaffoldModule.instance;
            if (scaffold != null && scaffold.isEnabled()) {
                scaffold.disable();
            }
            this.enabledScaffold = false;
        }
    }

    private static double distanceToNearestBlockBelow() {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        int minY = Math.max(0, feetY - 256);
        for (int y = feetY - 1; y >= minY; y--) {
            if (!mc.theWorld.isAirBlock(new BlockPos(x, y, z))) {
                return mc.thePlayer.posY - (y + 1.0);
            }
        }
        return Double.POSITIVE_INFINITY;
    }

}
