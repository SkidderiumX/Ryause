package omg.depchaivl.ryause.module.movement;

import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import omg.depchaivl.ryause.util.PacketUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Stasis from Simple Utils 2.3.1 (com.simpleutils.utilities.stasis.Stasis), replacing B4's R351.
 * Only "Pulse" (the pulse interval) is a setting, as in B4; the rest of StasisConfig is fixed below.
 */
public class StasisModule extends Module {
    public static StasisModule instance;
    public static SliderSetting pulse;
    /** True while the player is held at the anchor (read by NoRotate/Timer). */
    public static boolean frozen;

    // StasisConfig values: Mode = Flag, cancel on death off, pulse on (Pulse slider at min = off)
    private static final boolean FLAGLESS = false;
    private static final boolean CANCEL_ON_DEATH = false;
    private static final int VELOCITY_TICKS = 2;
    private static final int VELOCITY_BLOCK_TICKS = 2;
    private static final int PULSE_TICKS = 1;
    private static final int BLOCK_AFTER_DISABLE = 3;
    private static final float MAX_FALL = 0.5f;

    private static final double MOTION_EPS = 1.0E-4;
    private static final double GRAVITY_MATCH_EPS = 0.02;
    private static final int POSITION_REMINDER_TICKS = 20;
    private static final int GROUND_STABLE_TICKS = 3;
    private static final Minecraft mc = Minecraft.getMinecraft();

    private double storedMotionX, storedMotionY, storedMotionZ;
    private boolean motionStored;
    private double anchorX, anchorY, anchorZ;
    private boolean anchorKnown;
    private int velocityTicksRemaining;
    private int velocityInputBlockRemaining;
    private int pulseTicksRemaining;
    private int pulseCooldownRemaining;
    private int blockAfterDisableRemaining;
    private double velocityFallStartY;
    private double lastMotionX, lastMotionY, lastMotionZ;
    private int noReminderTicks;
    private float lastSentYaw, lastSentPitch;
    private boolean lastSentLookValid;
    private int airReminderTicksRemaining;
    private boolean pendingAirReminder;
    private boolean holding;
    private boolean keysDown;
    private int groundStableTicks;
    private double watchPosX, watchPosY, watchPosZ;
    private int lastHurtTime;
    private int[] footXs, footYs, footZs;
    private Block[] footBlocks;
    private int[] footMetas;
    private boolean[] footSolid;
    private boolean feetSampled;
    private int ticksSinceTeleport = 999;

