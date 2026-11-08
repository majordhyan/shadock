package dev.shadock.shadock.ui;

import dev.shadock.shadock.util.FileNames;
import dev.shadock.shadock.util.TextUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

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

	// returns the full text only when it had to be cut, for the hover tooltip
	@Nullable Component render(GuiGraphicsExtractor graphics, DockLayoutManager.Rect rect, @Nullable String activeFile, DockStatus status) {
		boolean busy = status.kind() == DockStatus.Kind.BUSY;
		int textX = rect.x();
		if (busy) {
			// freezes along with everything else while Iris compiles, that's expected
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
