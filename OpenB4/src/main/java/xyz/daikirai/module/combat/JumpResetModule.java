package xyz.daikirai.module.combat;

import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.RotationManager;
import xyz.daikirai.util.RotationSource;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import xyz.daikirai.client.BotCheck;
import xyz.daikirai.hook.Hooks;
import xyz.daikirai.util.PacketUtils;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

public class JumpResetModule extends Module {
    public static JumpResetModule instance;
    public static SliderSetting chance;
    public static ButtonSetting minimizeVertical, onlyWhileTargeting;
    public static boolean pending;
    private static boolean explosion;
    private static double kbX, kbZ;
    private static int kbY;

    /*
     * u20+ addition, not in the dump: "Hard reset", from the Hard Reset mode of OpenJade's Velocity
     * (github.com/Tewxx/OpenJade, jade/client/module/combat/Velocity.java, with its default "Delay",
     * "In air" delay condition and QueuedPacketDispatcher inbound hold). On own knockback while
     * airborne with a target in range, every inbound packet from the S12 on is held until the tick
     * you can jump (on ground) or "Hard reset max delay" passes, then replayed at the start of that
     * tick, so the jump below lands on the tick the knockback is applied.
     */
    public static ButtonSetting hardReset, aimJump;
    /** "Aim jump": a hit of yours went out this tick, so the look must stay on its target. */
    private static boolean otherAttack;
    /** "Aim jump": ticks of the aim left, the yaw against the knockback, and whether the jump has left the ground. */
    private static int aimLeft;
    private static float aimYaw;
    private static boolean aimAirborne;
    public static SliderSetting hardResetDelay, hardResetDistance, aimJumpTicks;
    private static final Object LOCK = new Object();
    private static final List<Packet<?>> held = new ArrayList<Packet<?>>();
    private static INetHandler heldHandler;
    /** System time the held S12 arrived; 0 while not holding. */
    private static volatile long heldSince;

    public JumpResetModule(int key) {
        super("Jump Reset", Category.combat, key);
        instance = this;
    }

    public void link() {
        chance = (SliderSetting) ModuleManager.setting(this, "Chance");
        minimizeVertical = (ButtonSetting) ModuleManager.setting(this, "Minimize vertical");
        onlyWhileTargeting = (ButtonSetting) ModuleManager.setting(this, "Only while targeting");
        hardReset = (ButtonSetting) ModuleManager.setting(this, "Hard reset");
        hardResetDelay = (SliderSetting) ModuleManager.setting(this, "Hard reset max delay");
        hardResetDistance = (SliderSetting) ModuleManager.setting(this, "Hard reset distance");
        aimJump = (ButtonSetting) ModuleManager.setting(this, "Aim jump");
        aimJumpTicks = (SliderSetting) ModuleManager.setting(this, "Aim jump ticks");
        aimJumpTicks.visibility = () -> aimJump.enabled;
        hardResetDelay.visibility = () -> hardReset.enabled;
        hardResetDistance.visibility = () -> hardReset.enabled;
    }

    public static boolean on() {
        return instance != null && instance.isEnabled();
    }

    @Override
    public boolean onReceivePacket(net.minecraft.network.Packet packet, net.minecraft.network.INetHandler handler) {
        if (!(packet instanceof S27PacketExplosion)) {
            return true;
        }
        S27PacketExplosion p = (S27PacketExplosion) packet;
        if (Minecraft.getMinecraft().thePlayer.getDistanceSq(p.getX(), p.getY(), p.getZ()) > 36.0) {
            return true;
        }
        explosion = true;
        return true;
    }

    @Override
    public void onDisable() {
        explosion = false;
        pending = false;
        aimLeft = 0;
        releaseHeld();
    }

