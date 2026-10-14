package xyz.daikirai.module.player;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.MoveUtils;
import xyz.daikirai.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;

/**
 * Scaffold's Rotation "Snap": the cryptix Scaffold (a/wq) ported through the ScaffoldPort script.
 * While {@link #enabled()} ScaffoldModule hands its hooks to this class instead of running its own logic.
 *
 * Profiles stay loadable both ways: "Snap" is past Rotation's compatMax, so it is saved as "Rotation (ext)" with the
 * last other rotation under "Rotation", and the "Snap ..." settings are new keys older builds ignore.
 *
 * Fast modes: Disabled; Telly; Watchdog3 (snap), which runs the original Watchdog2 telly while RMB is held when
 * "Snap telly RMB" is on. Recovered_it.m50() in the source is taken as the walking direction ({@link #moveYaw()}).
 */
public final class SnapScaffold {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static SliderSetting mode, rotations, rotationSpeed, tower, timerTicks, sneakEvery;
    public static ButtonSetting tellyRmb;

    private static final String[] MODES = {"Disabled", "Telly", "Watchdog3"};
    private static final String[] ROTATIONS = {"HitVec", "Backward", "Offset", "Raycast", "Raycast2"};
    private static final String[] TOWERS = {"Disabled", "Watchdog", "Watchdog Low"};

    private static final float[] YAW_OFFSETS = offsets(180);    // f640
    private static final float[] PITCH_OFFSETS = offsets(90);   // f641
    private static final double REACH = 4.5;

    private static int keepYLevel;            // f634
    private static int prevSlot = -1;         // f635
    private static int placeTimer;            // f636
    private static int timerCount;            // f637
    private static int blockSlot = -1;        // f638
    private static float rotYaw, rotPitch;    // f624, f625: rotation sent to the server
    private static float lastYaw, lastPitch;  // f626, f627: last target rotation
    private static final float[] target = new float[2];  // f639
    private static boolean reached;           // f629
    private static boolean stepped;           // f630
    private static boolean wasOnGround;       // f631
    private static boolean timerActive;       // f633
    private static float lastPlacedDelta = -1f;
    private static boolean placedThisTick;
    private static boolean tellyJump;         // this jump started as a telly: finish it as one

    private SnapScaffold() {
    }

