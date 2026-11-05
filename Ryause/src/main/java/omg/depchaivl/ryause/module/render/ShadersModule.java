package omg.depchaivl.ryause.module.render;

import omg.depchaivl.ryause.client.Chat;
import omg.depchaivl.ryause.module.Category;
import omg.depchaivl.ryause.module.Module;
import omg.depchaivl.ryause.module.setting.SliderSetting;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShadersModule extends Module {
    private static final Logger LOGGER = LogManager.getLogger("Ryause");
    public static ResourceLocation[] shaders;
    public static String[] names;
    public static SliderSetting shader;
    public static int index;
    private final Field shaderIndex;
    private int activeShaderIndex = -1;
    private boolean initializationFailed;

    public ShadersModule(int key) {
        super("Shaders", Category.render, key);
        Field indexField;
        try {
            indexField = ReflectionHelper.findField(EntityRenderer.class, "shaderIndex", "field_147713_ae");
            shaders = ReflectionHelper.getPrivateValue(EntityRenderer.class, mc.entityRenderer, "shaderResourceLocations", "field_147712_ad");
            if (shaders == null || shaders.length == 0) {
                throw new IllegalStateException("Minecraft shader list is empty");
            }
            names = new String[shaders.length];
            for (int i = 0; i < shaders.length; ++i) {
                names[i] = shaders[i].getResourcePath().replaceFirst("shaders/post/", "").split("\\.json")[0].toUpperCase();
            }
        } catch (Exception e) {
            initializationFailed = true;
            LOGGER.error("Failed to initialize the Shaders module", e);
            indexField = null;
        }
        shaderIndex = indexField;
        if (!initializationFailed) {
            index = Math.max(0, Math.min(index, shaders.length - 1));
            shader = new SliderSetting("Shader", "", "", index, 0, names.length - 1, 1.0, names, null);
            this.registerSetting(shader);
        }
    }

    @Override
    public void onDisable() {
        mc.entityRenderer.stopUseShader();
        activeShaderIndex = -1;
    }

    @Override
    public boolean canBeEnabled() {
        if (initializationFailed || shader == null || shaderIndex == null) {
            Chat.print("&b" + this.getName() + "&7: &cShader list could not be initialized.");
            return false;
        }
        if (!OpenGlHelper.shadersSupported) {
            Chat.print("&b" + this.getName() + "&7: &r" + "&cShaders not supported.");
            return false;
        }
        return true;
    }

    @Override
    public void guiUpdate() {
        if (shader != null) {
            index = Math.max(0, Math.min((int) shader.getInput(), shaders.length - 1));
        }
    }

    @Override
    public void onUpdate() {
        try {
            int selected = Math.max(0, Math.min((int) shader.getInput(), shaders.length - 1));
            boolean active = mc.entityRenderer.isShaderActive();
            if (!active || activeShaderIndex != selected || shaderIndex.getInt(mc.entityRenderer) != selected) {
                if (active) {
                    mc.entityRenderer.stopUseShader();
                }
                mc.entityRenderer.loadShader(shaders[selected]);
                if (mc.entityRenderer.isShaderActive()) {
                    shaderIndex.setInt(mc.entityRenderer, selected);
                    activeShaderIndex = selected;
                } else {
                    activeShaderIndex = -1;
                }
            }
        } catch (Exception e) {
            activeShaderIndex = -1;
            LOGGER.error("Failed to apply shader selection", e);
        }
    }
}
