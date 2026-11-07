package omg.depchaivl.ryause.mixin;

import com.hrznstudio.emojiful.api.Emoji;
import com.hrznstudio.emojiful.Constants;
import com.hrznstudio.emojiful.config.EmojifulConfig;
import com.hrznstudio.emojiful.render.EmojiFontHelper;
import com.hrznstudio.emojiful.render.EmojiRenderer;
import net.minecraft.client.gui.FontRenderer;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;

/**
 * Mixin into FontRenderer to intercept drawString and inject emoji quads.
 *
 * Re-entrancy guard: when we call drawString recursively for a plain-text segment,
 * we prefix the string with GUARD_CHAR (U+E000, a private-use Unicode char).
 * The injector detects this and immediately returns, letting vanilla draw the text
 * normally — but first we strip the guard char via a ModifyArg so it is never
 * visible on screen.
 *
 * Chat vs Signs:
 * - Chat history: rendered via GuiNewChat → drawStringWithShadow → drawString(s, x, y, color, true)
 * - Signs: rendered via TileEntitySignRenderer → drawString(s, x, y, color, false)
 * Both paths hit drawString(String, float, float, int, boolean) which is our injection point.
 */
@Mixin(FontRenderer.class)
public abstract class MixinEmojiFontRenderer {

    // We need to call renderString directly for segment rendering to avoid
    // re-triggering our own injector. renderString is private so we use @Shadow.
    @Shadow
    private int renderString(String text, float x, float y, int color, boolean dropShadow) {
        throw new AssertionError();
    }

    @Shadow
    private void resetStyles() {
        throw new AssertionError();
    }

    @Inject(
        method = "drawString(Ljava/lang/String;FFIZ)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void emojiful_drawString(String text, float x, float y, int color, boolean dropShadow,
                                      CallbackInfoReturnable<Integer> cir) {
        if (!EmojifulConfig.renderEmoji || Constants.error) return;
        if (text == null || text.isEmpty()) return;
        // Re-entrant guard — this call came from us for a plain segment, skip it
        if (text.charAt(0) == EmojiFontHelper.GUARD_CHAR) {
            // Strip our guard char and let vanilla render the rest normally
            // We do this by replacing the return value — but we still need to cancel
            // and call renderString ourselves to get the right return value.
            // drawString calls resetStyles then renderString (once or twice for shadow).
            String clean = text.substring(1);
            net.minecraft.client.renderer.GlStateManager.enableAlpha();
            resetStyles();
            int result;
            if (dropShadow) {
                result = renderString(clean, x + 1.0f, y + 1.0f, color, true);
                result = Math.max(result, renderString(clean, x, y, color, false));
            } else {
                result = renderString(clean, x, y, color, false);
            }
            cir.setReturnValue(result);
            return;
        }

        Pair<String, HashMap<Integer, Emoji>> cached;
        try {
            cached = EmojiFontHelper.RECENT_STRINGS.get(text);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return;
        }

        HashMap<Integer, Emoji> emojis = cached.getRight();
        if (emojis.isEmpty()) return; // no emojis — let vanilla handle normally

        String processedText = cached.getLeft();

        FontRenderer self = (FontRenderer) (Object) this;
        float curX = x;

        // Walk processedText: flush plain-text segments normally, draw emoji quads at placeholders
        StringBuilder segment = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < processedText.length(); i++) {
            char ch = processedText.charAt(i);
            if (emojis.containsKey(pos)) {
                // Flush accumulated plain text
                if (segment.length() > 0) {
                    // GUARD_CHAR prefix prevents our injector from re-triggering
                    int advance = self.drawString(EmojiFontHelper.GUARD_PREFIX + segment.toString(),
                            curX, y, color, dropShadow);
                    curX = advance; // drawString returns the end X position
                    segment.setLength(0);
                }
                // Draw the emoji quad. We always draw it — even on the shadow pass
                // call — because FontRenderer handles shadow by calling drawString twice
                // (once with dropShadow=true for the shadow offset, once =false for the
                // real pixels). But our mixin cancels that outer call and drives rendering
                // itself, so we must draw the emoji for BOTH passes: once offset for shadow,
                // once at the real position.
                if (dropShadow) {
                    // Shadow pass: draw slightly offset and dimmed
                    EmojiRenderer.renderEmoji(emojis.get(pos), curX + 1, y, 0.25f);
                }
                // Always draw the normal (non-shadow) emoji
                EmojiRenderer.renderEmoji(emojis.get(pos), curX, y - 1, 1.0f);
                curX += 11f; // emoji width (10px) + 1px gap
                pos++;
            } else if (ch == '\u2603') {
                // Orphaned snowman placeholder (shouldn't happen) — skip it
                pos++;
            } else {
                segment.append(ch);
                pos++;
            }
        }
        // Flush any remaining plain text
        if (segment.length() > 0) {
            int advance = self.drawString(EmojiFontHelper.GUARD_PREFIX + segment.toString(),
                    curX, y, color, dropShadow);
            curX = advance;
        }

        cir.setReturnValue((int) curX);
    }

    @Inject(
        method = "getStringWidth(Ljava/lang/String;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void emojiful_getStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (!EmojifulConfig.renderEmoji || Constants.error) return;
        if (text == null || text.isEmpty()) return;
        if (text.charAt(0) == EmojiFontHelper.GUARD_CHAR) return; // pass-through for our own calls

        Pair<String, HashMap<Integer, Emoji>> cached;
        try {
            cached = EmojiFontHelper.RECENT_STRINGS.get(text);
        } catch (ExecutionException e) {
            return;
        }

        HashMap<Integer, Emoji> emojis = cached.getRight();
        if (emojis.isEmpty()) return;

        FontRenderer self = (FontRenderer) (Object) this;
        String processedText = cached.getLeft();

        // Calculate width: sum up plain segments + 11px per emoji
        int totalWidth = 0;
        StringBuilder segment = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < processedText.length(); i++) {
            char ch = processedText.charAt(i);
            if (emojis.containsKey(pos)) {
                if (segment.length() > 0) {
                    totalWidth += self.getStringWidth(EmojiFontHelper.GUARD_PREFIX + segment.toString());
                    segment.setLength(0);
                }
                totalWidth += 11; // emoji width
                pos++;
            } else if (ch == '\u2603') {
                pos++;
            } else {
                segment.append(ch);
                pos++;
            }
        }
        if (segment.length() > 0) {
            totalWidth += self.getStringWidth(EmojiFontHelper.GUARD_PREFIX + segment.toString());
        }

        cir.setReturnValue(totalWidth);
    }
}
