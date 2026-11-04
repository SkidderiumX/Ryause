package omg.depchaivl.ryause.module.player;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class AutoJumpModule extends Module {
    public static ButtonSetting stopWhileSneaking;
    private boolean jumping;

    public AutoJumpModule(int key) {
        super("AutoJump", Category.player, key);
    }

    public void link() {
        stopWhileSneaking = (ButtonSetting) ModuleManager.setting(this, "Stop while sneaking");
    }

    @Override
    public void onUpdate() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(!mc.thePlayer.onGround || stopWhileSneaking.enabled && mc.thePlayer.isSneaking())) {
            if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox()
                    .offset(mc.thePlayer.motionX / 3.0, -1.0, mc.thePlayer.motionZ / 3.0)).isEmpty()) {
                this.jumping = true;
                this.setJump(true);
            } else if (this.jumping) {
                this.jumping = false;
                this.setJump(false);
            }
        } else if (this.jumping) {
            this.jumping = false;
            this.setJump(false);
        }
    }

    @Override
    public void onDisable() {
        this.jumping = false;
        this.setJump(false);
    }

    private void setJump(boolean down) {
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), down);
    }
}
