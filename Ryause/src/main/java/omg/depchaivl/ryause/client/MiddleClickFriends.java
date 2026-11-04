package omg.depchaivl.ryause.client;

import omg.depchaivl.ryause.module.SettingsModule;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.MouseEvent;

public final class MiddleClickFriends {
    private MiddleClickFriends() {
    }

    public static void onMouse(MouseEvent event) {
        if (!event.buttonstate || event.button != 2 || !SettingsModule.middleClickFriends.isToggled()
                || omg.depchaivl.ryause.module.ModuleManager.enabled("TargetStrafe") != null && omg.depchaivl.ryause.module.movement.TargetStrafeModule.active()) {
            return;
        }
        EntityLivingBase e = BotCheck.lookingAt(100.0);
        if (BotCheck.isBot(e) || Friends.addFriend(e.getName())) {
            return;
        }
        Friends.removeFriend(e.getName());
    }
}
