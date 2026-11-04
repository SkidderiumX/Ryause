package omg.depchaivl.ryause.module.network;

import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;

public class VpnStatusModule extends Module {
    public VpnStatusModule(int key) {
        super("VPN Status", Category.network, key);
    }

    @Override
    public boolean canBeEnabled() {
        Chat.moduleMessage(this, "&cThere was an error, try relaunching.");
        return false;
    }
}
