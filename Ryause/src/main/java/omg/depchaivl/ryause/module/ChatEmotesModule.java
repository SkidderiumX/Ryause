package omg.depchaivl.ryause.module;

import com.hrznstudio.emojiful.ClientEmojiHandler;
import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.config.EmojifulConfig;
import com.hrznstudio.emojiful.gui.EmojifulChatScreen;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.lang.reflect.Field;

public class ChatEmotesModule extends Module {
    public ChatEmotesModule(int key) {
        super("Chat", Category.client, key);
        File configFile = new File(mc.mcDataDir, "config/ryause-chat-emotes.cfg");
        EmojifulConfig.init(configFile);
        EmojifulConfig.renderEmoji = false;
    }

    @Override
    public void onEnable() {
        EmojifulConfig.renderEmoji = true;
        ClientEmojiHandler.setup();
    }

    @Override
    public void onDisable() {
        EmojifulConfig.renderEmoji = false;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.gui instanceof GuiChat) || event.gui instanceof EmojifulChatScreen) {
            return;
        }

        String initialText = "";
        try {
            Field field = ReflectionHelper.findField(
                    GuiChat.class, "defaultInputFieldText", "field_146409_v");
            initialText = (String) field.get(event.gui);
        } catch (Exception e) {
            Constants.LOG.warn("Could not preserve chat draft while opening emoji chat", e);
        }
        event.gui = new EmojifulChatScreen(initialText);
    }
}
