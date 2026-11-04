package omg.depchaivl.ryause.client;

import omg.depchaivl.ryause.gui.Console;
import omg.depchaivl.ryause.module.render.BedESPModule;
import omg.depchaivl.ryause.module.render.FallViewModule;
import omg.depchaivl.ryause.module.render.NametagsModule;
import omg.depchaivl.ryause.module.render.PlayerESPModule;
import omg.depchaivl.ryause.module.setting.ButtonSetting;
import omg.depchaivl.ryause.util.Render3D;
import omg.depchaivl.ryause.util.Strings;
import omg.depchaivl.ryause.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class RenderDispatcher {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean playerEsp() {
        return PlayerESPModule.instance != null && PlayerESPModule.instance.isEnabled()
                && (!PlayerESPModule.disableInLobby.isToggled() || Hypixel.state != Hypixel.State.LOBBY);
    }

    public static boolean nametags() {
        return NametagsModule.instance != null && NametagsModule.instance.isEnabled()
                && (!NametagsModule.disableInLobby.isToggled() || Hypixel.state != Hypixel.State.LOBBY);
    }

    public static boolean visible(EntityLivingBase e, ButtonSetting showInvis) {
        if (showInvis != null && !showInvis.isToggled() && e.isInvisible()) {
            return false;
        }
        return !BotCheck.aboveSpawn(e) && Render3D.isVisible(e) && !BotCheck.isBot(e);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Chat.inWorld()) {
            return;
        }
        float pt = e.partialTicks;
        boolean esp = playerEsp();
        boolean tags = nametags();
        boolean murder = omg.depchaivl.ryause.module.minigames.MurderMysteryModule.inGame;
        boolean track = ChatCommands.tracking != null;
        boolean beds = BedESPModule.active;
        if (tags || beds) {
            Render3D.updateCameraPos();
        }
        if (beds) {
            BedESPModule.render(pt);
        }
        if (esp && PlayerESPModule.isShader()) {
            PlayerESPModule.renderShader(pt);
        }
        if (esp || tags || murder || track) {
            EntityPlayer self = null;
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                if (p == mc.thePlayer) {
                    self = p;
                    continue;
                }
                if (p.deathTime != 0) continue;
                if (esp && visible(p, PlayerESPModule.showInvis)) {
                    PlayerESPModule.render(p, pt);
                }
                if (tags && visible(p, NametagsModule.showInvis)) {
                    NametagsModule.render(p, pt);
                }
                if (murder) {
                    omg.depchaivl.ryause.module.minigames.MurderMysteryModule.render(p, pt);
                }
                if (!track || !p.getName().equals(ChatCommands.tracking)) continue;
                Render3D.entityBox((Entity) p, 2, Theme.defaultTheme.getRGB(0.0), pt);
                if (esp || tags || murder) continue;
                break;
            }
            if (self != null && mc.gameSettings.thirdPersonView != 0 && !self.isPlayerSleeping()) {
                if (esp && PlayerESPModule.renderSelf.isToggled()) {
                    PlayerESPModule.render(self, pt);
                }
                if (tags && NametagsModule.renderSelf.isToggled()) {
                    NametagsModule.render(self, pt);
                }
            }
        }
        BotCheck.renderWatchdog(pt);
        omg.depchaivl.ryause.module.player.ScaffoldModule.renderPlaced();
    }

    @SubscribeEvent
    public void onSpecials(RenderLivingEvent.Specials.Pre<?> e) {
        if (PlayerESPModule.shaderPass || e.x == 0.0 && e.y == 0.0 && e.z == 0.0) {
            e.setCanceled(true);
        } else if (nametags() && Chat.inWorld() && visible(e.entity, NametagsModule.showInvis)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            Irc.countFrame();
            Render3D.updateFrustum(e.renderTickTime);
        } else if (Chat.inWorld()) {
            omg.depchaivl.ryause.module.combat.KillAuraModule.onRenderTick();
            omg.depchaivl.ryause.module.minigames.SpeedBuildersModule.render();
            boolean visible = Overlay.canRender();
            if (visible) {
                omg.depchaivl.ryause.module.player.ScaffoldModule.renderCount();
                omg.depchaivl.ryause.module.player.FastPlaceModule.renderCount();
                omg.depchaivl.ryause.module.player.AutoPlaceModule.renderCount();
                omg.depchaivl.ryause.module.network.BlinkModule.renderTicks();
                omg.depchaivl.ryause.module.network.FreezeModule.render();
                FallViewModule.render();
                if (Console.bps) {
                    mc.fontRendererObj.drawString(bps(mc.thePlayer, 2) + "bps", 20.0f, 20.0f, -1, true);
                }
            }
            InfoHud.render(visible);
        }
    }

    public static double bps(Entity e, int places) {
        double dx = e.posX - e.prevPosX;
        double dz = e.posZ - e.prevPosZ;
        double d = Math.sqrt(dx * dx + dz * dz) * 20.0;
        return Strings.round(d, places);
    }
}
