package omg.depchaivl.ryause;

import me.ksyz.accountmanager.AccountManager;
import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.client.ChatCommands;
import omg.depchaivl.ryause.client.ClientConfig;
import omg.depchaivl.ryause.client.Hypixel;
import omg.depchaivl.ryause.client.Irc;
import omg.depchaivl.ryause.client.MiddleClickFriends;
import omg.depchaivl.ryause.client.Sounds;
import omg.depchaivl.ryause.gui.ClickGui;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.client.RenderDispatcher;
import omg.depchaivl.ryause.module.HudModule;
import omg.depchaivl.ryause.module.ModuleManager;
import omg.depchaivl.ryause.util.Zoom;
import omg.depchaivl.ryause.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod(modid = "clickgui", name = "Ryause", version = "1.0.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class ClickGuiMod {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        AccountManager.init();
        ModuleManager.init();
        omg.depchaivl.ryause.script.ScriptReflection.init();
        omg.depchaivl.ryause.script.ScriptEvents.bind(omg.depchaivl.ryause.script.ScriptDelegated.class);
        ClickGui.instance = new ClickGui();
        ClientConfig.load();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RenderDispatcher());
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new omg.depchaivl.ryause.client.KeystrokesGui.Command());
        MinecraftForge.EVENT_BUS.register(new omg.depchaivl.ryause.client.Keystrokes());
        omg.depchaivl.ryause.client.Keystrokes.instance = new omg.depchaivl.ryause.client.Keystrokes();
        omg.depchaivl.ryause.client.Keystrokes.load();
    }

    private static boolean isDown(int key) {
        return key != 0 && (key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key));
    }

    private final java.util.Map<Module, Boolean> held = new java.util.HashMap<Module, Boolean>();

    private boolean pollBind(Module m) {
        if (m.getKeycode() == 0 || m.getKeycode() == -1) {
            return false;
        }
        boolean wasHeld = Boolean.TRUE.equals(this.held.get(m));
        if (isDown(m.getKeycode())) {
            if (!wasHeld) {
                m.toggle();
                wasHeld = true;
            }
        } else {
            wasHeld = false;
        }
        this.held.put(m, wasHeld);
        return wasHeld;
    }

    private static boolean firstTick = true;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (omg.depchaivl.ryause.util.PacketQueue.active) {
            omg.depchaivl.ryause.util.PacketQueue.tick();
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (firstTick) {
            firstTick = false;
            omg.depchaivl.ryause.script.ScriptManager.reload();
            omg.depchaivl.ryause.client.Profiles.loadDefault();
            Zoom.find();
        }
        boolean profileHandled = false;
        for (Module m : ModuleManager.modules) {
            if (m.moduleCategory() == Category.profiles) {
                if (profileHandled || mc.currentScreen != null || !this.pollBind(m)) continue;
                profileHandled = true;
                continue;
            }
            if (mc.currentScreen == null) {
                this.pollBind(m);
            } else if (mc.currentScreen instanceof ClickGui) {
                m.guiUpdate();
            }
            if (!m.isEnabled()) continue;
            m.onUpdate();
        }
        if (ModuleManager.hud.isEnabled()) {
            HudModule.sort();
        }
        Irc.tick();
        Sounds.flush();
        if (omg.depchaivl.ryause.client.KeystrokesGui.open) {
            omg.depchaivl.ryause.client.KeystrokesGui.open = false;
            mc.displayGuiScreen(new omg.depchaivl.ryause.client.KeystrokesGui());
        }
        if (mc.thePlayer.isRiding()) {
            if (RotationManager.rotating) {
                RotationManager.updateBody(RotationManager.serverYaw);
            }
            RotationManager.wasRotating = RotationManager.rotating;
            RotationManager.setLast(RotationManager.serverYaw, RotationManager.serverPitch, RotationManager.lastPitch);
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || !Chat.inWorld()) {
            return;
        }
        String s = Chat.strip(e.message.getUnformattedText());
        if (s.isEmpty()) {
            return;
        }
        if (ChatCommands.pingSentAt > 0L) {
            ChatCommands.onChat(e, s);
        }
        for (String name : new String[]{"AntiBot", "AutoWho", "AutoRequeue", "Duels Stats", "NoFall", "Sky Wars", "Speed Builders"}) {
            Module m = ModuleManager.enabled(name);
            if (m != null) {
                m.onChat(e, s);
            }
        }
        if (s.charAt(0) == ' ') {
            boolean bedWars = ModuleManager.enabled("Bed Wars") != null;
            boolean bedEsp = ModuleManager.enabled("BedESP") != null && omg.depchaivl.ryause.module.render.BedESPModule.optimizeForBedWars.isToggled();
            boolean autoWho = ModuleManager.enabled("AutoWho") != null && omg.depchaivl.ryause.module.minigames.AutoWhoModule.runBedWarsInGame.isToggled();
            if (bedWars || bedEsp || autoWho) {
                omg.depchaivl.ryause.module.minigames.BedWarsModule.onGameStartChat(e, s, bedWars, bedEsp, autoWho);
            }
        }
                if (Hypixel.onChat(s)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onJoinQueue(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            omg.depchaivl.ryause.util.PacketQueue.onJoinWorld();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            Hypixel.joinedAt = System.currentTimeMillis();
            Hypixel.pendingLocraw = true;
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        omg.depchaivl.ryause.util.ClickUtils.onMouse(e);
    }
}
