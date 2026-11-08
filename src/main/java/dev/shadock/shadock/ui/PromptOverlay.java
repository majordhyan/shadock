package dev.shadock.shadock.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

// The Replace / Keep Both / Cancel question, drawn over the list area. The buttons are real widgets
// owned by the screen, so the screen hands in its add/remove methods (both are protected on Screen).
final class PromptOverlay {
	static final int MIN_ROWS = 5;
	private static final int BUTTON_HEIGHT = 16;

	private final Function<DockButton, DockButton> addWidget;
	private final Consumer<DockButton> removeWidget;
	private final List<DockButton> buttons = new ArrayList<>();
	private @Nullable DockPrompt shown;

	PromptOverlay(Function<DockButton, DockButton> addWidget, Consumer<DockButton> removeWidget) {
		this.addWidget = addWidget;
		this.removeWidget = removeWidget;
	}

	// init() wipes the screen's widgets, so forget ours too and let the next sync rebuild them
	void reset() {
		buttons.clear();
		shown = null;
	}

	void sync(@Nullable DockPrompt prompt) {
		if (prompt == shown) {
			return;
		}
		buttons.forEach(removeWidget);
		buttons.clear();
		shown = prompt;
		if (prompt == null) {
			return;
		}
		for (DockPrompt.Choice choice : prompt.choices()) {
			buttons.add(addWidget.apply(DockButton.text(choice.label(), 40, BUTTON_HEIGHT, choice.action())));
		}
	}

	void render(GuiGraphicsExtractor graphics, Font font, DockLayoutManager.Rect area, DockPrompt prompt) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, DockSprites.LIST_BACKGROUND, area.x(), area.y(), 0f, 0f, area.width(), area.height(), 32, 32);
		graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0x80000000);

		int buttonsY = area.bottom() - BUTTON_HEIGHT - 3;
		List<FormattedCharSequence> lines = font.split(prompt.message(), area.width() - 8);
		int maxLines = Math.max(1, (buttonsY - area.y() - 4) / 10);
		int y = area.y() + 4;
		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			graphics.text(font, lines.get(i), area.x() + 4, y, DockColors.WHITE, true);
			y += 10;
		}

		int gap = 2;
		int w = (area.width() - 6 - gap * (buttons.size() - 1)) / Math.max(1, buttons.size());
		for (int i = 0; i < buttons.size(); i++) {
			DockButton b = buttons.get(i);
			b.setWidth(w);
			b.setX(area.x() + 3 + i * (w + gap));
			b.setY(buttonsY);
		}
	}
}