    /**
     * Velocity.onPacketReceive + QueuedPacketDispatcher.onPacketReceive (inbound). Returns false when
     * the packet is held. Velocity.handleVelocityPacket starts the hold on own S12 when the reset
     * conditions pass (not burning, no jump/poison/wither) and the delay conditions pass ("In air",
     * target within "Distance to target"); S08 ends it (clearPendingReset + cancelInboundDelay).
     */
    public static boolean hardResetReceive(Packet<?> packet, INetHandler handler) {
        Minecraft mc = Minecraft.getMinecraft();
        if (packet == PacketUtils.reprocessing || !(handler instanceof INetHandlerPlayClient) || mc.isSingleplayer()) {
            return true;
        }
        List<Packet<?>> flush = null;
        synchronized (LOCK) {
            if (heldSince != 0L) {
                if (packet instanceof S08PacketPlayerPosLook || packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn) {
                    flush = drain();
                } else {
                    held.add(packet);
                    return false;
                }
            } else if (hardReset.enabled && packet instanceof S12PacketEntityVelocity && mc.thePlayer != null
                    && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()
                    && PacketUtils.isKnockback((S12PacketEntityVelocity) packet) && !mc.thePlayer.onGround
                    && !mc.thePlayer.isBurning() && !badPotion() && target() != null) {
                heldSince = System.currentTimeMillis();
                heldHandler = handler;
                held.add(packet);
                return false;
            }
        }
        if (flush != null) {
            replay(flush, handler);
        }
        return true;
    }

