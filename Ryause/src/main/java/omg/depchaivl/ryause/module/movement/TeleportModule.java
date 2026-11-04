package omg.depchaivl.ryause.module.movement;

import omg.depchaivl.ryause.util.PlayerUtils;
import omg.depchaivl.ryause.util.TeleportPath;
import omg.depchaivl.ryause.client.BotCheck;
import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.util.Render3D;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.Color;
import java.util.ArrayList;

public class TeleportModule extends Module {
    public static ButtonSetting rightClickTeleport, highlightTarget, highlightPath;
    private ArrayList<TeleportPath.Point> path = new ArrayList<>();
    private BlockPos target;

    public TeleportModule(int key) {
        super("Teleport", Category.movement, key);
    }

    public void link() {
        rightClickTeleport = (ButtonSetting) ModuleManager.setting(this, "Right click teleport");
        highlightTarget = (ButtonSetting) ModuleManager.setting(this, "Highlight target");
        highlightPath = (ButtonSetting) ModuleManager.setting(this, "Highlight path");
    }

    private static MovingObjectPosition lookBlock() {
        MovingObjectPosition m = BotCheck.rayTraceBlocks(150.0);
        return m == null || m.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK ? null : m;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!(rightClickTeleport.enabled && highlightTarget.enabled && this.target != null && Chat.inWorld())) {
            return;
        }
        Render3D.block(this.target, Color.orange.getRGB(), true, true);
        if (highlightPath.enabled) {
            int n = 0;
            for (TeleportPath.Point p : this.path) {
                if (n >= 175) break;
                Render3D.block(new BlockPos(p.x, p.y, p.z), Color.yellow.getRGB(), false, true);
                ++n;
            }
        }
    }

    @Override
    public void onUpdate() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!rightClickTeleport.enabled || !highlightTarget.enabled) {
            return;
        }
        MovingObjectPosition m = lookBlock();
        if (m == null) {
            return;
        }
        this.target = m.getBlockPos();
        if (highlightPath.enabled) {
            this.path = TeleportPath.path(new TeleportPath.Point(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                    new TeleportPath.Point(this.target.getX(), this.target.getY() + 2, this.target.getZ()));
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (!(e.button == 1 && e.buttonstate && rightClickTeleport.enabled && Chat.inWorld())) {
            return;
        }
        MovingObjectPosition m = lookBlock();
        if (m == null) {
            return;
        }
        this.target = m.getBlockPos();
        TeleportPath.teleport(this.target, true);
    }

    @Override
    public void onEnable() {
        this.target = null;
        this.path.clear();
        if (rightClickTeleport.enabled) {
            return;
        }
        MovingObjectPosition m = lookBlock();
        if (m == null) {
            this.disable();
            return;
        }
        TeleportPath.teleport(m.getBlockPos(), true);
        this.disable();
    }
}
