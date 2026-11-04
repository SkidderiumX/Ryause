package omg.depchaivl.ryause.module.minigames;

import omg.depchaivl.ryause.util.MathUtils;
import omg.depchaivl.ryause.util.PlayerUtils;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

public class AutoRequeueModule extends Module {
    public static AutoRequeueModule instance;
    public static SliderSetting delay;
    private static String command;
    private static int ticks, waitTicks;

    public AutoRequeueModule(int key) {
        super("AutoRequeue", Category.minigames, key);
        instance = this;
    }

    public void link() {
        delay = (SliderSetting) ModuleManager.setting(this, "Delay");
    }

    @Override
    public void onChat(ClientChatReceivedEvent e, String s) {
        if (s.charAt(0) != 'Y' || !s.contains("Want to play again?")) {
            return;
        }
        for (IChatComponent c : e.message.getSiblings()) {
            ClickEvent click = c.getChatStyle().getChatClickEvent();
            if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND) continue;
            command = click.getValue();
            waitTicks = (int) (delay.getInput() * 20.0 + (double) MathUtils.randomInt(1));
            break;
        }
    }

    @Override
    public void onPreUpdate() {
        if (command != null && ++ticks >= waitTicks) {
            PlayerUtils.sendChatMessage(command);
            instance.onDisable();
        }
    }

    @Override
    public void onDisable() {
        command = null;
        ticks = 0;
    }
}