    /**
     * Velocity.onTickStart: let go when a delay condition fails (landed, target out of range) or the
     * held knockback is older than the maximum delay (PacketQueue.expireTimedOutTasks). This runs at
     * the start of Minecraft.runTick, as Jade's TickStartEvent does, so the replies to held
     * transactions go out before this tick's clicks and attacks. Releasing later (player pre-update)
     * put them after KillAura's swing/unblock and split Grim's tick (Post, Simulation, AntiKB, NoSlow).
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        long since = heldSince;
        if (e.phase == TickEvent.Phase.START) {
            otherAttack = false;
            // "Aim jump": count the hold down, and end it once the jump has landed.
            if (aimLeft > 0) {
                if (mc.thePlayer == null || mc.thePlayer.onGround && aimAirborne) {
                    aimLeft = 0;
                } else {
                    aimAirborne |= !mc.thePlayer.onGround;
                    --aimLeft;
                }
            }
        }
        if (e.phase != TickEvent.Phase.START || since == 0L) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            releaseHeld();
            return;
        }
        if (!hardReset.enabled || mc.thePlayer.isDead || mc.thePlayer.onGround || target() == null
                || System.currentTimeMillis() - since >= (long) hardResetDelay.getInput()) {
            releaseHeld();
        }
    }

    private static void releaseHeld() {
        List<Packet<?>> list;
        INetHandler handler;
        synchronized (LOCK) {
            handler = heldHandler;
            list = drain();
        }
        if (list != null && handler != null && Minecraft.getMinecraft().thePlayer != null) {
            replay(list, handler);
        }
    }

    private static List<Packet<?>> drain() {
        heldSince = 0L;
        heldHandler = null;
        if (held.isEmpty()) {
            return null;
        }
        List<Packet<?>> list = new ArrayList<Packet<?>>(held);
        held.clear();
        return list;
    }

    @SuppressWarnings({"rawtypes"})
    private static void replay(List<Packet<?>> list, INetHandler handler) {
        for (Packet packet : list) {
            try {
                PacketUtils.reprocessing = packet;
                Hooks.onReceive(packet, handler);
            } catch (Throwable ignored) {
            } finally {
                PacketUtils.reprocessing = null;
            }
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (e.entityPlayer == Minecraft.getMinecraft().thePlayer) {
            otherAttack = true;
        }
    }

    /**
     * u20+ addition, "Aim jump": EntityLivingBase.jump adds 0.2 along the movement yaw when sprinting (Hooks.onJump),
     * which only cancels knockback head-on if you face away from where it pushes you. On the tick the reset jump is
     * due, the yaw is turned so the boost points straight against the knockback (sin(yaw) = kbX / |kb|,
     * cos(yaw) = -kbZ / |kb|), and held for "Aim jump ticks" ticks or until the jump lands, so the air movement
     * keeps pushing that way too (onPostPlayerInput). A tick where one of your hits went out is skipped: Grim
     * checks the hit against that tick's look.
     */
    @Override
    public float[] getRotations(float yaw, float pitch) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!aimJump.enabled) {
            aimLeft = 0;
            return null;
        }
        if (pending && mc.thePlayer.onGround && kbX * kbX + kbZ * kbZ >= 1.0E-4) {
            aimYaw = (float) Math.toDegrees(Math.atan2(kbX, -kbZ));
            aimLeft = (int) aimJumpTicks.getInput();
            aimAirborne = false;
        }
        if (aimLeft <= 0 || otherAttack) {
            return null;
        }
        return new float[]{yaw + MathHelper.wrapAngleTo180_float(aimYaw - yaw), pitch};
    }

    /** Velocity.AuVw. */
    private static boolean badPotion() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer.isPotionActive(Potion.jump) || mc.thePlayer.isPotionActive(Potion.poison) || mc.thePlayer.isPotionActive(Potion.wither);
    }

    /** Velocity.getTargetFailureReason: TargetFinder.findTarget(distance squared). */
    private static EntityPlayer target() {
        Minecraft mc = Minecraft.getMinecraft();
        double range = hardResetDistance.getInput();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p != mc.thePlayer && p.isEntityAlive() && !BotCheck.isBot(p) && mc.thePlayer.getDistanceSqToEntity(p) <= range * range) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void onKnockback(S12PacketEntityVelocity p, long now) {
        if (explosion) {
            return;
        }
        if (minimizeVertical.enabled && (long) p.getMotionY() < 3000L) {
            return;
        }
        kbX = (double) p.getMotionX() / 8000.0;
        kbY = p.getMotionY();
        kbZ = (double) p.getMotionZ() / 8000.0;
        pending = true;
    }

    @Override
    public void onUpdateEntityActionState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (pending) {
            pending = false;
            // "Aim jump" only goes on through the air if the reset jump below happens.
            int aim = aimLeft;
            aimLeft = 0;
            if (!mc.thePlayer.onGround || !mc.thePlayer.isSprinting()) {
                return;
            }
            if (PlayerUtils.inLiquid() || mc.thePlayer.isRiding() || mc.thePlayer.isPotionActive(Potion.jump)) {
                return;
            }
            if (onlyWhileTargeting.enabled && !PlayerUtils.targetMatches(1)) {
                return;
            }
            if (chance.getInput() != 100.0 && !(Math.random() < chance.getInput() / 100.0)) {
                return;
            }
            if (!helps()) {
                return;
            }
            mc.thePlayer.setJumping(true);
            aimLeft = aim;
        }
    }

    /**
     * "Aim jump": for the ticks of the hold, walk straight forward along the aimed yaw (W, no strafe), so the
     * sprint jump and the air movement after it both push against the knockback. The aimed yaw is the movement
     * yaw (RotationManager.movementYaw) and the input is not remapped, so Grim simulates exactly this. Not while
     * sneaking, which would need the 0.3 input scale.
     */
    @Override
    public void onPostPlayerInput() {
        Minecraft mc = Minecraft.getMinecraft();
        if (aimLeft > 0 && RotationManager.rotating && RotationManager.source == RotationSource.JUMPRESET && !mc.thePlayer.isSneaking()) {
            mc.thePlayer.movementInput.moveForward = 1.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
        }
    }

    private static boolean helps() {
        if (kbX == 0.0 && kbZ == 0.0) {
            return false;
        }
        double kb = Math.sqrt(kbX * kbX + kbZ * kbZ);
        float yaw = MathHelper.wrapAngleTo180_float(RotationManager.movementYaw());
        double after = withJump(kbX, kbZ, yaw);
        double less = (kb - after) / kb;
        return less >= 0.03;
    }

    @Override
    public void onUpdate() {
        explosion = false;
    }

    private static double withJump(double x, double z, float yaw) {
        double r = Math.toRadians(yaw);
        double nx = x + -Math.sin(r) * 0.2;
        double nz = z + Math.cos(r) * 0.2;
        return Math.sqrt(nx * nx + nz * nz);
    }
}
