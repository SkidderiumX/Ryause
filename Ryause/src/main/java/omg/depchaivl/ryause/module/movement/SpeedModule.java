package omg.depchaivl.ryause.module.movement;

import omg.depchaivl.ryause.util.MoveDirection;
import omg.depchaivl.ryause.event.MotionEvent;
import omg.depchaivl.ryause.util.MoveUtils;
import omg.depchaivl.ryause.util.PlayerUtils;
import omg.depchaivl.ryause.client.Hypixel;
import omg.depchaivl.ryause.client.Overlay;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.player.NoRotateModule;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;

public class SpeedModule extends Module {
    public static SpeedModule instance;
    public static SliderSetting mode, multiplier;
    public static ButtonSetting disableInInventory, onlyForward, onlyStrafe, setbackRecovery;

    public enum Mode {
        VANILLA, FLOAT
    }

    public static Mode current = Mode.VANILLA;
    private static boolean waiting;

    public SpeedModule(int key) {
        super("Speed", Category.movement, key);
        instance = this;
    }

    public void link() {
        mode = (SliderSetting) ModuleManager.setting(this, "Speed");
        multiplier = (SliderSetting) ModuleManager.setting(this, "Multiplier");
        disableInInventory = (ButtonSetting) ModuleManager.setting(this, "Disable in inventory");
        onlyForward = (ButtonSetting) ModuleManager.setting(this, "Only forward");
        onlyStrafe = (ButtonSetting) ModuleManager.setting(this, "Only strafe");
        setbackRecovery = (ButtonSetting) ModuleManager.setting(this, "Setback recovery");
    }

    @Override
    public void onEnable() {
        waiting = true;
    }

    @Override
    public void guiUpdate() {
        current = Mode.values()[(int) mode.getInput()];
    }

    private static void boost(MotionEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.thePlayer.onGround || mc.thePlayer.isSneaking() || mc.thePlayer.capabilities.isFlying) {
            return;
        }
        if (!PlayerUtils.isMoving() || GameSettings.isKeyDown(mc.gameSettings.keyBindJump)) {
            return;
        }
        if (disableInInventory.enabled && Overlay.screenOpen()) {
            return;
        }
        if (setbackRecovery.enabled && NoRotateModule.sinceSetback(3000L)) {
            return;
        }
        double current = MoveUtils.speed();
        if (current == 0.0) {
            return;
        }
        MoveDirection dir = PlayerUtils.direction(e.yaw);
        if (onlyForward.enabled && !dir.forward || onlyStrafe.enabled && !dir.diagonal) {
            return;
        }
        double d = 0.153 * multiplier.getInput() + (double) PlayerUtils.speedLevel() * 0.04;
        MoveUtils.strafe(Math.max(d, current));
    }

    private static boolean edgeStairs(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        IBlockState st = mc.theWorld.getBlockState(pos);
        if (st.getBlock() instanceof BlockStairs) {
            if (st.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP) {
                return false;
            }
            for (int i = -1; i <= 1; i += 2) {
                if (mc.theWorld.getBlockState(pos.add(i, 0, 0)).getBlock() == Blocks.air) {
                    return true;
                }
                if (mc.theWorld.getBlockState(pos.add(0, 0, i)).getBlock() != Blocks.air) continue;
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPreMotion(MotionEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (current == Mode.VANILLA) {
            boost(e);
        } else if (current == Mode.FLOAT) {
            if (omg.depchaivl.ryause.module.player.ScaffoldModule.active) {
                waiting = true;
                return;
            }
            BlockPos pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (edgeStairs(pos) || block instanceof BlockSlab && block.getClass().getSimpleName().contains("Half")) {
                waiting = true;
            } else {
                if (waiting && PlayerUtils.airTicks >= 3L) {
                    waiting = false;
                }
                if (!waiting && mc.thePlayer.onGround && e.y % 1.0 == 0.0) {
                    if (Hypixel.onHypixel()) {
                        e.y += 1.0E-13;
                    }
                    boost(e);
                }
            }
        }
    }
}