    public StasisModule(int key) {
        super("Stasis", Category.movement, key);
        instance = this;
        // ticks keep running while disabled (blockAfterDisable), so not tied to enable/disable registration
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent(priority = EventPriority.HIGHEST)
            public void onTick(TickEvent.ClientTickEvent e) {
                if (e.phase == TickEvent.Phase.START) {
                    instance.onClientTickStart();
                } else {
                    instance.onClientTickEnd();
                }
            }

            @SubscribeEvent
            public void onUnload(WorldEvent.Unload e) {
                if (e.world != null && e.world.isRemote && instance.isEnabled()) {
                    instance.reset();
                }
            }
        });
    }

    public void link() {
        pulse = (SliderSetting) ModuleManager.setting(this, "Pulse");
    }

    private boolean isPulseEnabled() {
        return !pulse.isAtMin();
    }

    private int getPulseInterval() {
        return (int) pulse.getInput();
    }

    private boolean isActive() {
        return this.isEnabled() && nullCheck();
    }

    private static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    @Override
    public void onEnable() {
        this.motionStored = false;
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.pulseCooldownRemaining = this.getPulseInterval();
        this.blockAfterDisableRemaining = 0;
        this.velocityFallStartY = 0.0;
        this.noReminderTicks = 0;
        this.lastSentLookValid = false;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.groundStableTicks = 0;
        this.feetSampled = false;
        EntityPlayerSP p = mc.thePlayer;
        this.snapshotWatchState(p);
        if (p != null && !p.onGround) {
            this.beginHolding(p);
        } else {
            this.enterIdle(p, false);
        }
        updateFrozen();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        if (this.holding && this.isFrozen()) {
            this.restoreMotion();
        }
        this.holding = false;
        this.motionStored = false;
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.pulseCooldownRemaining = 0;
        this.velocityFallStartY = 0.0;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.groundStableTicks = 0;
        this.feetSampled = false;
        this.anchorKnown = false;
        this.blockAfterDisableRemaining = BLOCK_AFTER_DISABLE;
        updateFrozen();
    }

    private void updateFrozen() {
        frozen = this.isEnabled() && this.isFrozen();
    }

    /** MixinEntityPlayerSP: tail of EntityPlayerSP.onUpdate (Stasis.afterPlayerUpdate). */
    public static void afterPlayerUpdate(EntityPlayerSP p) {
        if (instance == null || p == null || !instance.isActive() || !instance.holding) {
            return;
        }
        if (instance.isFrozen()) {
            instance.applyFrozenState(p);
        } else {
            instance.maybeFinishVelocityForFall(p);
        }
    }

    private void maybeFinishVelocityForFall(EntityPlayerSP p) {
        if (this.velocityTicksRemaining > 0 && MAX_FALL > 0.0f && this.velocityFallStartY - p.posY >= MAX_FALL) {
            this.velocityTicksRemaining = 0;
            this.finishVelocityPassthrough(p);
        }
    }

    /** Hooks.onReceive, for S08/S12/S27. Returns false to cancel. */
    @Override
    public boolean onReceivePacketAlways(Packet packet, net.minecraft.network.INetHandler handler) {
        if (!this.isActive()) {
            return true;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (packet instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook s = (S08PacketPlayerPosLook) packet;
            this.onServerTeleport(p, s.getX(), s.getY(), s.getZ());
        } else if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion s = (S27PacketExplosion) packet;
            if (VELOCITY_TICKS > 0 && motionSpeed(s.func_149149_c(), s.func_149144_d(), s.func_149147_e()) > MOTION_EPS) {
                this.beginHolding(p);
                this.openVelocityWindow();
            }
        } else if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity s = (S12PacketEntityVelocity) packet;
            if (s.getEntityID() == p.getEntityId()) {
                double x = s.getMotionX() / 8000.0;
                double y = s.getMotionY() / 8000.0;
                double z = s.getMotionZ() / 8000.0;
                if (VELOCITY_TICKS > 0 && motionSpeed(x, y, z) > MOTION_EPS) {
                    this.beginHolding(p);
                    this.openVelocityWindow();
                } else if (this.holding) {
                    updateFrozen();
                    return false;
                }
            }
        }
        updateFrozen();
        return true;
    }

    private void onServerTeleport(EntityPlayerSP p, double x, double y, double z) {
        if (CANCEL_ON_DEATH && !this.isLikelyFlagTeleport(p, x, y, z)) {
            this.disable();
            return;
        }
        this.beginHolding(p);
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.pulseCooldownRemaining = this.getPulseInterval();
        this.groundStableTicks = 0;
        this.noReminderTicks = 0;
        this.clearStoredMotion();
        this.setAnchor(x, y, z);
        if (p != null) {
            zeroAllMotion(p);
            this.snapshotMotion();
            this.applyFrozenState(p);
        }
        this.snapshotWatchState(p);
        this.ticksSinceTeleport = 0;
    }

    private void clearStoredMotion() {
        this.storedMotionX = 0.0;
        this.storedMotionY = 0.0;
        this.storedMotionZ = 0.0;
        this.motionStored = true;
    }

    private void openVelocityWindow() {
        if (this.pulseTicksRemaining > 0) {
            this.pulseTicksRemaining = 0;
        }
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.velocityTicksRemaining = VELOCITY_TICKS;
        this.velocityInputBlockRemaining = VELOCITY_BLOCK_TICKS;
        EntityPlayerSP p = mc.thePlayer;
        this.velocityFallStartY = p != null ? p.posY : 0.0;
        this.groundStableTicks = 0;
    }

    private void beginHolding(EntityPlayerSP p) {
        if (p == null) {
            return;
        }
        boolean was = this.holding;
        this.holding = true;
        this.groundStableTicks = 0;
        if (!was) {
            this.motionStored = false;
            this.captureAnchorFromPlayer(p);
            this.storeMotionAndClear();
            this.snapshotMotion();
            this.applyFrozenState(p);
            this.noReminderTicks = 0;
            this.lastSentLookValid = false;
            this.airReminderTicksRemaining = 0;
            this.pendingAirReminder = false;
        } else if (!this.anchorKnown) {
            this.captureAnchorFromPlayer(p);
        }
        this.snapshotWatchState(p);
    }

    private void enterIdle(EntityPlayerSP p, boolean zero) {
        this.holding = false;
        this.groundStableTicks = 0;
        this.velocityTicksRemaining = 0;
        this.velocityInputBlockRemaining = 0;
        this.pulseTicksRemaining = 0;
        this.airReminderTicksRemaining = 0;
        this.pendingAirReminder = false;
        this.noReminderTicks = 0;
        if (zero) {
            if (p != null) {
                zeroAllMotion(p);
            }
            this.motionStored = false;
        }
        this.snapshotWatchState(p);
    }

    private void snapshotWatchState(EntityPlayerSP p) {
        if (p == null) {
            this.feetSampled = false;
            return;
        }
        this.watchPosX = p.posX;
        this.watchPosY = p.posY;
        this.watchPosZ = p.posZ;
        this.lastHurtTime = p.hurtTime;
        this.snapshotMotion();
        this.sampleFeet(p);
    }

    private boolean shouldActivate(EntityPlayerSP p) {
        if (p == null) {
            return false;
        }
        if (!p.onGround) {
            return true;
        }
        if (p.hurtTime > this.lastHurtTime || p.hurtTime > 0 && this.lastHurtTime == 0) {
            return true;
        }
        if (Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ) > MOTION_EPS) {
            return true;
        }
        double dx = p.posX - this.watchPosX;
        double dy = p.posY - this.watchPosY;
        double dz = p.posZ - this.watchPosZ;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 0.001) {
            return true;
        }
        return this.feetChanged(p);
    }

    private void sampleFeet(EntityPlayerSP p) {
        AxisAlignedBB bb = p.getEntityBoundingBox();
        if (bb == null || p.worldObj == null) {
            this.feetSampled = false;
            return;
        }
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.floor_double(bb.maxX - 1.0E-7);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.floor_double(bb.maxZ - 1.0E-7);
        int y = MathHelper.floor_double(bb.minY - 0.001);
        int n = (x1 - x0 + 1) * (z1 - z0 + 1);
        this.footXs = new int[n];
        this.footYs = new int[n];
        this.footZs = new int[n];
        this.footBlocks = new Block[n];
        this.footMetas = new int[n];
        this.footSolid = new boolean[n];
        World w = p.worldObj;
        int i = 0;
        for (int x = x0; x <= x1; ++x) {
            for (int z = z0; z <= z1; ++z) {
                IBlockState state = w.getBlockState(new BlockPos(x, y, z));
                Block b = state == null ? null : state.getBlock();
                this.footXs[i] = x;
                this.footYs[i] = y;
                this.footZs[i] = z;
                this.footBlocks[i] = b;
                this.footMetas[i] = b != null ? b.getMetaFromState(state) : 0;
                this.footSolid[i] = b != null && b.getMaterial().blocksMovement();
                ++i;
            }
        }
        this.feetSampled = true;
    }

    private boolean feetChanged(EntityPlayerSP p) {
        if (!this.feetSampled) {
            this.sampleFeet(p);
            return false;
        }
        AxisAlignedBB bb = p.getEntityBoundingBox();
        if (bb == null || p.worldObj == null) {
            return false;
        }
        int x0 = MathHelper.floor_double(bb.minX);
        int x1 = MathHelper.floor_double(bb.maxX - 1.0E-7);
        int z0 = MathHelper.floor_double(bb.minZ);
        int z1 = MathHelper.floor_double(bb.maxZ - 1.0E-7);
        int y = MathHelper.floor_double(bb.minY - 0.001);
        if ((x1 - x0 + 1) * (z1 - z0 + 1) != this.footXs.length) {
            return true;
        }
        World w = p.worldObj;
        int i = 0;
        for (int x = x0; x <= x1; ++x) {
            for (int z = z0; z <= z1; ++z) {
                if (this.footXs[i] != x || this.footYs[i] != y || this.footZs[i] != z) {
                    return true;
                }
                IBlockState state = w.getBlockState(new BlockPos(x, y, z));
                Block b = state == null ? null : state.getBlock();
                int meta = b != null ? b.getMetaFromState(state) : 0;
                boolean solid = b != null && b.getMaterial().blocksMovement();
                if (b != this.footBlocks[i] || meta != this.footMetas[i] || solid != this.footSolid[i]) {
                    return true;
                }
                ++i;
            }
        }
        return false;
    }

    private boolean canEnterIdle(EntityPlayerSP p) {
        if (!this.holding || p == null || !p.onGround) {
            return false;
        }
        if (this.velocityTicksRemaining > 0 || this.pulseTicksRemaining > 0 || this.airReminderTicksRemaining > 0) {
            return false;
        }
        if (this.keysDown || motionSpeed(p.motionX, p.motionY, p.motionZ) > MOTION_EPS) {
            return false;
        }
        return this.groundStableTicks >= GROUND_STABLE_TICKS;
    }

    private void updateHoldingState(EntityPlayerSP p) {
        if (!this.holding) {
            if (this.shouldActivate(p)) {
                this.beginHolding(p);
            } else {
                this.snapshotWatchState(p);
            }
            return;
        }
        this.groundStableTicks = p.onGround && this.velocityTicksRemaining <= 0 && this.pulseTicksRemaining <= 0
                && this.airReminderTicksRemaining <= 0 ? this.groundStableTicks + 1 : 0;
        if (this.canEnterIdle(p)) {
            this.enterIdle(p, true);
        }
    }

    private void sendAnchorC04Reminder(EntityPlayerSP p) {
        if (p == null || !this.anchorKnown) {
            return;
        }
        this.syncPlayerToAnchor(p);
        zeroAllMotion(p);
        PacketUtils.sendNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(this.anchorX, this.anchorY, this.anchorZ, p.onGround));
        this.noReminderTicks = 0;
    }

    private void startAirReminderPulse(EntityPlayerSP p, boolean now) {
        if (!now) {
            this.pendingAirReminder = true;
            return;
        }
        this.pendingAirReminder = false;
        this.airReminderTicksRemaining = 1;
        if (p != null) {
            this.applyStoredMotion();
        }
    }

    private void finishAirReminder(EntityPlayerSP p) {
        this.airReminderTicksRemaining = 0;
        this.captureAnchorFromPlayer(p);
        this.refreezeMotion(p);
        this.applyFrozenState(p);
        this.snapshotMotion();
        this.noReminderTicks = 0;
    }

    private boolean maybeForcePositionReminder(EntityPlayerSP p) {
        if (this.noReminderTicks < POSITION_REMINDER_TICKS) {
            return false;
        }
        if (p != null && p.onGround) {
            this.sendAnchorC04Reminder(p);
        } else {
            this.startAirReminderPulse(p, false);
        }
        return true;
    }

    /** Hooks.onSend, C03 only. Returns false to cancel. */
    @Override
    public boolean onSendPacketAlways(Packet<?> packet) {
        if (!this.holding || PacketUtils.sendingNoEvent == packet) {
            return true;
        }
        return FLAGLESS ? this.onSendPacketFlagless(packet) : this.onSendPacketFlag(packet);
    }

    private boolean onSendPacketFlag(Packet<?> packet) {
        if (!this.isActive() || !this.isFrozen() || !this.anchorKnown || !(packet instanceof C03PacketPlayer)) {
            return true;
        }
        C03PacketPlayer c = (C03PacketPlayer) packet;
        boolean moving = c instanceof C03PacketPlayer.C04PacketPlayerPosition || c instanceof C03PacketPlayer.C06PacketPlayerPosLook;
        if (this.ticksSinceTeleport <= 2 && moving) {
            return true;
        }
        if (c instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            return false;
        }
        if (c instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            sendLookPacket(c.getYaw(), c.getPitch(), c.isOnGround());
            return false;
        }
        return true;
    }

    private boolean onSendPacketFlagless(Packet<?> packet) {
        if (!this.isActive() || !this.anchorKnown || !(packet instanceof C03PacketPlayer)) {
            return true;
        }
        C03PacketPlayer c = (C03PacketPlayer) packet;
        if (!this.isFrozen()) {
            if (c instanceof C03PacketPlayer.C04PacketPlayerPosition || c instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                this.noReminderTicks = 0;
            }
            return true;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (c instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            if (this.ticksSinceTeleport <= 2) {
                this.noReminderTicks = 0;
                return true;
            }
            if (this.noReminderTicks >= POSITION_REMINDER_TICKS) {
                if (p != null && p.onGround) {
                    this.sendAnchorC04Reminder(p);
                } else {
                    this.startAirReminderPulse(p, false);
                }
            }
            return false;
        }
        if (c instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            if (this.ticksSinceTeleport <= 2) {
                this.noReminderTicks = 0;
                return true;
            }
            if (!this.maybeForcePositionReminder(p)) {
                float yaw = c.getYaw();
                float pitch = c.getPitch();
                if (this.lastSentLookValid && yaw == this.lastSentYaw && pitch == this.lastSentPitch) {
                    PacketUtils.sendNoEvent(new C03PacketPlayer(c.isOnGround()));
                } else {
                    sendLookPacket(yaw, pitch, c.isOnGround());
                    this.lastSentYaw = yaw;
                    this.lastSentPitch = pitch;
                    this.lastSentLookValid = true;
                }
                ++this.noReminderTicks;
            }
            return false;
        }
        if (this.maybeForcePositionReminder(p)) {
            return false;
        }
        if (c instanceof C03PacketPlayer.C05PacketPlayerLook) {
            this.lastSentYaw = c.getYaw();
            this.lastSentPitch = c.getPitch();
            this.lastSentLookValid = true;
        }
        ++this.noReminderTicks;
        return true;
    }

    /** Hooks.postPlayerInput (Stasis.onPrePlayerInput). */
    @Override
    public void onPostPlayerInputAlways() {
        if (mc.thePlayer == null) {
            return;
        }
        MovementInput in = mc.thePlayer.movementInput;
        if (!this.isEnabled()) {
            if (this.blockAfterDisableRemaining > 0) {
                blockInput(in);
            }
            return;
        }
        if (!nullCheck()) {
            return;
        }
        boolean down = Math.abs(in.moveForward) > MOTION_EPS || Math.abs(in.moveStrafe) > MOTION_EPS || in.jump;
        this.keysDown = down;
        if (!this.holding) {
            if (!down) {
                return;
            }
            this.beginHolding(mc.thePlayer);
        }
        if (this.velocityTicksRemaining <= 0 || this.velocityInputBlockRemaining > 0) {
            blockInput(in);
        }
        updateFrozen();
    }

    private void onClientTickStart() {
        if (!this.isEnabled()) {
            if (this.blockAfterDisableRemaining > 0) {
                --this.blockAfterDisableRemaining;
            }
            return;
        }
        if (!nullCheck()) {
            return;
        }
        EntityPlayerSP p = mc.thePlayer;
        this.updateHoldingState(p);
        if (this.ticksSinceTeleport < 1000) {
            ++this.ticksSinceTeleport;
        }
        if (this.holding) {
            if (FLAGLESS) {
                this.onClientTickStartFlagless(p);
            } else {
                this.onClientTickStartFlag(p);
            }
            this.tickReleaseWindows(p);
        }
        updateFrozen();
    }

    private void onClientTickStartFlag(EntityPlayerSP p) {
        if (this.isFrozen()) {
            this.clearMovementInputFlags();
            this.applyFrozenState(p);
        } else if (this.pulseTicksRemaining > 0 || this.velocityInputBlockRemaining > 0) {
            this.clearMovementInputFlags();
        }
    }

    private void onClientTickStartFlagless(EntityPlayerSP p) {
        if (this.isFrozen()) {
            this.clearMovementInputFlags();
            if (!this.pendingAirReminder && this.noReminderTicks < POSITION_REMINDER_TICKS) {
                this.applyFrozenState(p);
            } else if (!this.pendingAirReminder && p.onGround) {
                this.applyFrozenState(p);
                this.sendAnchorC04Reminder(p);
            } else {
                this.startAirReminderPulse(p, true);
            }
        } else if (this.airReminderTicksRemaining > 0) {
            this.clearMovementInputFlags();
        } else if (this.pulseTicksRemaining > 0 || this.velocityInputBlockRemaining > 0) {
            this.clearMovementInputFlags();
            this.noReminderTicks = 0;
        } else if (this.velocityTicksRemaining > 0) {
            this.noReminderTicks = 0;
        }
    }

    private void onClientTickEnd() {
        if (!this.isActive()) {
            return;
        }
        EntityPlayerSP p = mc.thePlayer;
        if (!this.holding) {
            this.snapshotWatchState(p);
        } else if (FLAGLESS && this.airReminderTicksRemaining > 0) {
            this.finishAirReminder(p);
        } else if (this.isFrozen()) {
            this.enforceFrozenMotion(p);
        } else {
            this.snapshotMotion();
        }
        updateFrozen();
    }

    private void tickReleaseWindows(EntityPlayerSP p) {
        if (this.pulseTicksRemaining > 0) {
            if (--this.pulseTicksRemaining == 0) {
                this.finishPulse(p);
            }
        } else if (this.velocityTicksRemaining > 0) {
            if (this.velocityInputBlockRemaining > 0) {
                --this.velocityInputBlockRemaining;
            }
            if (--this.velocityTicksRemaining == 0) {
                this.finishVelocityPassthrough(p);
            }
        } else if (this.holding && this.isPulseEnabled()) {
            this.tickPulseCooldown();
        }
    }

    private void finishPulse(EntityPlayerSP p) {
        this.captureAnchorFromPlayer(p);
        this.refreezeMotion(p);
        this.applyFrozenState(p);
        this.snapshotMotion();
        this.pulseCooldownRemaining = this.getPulseInterval();
    }

    private void finishVelocityPassthrough(EntityPlayerSP p) {
        this.velocityInputBlockRemaining = 0;
        this.captureAnchorFromPlayer(p);
        this.refreezeMotion(p);
        this.applyFrozenState(p);
        this.snapshotMotion();
    }

    private void enforceFrozenMotion(EntityPlayerSP p) {
        if (this.countsAsVelocityImpulse(p.motionX, p.motionY, p.motionZ) && VELOCITY_TICKS > 0) {
            this.openVelocityWindow();
        } else {
            zeroAllMotion(p);
            this.applyFrozenState(p);
            this.snapshotMotion();
        }
    }

    private void applyFrozenState(EntityPlayerSP p) {
        if (p != null && this.anchorKnown && this.holding) {
            zeroAllMotion(p);
            this.syncPlayerToAnchor(p);
        }
    }

    private boolean isFrozen() {
        if (!this.holding) {
            return false;
        }
        if (FLAGLESS) {
            return this.pulseTicksRemaining <= 0 && this.velocityTicksRemaining <= 0 && this.airReminderTicksRemaining <= 0;
        }
        return this.pulseTicksRemaining <= 0 && this.velocityTicksRemaining <= 0;
    }

    private void tickPulseCooldown() {
        if (this.pulseCooldownRemaining > 0) {
            --this.pulseCooldownRemaining;
        }
        if (this.pulseCooldownRemaining <= 0) {
            this.startPulse();
        }
    }

    private void startPulse() {
        this.pulseTicksRemaining = PULSE_TICKS;
        this.applyStoredMotion();
        this.pulseCooldownRemaining = this.getPulseInterval();
        this.groundStableTicks = 0;
    }

    private boolean countsAsVelocityImpulse(double x, double y, double z) {
        return motionSpeed(x, y, z) > MOTION_EPS && !this.isOnlyGravityMotion(x, y, z);
    }

    private boolean isOnlyGravityMotion(double x, double y, double z) {
        double dx = x - this.lastMotionX;
        double dy = y - this.lastMotionY;
        double dz = z - this.lastMotionZ;
        if (motionSpeed(dx, dy, dz) <= MOTION_EPS) {
            return false;
        }
        if (Math.abs(dx) > GRAVITY_MATCH_EPS || Math.abs(dz) > GRAVITY_MATCH_EPS) {
            return false;
        }
        return Math.abs(y - (this.lastMotionY - 0.08) * 0.98) < GRAVITY_MATCH_EPS;
    }

    private void setAnchor(double x, double y, double z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.anchorKnown = true;
    }

    private void captureAnchorFromPlayer(EntityPlayerSP p) {
        if (p != null) {
            this.setAnchor(p.posX, p.posY, p.posZ);
        }
    }

    private void syncPlayerToAnchor(EntityPlayerSP p) {
        if (p == null || !this.anchorKnown) {
            return;
        }
        double dx = p.posX - this.anchorX;
        double dy = p.posY - this.anchorY;
        double dz = p.posZ - this.anchorZ;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= 0.001) {
            return;
        }
        p.setPosition(this.anchorX, this.anchorY, this.anchorZ);
        p.lastTickPosX = this.anchorX;
        p.lastTickPosY = this.anchorY;
        p.lastTickPosZ = this.anchorZ;
        p.prevPosX = this.anchorX;
        p.prevPosY = this.anchorY;
        p.prevPosZ = this.anchorZ;
    }

    private static void zeroAllMotion(EntityPlayerSP p) {
        p.motionX = 0.0;
        p.motionY = 0.0;
        p.motionZ = 0.0;
    }

    private static void blockInput(MovementInput in) {
        in.moveForward = 0.0f;
        in.moveStrafe = 0.0f;
        in.jump = false;
    }

    private void storeMotionAndClear() {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) {
            return;
        }
        if (!this.motionStored) {
            this.storedMotionX = p.motionX;
            this.storedMotionY = p.motionY;
            this.storedMotionZ = p.motionZ;
            this.motionStored = true;
        }
        zeroAllMotion(p);
    }

    private void applyStoredMotion() {
        EntityPlayerSP p = mc.thePlayer;
        if (p != null && this.motionStored) {
            p.motionX = this.storedMotionX;
            p.motionY = this.storedMotionY;
            p.motionZ = this.storedMotionZ;
        }
    }

    private void refreezeMotion(EntityPlayerSP p) {
        if (p != null) {
            this.storedMotionX = p.motionX;
            this.storedMotionY = p.motionY;
            this.storedMotionZ = p.motionZ;
            this.motionStored = true;
            zeroAllMotion(p);
        }
    }

    private void restoreMotion() {
        this.applyStoredMotion();
        this.motionStored = false;
    }

    private void clearMovementInputFlags() {
        MovementInput in = mc.thePlayer.movementInput;
        if (in != null) {
            blockInput(in);
        }
    }

    private static void sendLookPacket(float yaw, float pitch, boolean ground) {
        PacketUtils.sendNoEvent(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, ground));
    }

    private void snapshotMotion() {
        EntityPlayerSP p = mc.thePlayer;
        if (p != null) {
            this.lastMotionX = p.motionX;
            this.lastMotionY = p.motionY;
            this.lastMotionZ = p.motionZ;
        }
    }

    private static double motionSpeed(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private boolean isLikelyFlagTeleport(EntityPlayerSP p, double x, double y, double z) {
        if (p == null) {
            return true;
        }
        double dx = x - p.posX;
        double dy = y - p.posY;
        double dz = z - p.posZ;
        return dx * dx + dy * dy + dz * dz <= 100.0;
    }
}