    private static float[] offsets(int max) {
        ArrayList<Float> l = new ArrayList<Float>();
        l.add(0f);
        for (int i = 1; i <= max; i += (i < 20 ? 1 : 2)) {
            l.add((float) -i);
            l.add((float) i);
        }
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    // ---------------------------------------------------------------- settings

    private static String option(SliderSetting s, String[] options) {
        return options[Math.max(0, Math.min((int) s.getInput(), options.length - 1))];
    }

    /** index of "Snap" in Scaffold's Rotation setting */
    public static final int SNAP_ROTATION = 6;

    public static boolean enabled() {
        return ScaffoldModule.rotationSetting != null && (int) ScaffoldModule.rotationSetting.getInput() == SNAP_ROTATION;
    }

    private static String fast() { return option(mode, MODES); }
    private static String rotMode() { return option(rotations, ROTATIONS); }
    private static String towerMode() { return option(tower, TOWERS); }
    private static boolean rmbDown() { return Mouse.isButtonDown(1) && mc.currentScreen == null; }

    /** the mode actually running: Watchdog3 + telly RMB runs the Watchdog2 telly while RMB is held or a telly jump is on */
    private static String running() {
        String fm = fast();
        return fm.equals("Watchdog3") && tellyRmb.isToggled() && (rmbDown() || tellyJump) ? "Watchdog2" : fm;
    }

    private static boolean tellying() {
        String fm = fast();
        if (fm.equals("Telly")) return !tellyRmb.isToggled() || rmbDown();
        return fm.equals("Watchdog3") && tellyRmb.isToggled() && rmbDown();
    }

    // ---------------------------------------------------------------- lifecycle

    public static void onEnable() {
        keepYLevel = (int) (mc.thePlayer.posY - 1.0);
        prevSlot = mc.thePlayer.inventory.currentItem;
        rotYaw = mc.thePlayer.rotationYaw;
        lastPlacedDelta = -1f;
        lastYaw = moveYaw() - 180f;
        lastPitch = 83f;
        rotPitch = 83f;
        // the original starts this false, which makes Watchdog3 jump on enable
        wasOnGround = true;
        timerCount = 0;
        blockSlot = -1;
        tellyJump = false;
    }

    public static void onDisable() {
        if (prevSlot != -1) mc.thePlayer.inventory.currentItem = prevSlot;
        MoveUtils.setTimer(1.0f);
        timerActive = false;
        prevSlot = -1;
    }

    // ---------------------------------------------------------------- hooks

    /** KeybindEvent: keep the block slot selected */
    public static void onPreUpdate() {
        if (blockSlot != -1 && mc.thePlayer.inventory.currentItem != blockSlot) mc.thePlayer.inventory.currentItem = blockSlot;
    }

    /** RotationEvent; yaw/pitch are the last sent rotation (RotationManager.lastYaw/lastPitch) */
    public static float[] getRotations(float sentYaw, float sentPitch) {
        boolean onGround = mc.thePlayer.onGround;
        boolean jump = PlayerUtils.jumpKeyDown();
        if (placeTimer > 0) placeTimer--;
        placedThisTick = false;

        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        if (onGround) tellyJump = fast().equals("Watchdog3") && tellying();

        String fm = running();
        if (tellying() && onGround) {
            keepYLevel = (int) (py - 1.0);
            rotYaw = moveYaw();
            wasOnGround = true;
            return send(sentYaw, rotYaw, sentPitch);
        }

        stepped = false;
        int[] below = {floor(px), floor(py) - 1, floor(pz)};
        boolean towerOn = !towerMode().equals("Disabled");
        boolean telly = fm.equals("Watchdog2");
        if (telly && !(towerOn && jump)) below[1] = keepYLevel;

        // blocks we need filled: under us now and, for the telly, under where we'll be in 1-2 ticks
        ArrayList<int[]> wanted = new ArrayList<int[]>();
        addWanted(wanted, below);
        if (telly) {
            for (int t = 1; t <= 2; t++) {
                addWanted(wanted, new int[]{floor(px + mc.thePlayer.motionX * t), below[1], floor(pz + mc.thePlayer.motionZ * t)});
            }
        }

        if (!wanted.isEmpty()) {
            blockSlot = bestBlockSlot();
            if (blockSlot != -1) {
                Object[] data = placeData(wanted.get(0));
                if (data != null) {
                    computeRotation((int[]) data[0], (EnumFacing) data[1], (double[]) data[2]);
                    stepRotation(target[0], target[1]);
                    lastYaw = target[0];
                    lastPitch = target[1];
                    if (towerOn && jump && onGround) reached = false;
                }
                boolean towerHold = towerOn && jump && onGround;
                // telly: place as soon as the rotation being sent looks at a face that fills a wanted block
                Object[] rayPlace = telly && !towerHold ? rayPlacement(wanted) : null;
                if (rayPlace != null) {
                    place((int[]) rayPlace[0], (EnumFacing) rayPlace[1], (double[]) rayPlace[2]);
                } else if (data != null && reached) {
                    int[] sup = (int[]) data[0];
                    EnumFacing face = (EnumFacing) data[1];
                    double[] hit = (double[]) data[2];
                    MovingObjectPosition ray = raycast(rotYaw, rotPitch);
                    if (ray != null && ray.sideHit == face && same(blockOf(ray), sup)) hit = point(ray);
                    place(sup, face, hit);
                }
            }
        }

        if (fm.equals("Watchdog3")) {
            float m = moveYaw();
            float diff = Math.abs(wrap(m + 45f - rotYaw));
            if (placeTimer == (diff > 60f ? 8 : 9) && !jump && wasOnGround && onGround) rotYaw = m + 45f;
        } else {
            stepRotation(lastYaw, lastPitch);
        }
        if (onGround) keepYLevel = (int) (py - 1.0);

        if (towerOn) {
            if (onGround && jump) rotYaw = moveYaw();
            if (towerMode().equals("Watchdog Low")) {
                if (!onGround && mc.thePlayer.motionY < -0.1 && jump && timerCount < timerTicks.getInput()) {
                    MoveUtils.setTimer(2.0f);
                    timerCount++;
                    timerActive = true;
                } else if (timerActive) {
                    MoveUtils.setTimer(1.0f);
                    timerActive = false;
                }
            }
        }

        if (!fm.equals("Watchdog3")) wasOnGround = onGround;
        return send(sentYaw, rotYaw, rotPitch);
    }

    /**
     * Final rotation. Keeps the yaw continuous with the last one sent (Grim AimModulo360 flags a |delta| > 320
     * after a small turn) and nudges a placement tick whose |delta| repeats the previous placement's
     * (Grim DuplicateRotPlace: |delta| > 2 equal within 0.0001).
     */
    private static float[] send(float sentYaw, float yaw, float pitch) {
        float out = sentYaw + wrap(yaw - sentYaw);
        if (placedThisTick) {
            float delta = Math.abs(out - sentYaw);
            if (delta > 2f && lastPlacedDelta >= 0f && Math.abs(delta - lastPlacedDelta) < 0.01f) {
                float nudge = (float) (0.02 + Math.random() * 0.03);
                out += Math.signum(out - sentYaw) * (Math.random() < 0.5 ? nudge : -nudge);
                delta = Math.abs(out - sentYaw);
            }
            lastPlacedDelta = delta;
        }
        rotYaw = sentYaw + wrap(rotYaw - sentYaw);
        return new float[]{out, pitch};
    }

    /** MotionEvent: pay back the Watchdog Low timer ticks at half speed once jump is released */
    public static void onPreMotion(MotionEvent e) {
        if (PlayerUtils.jumpKeyDown()) return;
        if (timerCount > 0) {
            timerCount--;
            MoveUtils.setTimer(0.5f);
            timerActive = true;
        } else if (timerActive) {
            MoveUtils.setTimer(1.0f);
            timerActive = false;
        }
    }

    /** PostInputEvent */
    public static void onPostPlayerInput() {
        if (running().equals("Watchdog3") && !wasOnGround && placeTimer != 10) {
            PlayerUtils.setJump(true);
            wasOnGround = true;
        }
        if (tellying()) {
            if (mc.thePlayer.movementInput.moveForward != 0 || mc.thePlayer.movementInput.moveStrafe != 0) PlayerUtils.setJump(true);
        }
        int every = (int) sneakEvery.getInput();   // the original's Sneak + "every (Sneak Delay + 1) ticks"; 0 = off
        if (every > 0 && mc.thePlayer.ticksExisted % every == 0) {
            PlayerUtils.setSneak(true);
        }
    }

    // ---------------------------------------------------------------- rotation

    /** pm$110 */
    private static void stepRotation(float yaw, float pitch) {
        if (stepped) return;
        stepped = true;
        String fm = running();
        float speed = (float) (rotationSpeed.getInput() * 20.0 - Math.random());
        // telly: always the fast (boost) rotation; the original's 34/tick crawl got flagged
        if (fm.equals("Watchdog2")) speed = (float) (129.0 - Math.random());
        rotYaw += clamp(wrap(yaw - rotYaw), -speed, speed);
        float py = mc.thePlayer.rotationYaw;
        rotYaw = py + wrap(rotYaw - py);
        rotPitch += clamp(wrap(pitch - rotPitch), -speed, speed);
        reached = Math.abs(wrap(rotYaw - yaw)) <= 0.1 && Math.abs(wrap(rotPitch - pitch)) <= 0.1;
        if (fm.equals("Watchdog3")) {
            rotYaw = yaw;
            reached = true;
        }
    }

    /** pm$111 */
    private static void computeRotation(int[] sup, EnumFacing face, double[] hit) {
        double[] eye = eye();
        double dx = hit[0] - eye[0], dy = hit[1] - eye[1], dz = hit[2] - eye[2];
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        String rm = rotMode();
        if (rm.equals("Backward")) {
            target[0] = moveYaw() - 180f;
            target[1] = pitch;
        } else if (rm.equals("Offset")) {
            float m = moveYaw();
            target[0] = (m % 90f > 45f ? m - 45f : m + 45f) - 180f;
            target[1] = 83f;
        } else if (rm.equals("Raycast")) {
            raycastSearch(sup, face, moveYaw() - 180f);
        } else if (rm.equals("Raycast2")) {
            raycastSearch(sup, face, lastYaw);
        } else {
            target[0] = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            target[1] = pitch;
        }
    }

    /** pm$112 */
    private static void raycastSearch(int[] sup, EnumFacing face, float baseYaw) {
        boolean wd3 = running().equals("Watchdog3");
        if (wd3) baseYaw = moveYaw();
        float bestYaw = 0, bestPitch = 0, bestDiff = 0;
        for (float yo : YAW_OFFSETS) {
            for (float po : PITCH_OFFSETS) {
                float y = baseYaw + yo;
                float p = clamp(po, -90f, 90f);
                MovingObjectPosition hit = raycast(y, p);
                if (hit == null || hit.sideHit != face || !same(blockOf(hit), sup)) continue;
                if (wd3) {
                    float diff = Math.abs(wrap(y - moveYaw() + 180f));
                    if (diff > bestDiff) {
                        bestDiff = diff;
                        bestYaw = y;
                        bestPitch = p;
                    }
                    if (diff > 90f) continue;
                }
                target[0] = y;
                target[1] = p;
                return;
            }
        }
        if (wd3 && bestDiff != 0f) {
            target[0] = bestYaw;
            target[1] = bestPitch;
            return;
        }
        reached = false;
        stepped = true;
        target[0] = lastYaw;
        target[1] = lastPitch;
    }

    // ---------------------------------------------------------------- placement

    private static final int[][] DIRS = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};   // below + 4 sides

