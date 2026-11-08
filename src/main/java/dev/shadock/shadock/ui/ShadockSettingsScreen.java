package dev.shadock.shadock.ui;

import dev.shadock.shadock.ShadockClient;
import dev.shadock.shadock.config.DuplicateImportBehavior;
import dev.shadock.shadock.input.ShadockKeybinds;
import dev.shadock.shadock.config.ShadockConfig;
import dev.shadock.shadock.shader.ShaderPackSortMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The tiny built-in settings screen, opened from the dock's gear button. */
public final class ShadockSettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 210;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SPACING = 24;

	private final Screen parent;

	public ShadockSettingsScreen(Screen parent) {
		super(Component.translatable("shadock.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ShadockConfig config = ShadockClient.config().get();
		DockController dock = ShadockClient.dock();
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = Math.max(40, height / 2 - SPACING * 3);

		addRenderableWidget(Button.builder(Component.translatable("shadock.settings.keybind", ShadockKeybinds.openDock().getTranslatedKeyMessage()),
				b -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
			.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
			.tooltip(Tooltip.create(Component.translatable("shadock.settings.keybind.tooltip")))
			.build());
		y += SPACING;

		addRenderableWidget(CycleButton.<ShaderPackSortMode>builder(
				mode -> Component.translatable("shadock.settings.sort." + mode.name().toLowerCase(Locale.ROOT)), config.sortMode)
			.withValues(ShaderPackSortMode.values())
			.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("shadock.settings.sort"),
				(button, value) -> dock.setSortMode(value)));
		y += SPACING;

		List<Integer> autoHide = new ArrayList<>();
		for (int seconds : ShadockConfig.AUTO_HIDE_CHOICES) {
			autoHide.add(seconds);
		}
		addRenderableWidget(CycleButton.<Integer>builder(ShadockSettingsScreen::autoHideLabel, config.autoHideDelaySeconds)
			.withValues(autoHide)
			.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("shadock.settings.auto_hide"),
				(button, value) -> {
					config.autoHideDelaySeconds = value;
					ShadockClient.config().save();
				}));
		y += SPACING;

		addRenderableWidget(CycleButton.onOffBuilder(config.dockAnimation)
			.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("shadock.settings.animation"),
				(button, value) -> {
					config.dockAnimation = value;
					ShadockClient.config().save();
				}));
		y += SPACING;

		addRenderableWidget(CycleButton.<DuplicateImportBehavior>builder(
				b -> Component.translatable("shadock.settings.duplicate." + b.name().toLowerCase(Locale.ROOT)), config.duplicateImportBehavior)
			.withValues(DuplicateImportBehavior.values())
			.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("shadock.settings.duplicate"),
				(button, value) -> {
					config.duplicateImportBehavior = value;
					ShadockClient.config().save();
				}));
		y += SPACING + 8;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
	}

	private static Component autoHideLabel(int seconds) {
		return seconds <= 0
			? Component.translatable("shadock.settings.auto_hide.never")
			: Component.translatable("shadock.settings.auto_hide.seconds", seconds);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Branded header: prism mark + 2x wordmark (both 32x textures, crisp at any GUI scale).
		int headerY = Math.max(10, height / 2 - SPACING * 3 - 30);
		int wordW = DockSprites.WORDMARK_WIDTH * 2;
		int total = 20 + 5 + wordW;
		int hx = width / 2 - total / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.LOGO, hx, headerY, 20, 20);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.WORDMARK, hx + 25, headerY,
			wordW, DockSprites.WORDMARK_HEIGHT * 2);
		int hintY = Math.max(40, height / 2 - SPACING * 3) + SPACING * 6 + 8 + 6;
		if (hintY + 9 < height) {
			graphics.centeredText(font, Component.translatable("shadock.settings.controls"), width / 2, hintY, DockColors.GRAY);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
