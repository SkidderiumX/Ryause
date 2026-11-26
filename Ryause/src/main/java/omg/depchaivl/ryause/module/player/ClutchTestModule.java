package omg.depchaivl.ryause.module.player;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import omg.depchaivl.ryause.util.BlockUtils;
import omg.depchaivl.ryause.util.ItemUtils;
import omg.depchaivl.ryause.util.PlayerUtils;
import omg.depchaivl.ryause.util.RotationManager;
import omg.depchaivl.ryause.util.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class ClutchTestModule extends Module {
    private static final long GROUND_KNOCKBACK_TIMEOUT_MS = 500L;
    private static final long AIRBORNE_TIMEOUT_MS = 8000L;
    private static final EnumFacing[] PLACEMENT_FACES = {
            EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };

    public static ClutchTestModule instance;
    public static SliderSetting minFall, delay, fov;
    public static ButtonSetting onlyVoid;

    private volatile long lastKnockbackAt;
    private long handledKnockbackAt;
    private long sessionStartedAt;
    private boolean airborne;
    private int originalSlot = -1;
    private int blockSlot = -1;
    private Placement target;

    private static final class Placement {
        final BlockPos support;
        final EnumFacing face;
        final float yaw;
        final float pitch;
        final double distanceSquared;

        Placement(BlockPos support, EnumFacing face, float yaw, float pitch, double distanceSquared) {
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
            this.distanceSquared = distanceSquared;
        }
    }

    public ClutchTestModule(int key) {
        super("Clutch Test", Category.player, key);
        instance = this;
    }

    @Override
    public void link() {
        minFall = (SliderSetting) ModuleManager.setting(this, "Min fall");
        delay = (SliderSetting) ModuleManager.setting(this, "Delay");
        fov = (SliderSetting) ModuleManager.setting(this, "FOV");
        onlyVoid = (ButtonSetting) ModuleManager.setting(this, "Only-Void");
    }

    @Override
    public void onKnockback(S12PacketEntityVelocity packet, long now) {
        this.lastKnockbackAt = now;
    }

    @Override
    public float[] getRotations(float yaw, float pitch) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || mc.isGamePaused()) {
            endSession();
            return null;
        }

        long now = System.currentTimeMillis();
        if (this.sessionStartedAt == 0L) {
            long knockbackAt = this.lastKnockbackAt;
            if (knockbackAt <= this.handledKnockbackAt
                    || now - knockbackAt > GROUND_KNOCKBACK_TIMEOUT_MS) {
                return null;
            }
            this.handledKnockbackAt = knockbackAt;
            if (distanceToNearestBlockBelow() < minFall.getInput()
                    || onlyVoid.isToggled() && !isVoidBelow()) {
                return null;
            }
            this.blockSlot = findBlockSlot();
            if (this.blockSlot == -1) {
                return null;
            }
            this.originalSlot = mc.thePlayer.inventory.currentItem;
            this.sessionStartedAt = now;
            this.airborne = !mc.thePlayer.onGround;
            if (this.blockSlot != this.originalSlot) {
                PlayerUtils.setSlot(this.blockSlot, true);
            }
        } else if (now - this.sessionStartedAt > AIRBORNE_TIMEOUT_MS) {
            endSession();
            return null;
        }

        if (!mc.thePlayer.onGround) {
            this.airborne = true;
        } else if (this.airborne || now - this.lastKnockbackAt > GROUND_KNOCKBACK_TIMEOUT_MS) {
            endSession();
            return null;
        }

        if (now - this.sessionStartedAt < (long) delay.getInput()) {
            return null;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (!ItemUtils.isScaffoldBlock(held)) {
            endSession();
            return null;
        }

        this.target = findPlacement();
        if (this.target == null) {
            return null;
        }

        if (RotationManager.rotating) {
            MovingObjectPosition hit = PlayerUtils.rayTraceBlock(
                    this.target.support, this.target.face, reach(), RotationManager.serverYaw, RotationManager.serverPitch);
            if (hit != null && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held,
                    this.target.support, this.target.face, hit.hitVec)) {
                PlayerUtils.swing(false, true);
            }
        }
        return new float[]{this.target.yaw, this.target.pitch};
    }

    @Override
    public void onDisable() {
        endSession();
        this.lastKnockbackAt = 0L;
        this.handledKnockbackAt = 0L;
    }

    private void endSession() {
        this.target = null;
        this.sessionStartedAt = 0L;
        this.airborne = false;
        if (this.originalSlot >= 0 && mc.thePlayer != null
                && mc.thePlayer.inventory.currentItem == this.blockSlot) {
            PlayerUtils.setSlot(this.originalSlot, true);
        }
        this.originalSlot = -1;
        this.blockSlot = -1;
    }

    private Placement findPlacement() {
        Placement best = null;
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        if (feetY <= 0) {
            return null;
        }
        int minY = Math.max(0, feetY - 3);
        int maxY = feetY - 1;
        double reach = reach();
        double reachSquared = reach * reach;
        double halfFovCos = Math.cos(Math.toRadians(fov.getInput() / 2.0));
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = RotationUtils.getVectorForRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos destination = new BlockPos(x + dx, y, z + dz);
                    if (!BlockUtils.isReplaceable(destination)) {
                        continue;
                    }
                    for (EnumFacing face : PLACEMENT_FACES) {
                        BlockPos support = destination.offset(face.getOpposite());
                        if (BlockUtils.isReplaceable(support)
                                || BlockUtils.getBlock(support).getMaterial().isLiquid()
                                || BlockUtils.isInteractable(BlockUtils.getBlock(support))) {
                            continue;
                        }
                        for (Vec3 point : RotationUtils.facePoints(support, face)) {
                            double px = point.xCoord - eye.xCoord;
                            double py = point.yCoord - eye.yCoord;
                            double pz = point.zCoord - eye.zCoord;
                            double distanceSquared = px * px + py * py + pz * pz;
                            if (distanceSquared > reachSquared || best != null && distanceSquared >= best.distanceSquared) {
                                continue;
                            }
                            double distance = Math.sqrt(distanceSquared);
                            if (distance == 0.0 || (look.xCoord * px + look.yCoord * py + look.zCoord * pz)
                                    / distance < halfFovCos) {
                                continue;
                            }
                            float[] rotations = RotationUtils.toPosition(point.xCoord, point.yCoord, point.zCoord);
                            if (PlayerUtils.rayTraceBlock(support, face, reach, rotations[0], rotations[1]) == null) {
                                continue;
                            }
                            best = new Placement(support, face, rotations[0], rotations[1], distanceSquared);
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double reach() {
        return mc.playerController.getBlockReachDistance();
    }

    private static int findBlockSlot() {
        int bestSlot = -1;
        int bestStack = 0;
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!ItemUtils.isScaffoldBlock(stack) || stack.stackSize <= bestStack) {
                continue;
            }
            bestStack = stack.stackSize;
            bestSlot = slot;
        }
        return bestSlot;
    }

    private static double distanceToNearestBlockBelow() {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        for (int y = feetY - 1; y >= 0; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (mc.theWorld.getBlockState(pos).getBlock().getMaterial().isSolid()) {
                return mc.thePlayer.posY - y - 1.0;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private static boolean isVoidBelow() {
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        int minY = Math.max(0, feetY - 256);
        for (int y = feetY - 1; y >= minY; y--) {
            if (mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }
}
