package omg.depchaivl.ryause.module.movement;

import omg.depchaivl.ryause.util.TeleportPath;
import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public class VClipModule extends Module {
    public static SliderSetting distance;
    public static ButtonSetting sendMessage, sendPackets;

    public VClipModule(int key) {
        super("VClip", Category.movement, key);
    }

    public void link() {
        distance = (SliderSetting) ModuleManager.setting(this, "Distance");
        sendMessage = (ButtonSetting) ModuleManager.setting(this, "Send message");
        sendPackets = (ButtonSetting) ModuleManager.setting(this, "Send packets");
    }

    private static String format(double d) {
        return d == Math.floor(d) ? Long.toString((long) d) : Double.toString(d);
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        double d = distance.getInput();
        if (d != 0.0) {
            if (sendPackets.enabled) {
                TeleportPath.teleport(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + d, mc.thePlayer.posZ), false);
            } else {
                mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + d, mc.thePlayer.posZ);
            }
            if (sendMessage.enabled) {
                Chat.print("&7Teleported you " + (d > 0.0 ? "upwards" : "downwards") + " &b" + format(d) + " &7blocks.");
            }
        }
        this.disable();
    }
}