    /** the face on the neighbour at offset d that points back at the block to fill */
    private static EnumFacing faceToward(int[] d) {
        if (d[1] == -1) return EnumFacing.UP;
        if (d[0] == 1) return EnumFacing.WEST;
        if (d[0] == -1) return EnumFacing.EAST;
        if (d[2] == 1) return EnumFacing.NORTH;
        return EnumFacing.SOUTH;
    }

    /**
     * {support, face, hitPoint} for pos: a solid block touching it; for a diagonal gap a stepping block toward
     * where we're moving; otherwise the original getPlaceData search.
     */
    private static Object[] placeData(int[] pos) {
        if (!isAir(pos)) return null;
        Object[] best = adjacentPlacement(pos);
        if (best != null) return best;
        double[] ahead = {mc.thePlayer.posX + mc.thePlayer.motionX * 2, pos[1] + 0.5, mc.thePlayer.posZ + mc.thePlayer.motionZ * 2};
        double bestD = Double.MAX_VALUE;
        for (int[] d : DIRS) {
            int[] step = {pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]};
            if (!isAir(step) || playerInside(step)) continue;
            Object[] pl = adjacentPlacement(step);
            if (pl == null) continue;
            double dd = distSq(ahead, new double[]{step[0] + 0.5, step[1] + 0.5, step[2] + 0.5});
            if (dd < bestD) {
                bestD = dd;
                best = pl;
            }
        }
        return best != null ? best : searchPlacement(pos);
    }

    private static void addWanted(ArrayList<int[]> wanted, int[] pos) {
        if (!isAir(pos) || playerInside(pos)) return;
        for (int[] w : wanted) if (same(w, pos)) return;
        wanted.add(pos);
    }

    /** the sent rotation's ray, if its face fills a wanted block or a stepping block next to one */
    private static Object[] rayPlacement(ArrayList<int[]> wanted) {
        MovingObjectPosition ray = raycast(rotYaw, rotPitch);
        if (ray == null || ray.sideHit == EnumFacing.DOWN) return null;
        int[] sup = blockOf(ray);
        BlockPos d = ray.getBlockPos().offset(ray.sideHit);
        int[] dest = {d.getX(), d.getY(), d.getZ()};
        if (!isAir(dest) || playerInside(dest)) return null;
        for (int[] w : wanted) {
            if (same(dest, w) || dest[1] == w[1] && Math.abs(dest[0] - w[0]) + Math.abs(dest[2] - w[2]) == 1) {
                return new Object[]{sup, ray.sideHit, point(ray)};
            }
        }
        return null;
    }

    private static void place(int[] sup, EnumFacing face, double[] hit) {
        if (mc.thePlayer.inventory.currentItem != blockSlot) mc.thePlayer.inventory.currentItem = blockSlot;
        ItemStack held = mc.thePlayer.getHeldItem();
        BlockPos against = new BlockPos(sup[0], sup[1], sup[2]);
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, against, face, new Vec3(hit[0], hit[1], hit[2]))) {
            PlayerUtils.swing(ScaffoldModule.silentSwing.isToggled(), true);
            ScaffoldModule.markPlaced(against.offset(face));
            placeTimer = 10;
            placedThisTick = true;
        }
    }

    /** closest reachable face of a solid block touching pos */
    private static Object[] adjacentPlacement(int[] pos) {
        double[] eye = eye();
        Object[] best = null;
        double bestD = REACH * REACH;
        for (int[] d : DIRS) {
            int[] sup = {pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]};
            if (isAir(sup)) continue;
            EnumFacing face = faceToward(d);
            double[] hit = facePoint(sup, face, eye);
            double dd = distSq(eye, hit);
            if (dd < bestD) {
                bestD = dd;
                best = new Object[]{sup, face, hit};
            }
        }
        return best;
    }

    /** the point on a block face nearest the eye, 0.1 inside the edges */
    private static double[] facePoint(int[] b, EnumFacing face, double[] eye) {
        double x = clampD(eye[0], b[0] + 0.1, b[0] + 0.9);
        double y = clampD(eye[1], b[1] + 0.1, b[1] + 0.9);
        double z = clampD(eye[2], b[2] + 0.1, b[2] + 0.9);
        switch (face) {
            case UP: y = b[1] + 1.0; break;
            case EAST: x = b[0] + 1.0; break;
            case WEST: x = b[0]; break;
            case SOUTH: z = b[2] + 1.0; break;
            case NORTH: z = b[2]; break;
            default: break;
        }
        return new double[]{x, y, z};
    }

    /** original getPlaceData: nearest solid block in a 9x3x9 box, face along the dominant axis */
    private static Object[] searchPlacement(int[] pos) {
        double best = Double.MAX_VALUE;
        int[] bestPos = null;
        EnumFacing bestFace = null;
        for (int ox = -4; ox <= 4; ox++) {
            for (int oy = -2; oy <= 0; oy++) {
                for (int oz = -4; oz <= 4; oz++) {
                    int[] b = {pos[0] + ox, pos[1] + oy, pos[2] + oz};
                    if (isAir(b)) continue;
                    int ax = Math.abs(ox), ay = Math.abs(oy), az = Math.abs(oz);
                    EnumFacing face;
                    if (ax >= ay && ax >= az) face = ox < 0 ? EnumFacing.EAST : EnumFacing.WEST;
                    else if (ay >= ax && ay >= az) face = oy < 0 ? EnumFacing.UP : EnumFacing.DOWN;
                    else face = oz < 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
                    if (face == EnumFacing.DOWN) continue;
                    double dx = mc.thePlayer.posX - (b[0] + 0.5), dy = mc.thePlayer.posY - (b[1] + 0.5), dz = mc.thePlayer.posZ - (b[2] + 0.5);
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best) {
                        best = d;
                        bestPos = b;
                        bestFace = face;
                    }
                }
            }
        }
        return bestPos == null ? null : new Object[]{bestPos, bestFace, new double[]{bestPos[0] + 0.5, bestPos[1] + 0.5, bestPos[2] + 0.5}};
    }

    /** pm$109: hotbar slot with the biggest stack of placeable blocks */
    private static int bestBlockSlot() {
        int slot = -1, most = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && ItemUtils.isScaffoldBlock(s) && s.stackSize > most) {
                most = s.stackSize;
                slot = i;
            }
        }
        return slot;
    }

    // ---------------------------------------------------------------- helpers

    /** Recovered_it.m50(): camera yaw turned by the held movement keys */
    private static float moveYaw() {
        float yaw = mc.thePlayer.rotationYaw;
        int fwd = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
        int left = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0) - (mc.gameSettings.keyBindRight.isKeyDown() ? 1 : 0);
        if (fwd < 0) yaw += 180f;
        if (left != 0) yaw += -90f * (fwd == 0 ? 1f : 0.5f * Math.signum(fwd)) * Math.signum(left);
        return yaw;
    }

    private static MovingObjectPosition raycast(float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        float yr = (float) Math.toRadians(yaw), pr = (float) Math.toRadians(pitch);
        double x = -Math.sin(yr) * Math.cos(pr), y = -Math.sin(pr), z = Math.cos(yr) * Math.cos(pr);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, eye.addVector(x * REACH, y * REACH, z * REACH), false, false, false);
        return hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK ? null : hit;
    }

    private static double[] eye() {
        return new double[]{mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ};
    }

    private static int[] blockOf(MovingObjectPosition hit) {
        BlockPos b = hit.getBlockPos();
        return new int[]{b.getX(), b.getY(), b.getZ()};
    }

    private static double[] point(MovingObjectPosition hit) {
        return new double[]{hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord};
    }

    private static boolean playerInside(int[] b) {
        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        return x + 0.3 > b[0] && x - 0.3 < b[0] + 1 && y + mc.thePlayer.height > b[1] && y < b[1] + 1 && z + 0.3 > b[2] && z - 0.3 < b[2] + 1;
    }

    private static boolean isAir(int[] b) {
        return mc.theWorld.isAirBlock(new BlockPos(b[0], b[1], b[2]));
    }

    private static boolean same(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    private static int floor(double d) {
        return (int) Math.floor(d);
    }

    private static double distSq(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static float wrap(float f) {
        f %= 360f;
        if (f >= 180f) f -= 360f;
        if (f < -180f) f += 360f;
        return f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
