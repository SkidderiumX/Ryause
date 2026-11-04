package omg.depchaivl.ryause.module;

import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.client.ChatCommands;

public class FakeChatModule extends Module {
    public FakeChatModule(Category category, int key) {
        super("Fake Chat", category, key);
    }

    @Override
    public void onEnable() {
        String s = ChatCommands.fakeChatMessage;
        if (s.contains("\\n")) {
            for (String line : s.split("\\\\n")) {
                Chat.raw(line);
            }
        } else {
            Chat.raw(s);
        }
        this.disable();
    }
}
