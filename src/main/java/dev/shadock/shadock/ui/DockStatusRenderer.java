package dev.shadock.shadock.ui;

import dev.shadock.shadock.util.FileNames;
import dev.shadock.shadock.util.TextUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Draws the single status line. When idle it shows the active pack; otherwise the current message.
 * Text is resolved and truncated only when the status, pack or width changes, not every frame.
 */
final class DockStatusRenderer {
	private final Font font;

	private @Nullable DockStatus cachedStatus;
	private @Nullable String cachedActive;
	private int cachedWidth = -1;
	private String shown = "";
	private String full = "";
	private int color = DockColors.GRAY;

	DockStatusRenderer(Font font) {
		this.font = font;
	}

	/** Returns the full text when it had to be shortened (for a tooltip), otherwise null. */
	@Nullable Component render(GuiGraphicsExtractor graphics, DockLayoutManager.Rect rect, @Nullable String activeFile, DockStatus status) {
		boolean busy = status.kind() == DockStatus.Kind.BUSY;
		int textX = rect.x();
		if (busy) {
			// Animated spinner while something is in progress (it pauses while Iris compiles, as the game does).
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.SPINNER, rect.x(), rect.y() + 2, 8, 8);
			textX += 11;
		}
		int textWidth = rect.width() - (textX - rect.x());
		if (status != cachedStatus || !Objects.equals(activeFile, cachedActive) || textWidth != cachedWidth) {
			cachedStatus = status;
			cachedActive = activeFile;
			cachedWidth = textWidth;
			if (status.isIdle()) {
				full = activeFile == null
					? Component.translatable("shadock.status.shaders_off").getString()
					: Component.translatable("shadock.status.active", FileNames.displayName(activeFile)).getString();
				color = activeFile == null ? DockColors.GRAY : DockColors.WHITE;
			} else {
				full = status.text().getString();
				color = DockColors.forStatus(status.kind());
			}
			shown = TextUtils.ellipsize(font, full, textWidth);
		}
		graphics.text(font, shown, textX, rect.y() + 2, color, true);
		return shown.equals(full) ? null : Component.literal(full);
	}
}
