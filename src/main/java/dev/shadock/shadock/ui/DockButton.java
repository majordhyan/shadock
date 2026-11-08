package dev.shadock.shadock.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * A button drawn with Shadock's 32x stone textures. Shows an icon or a text label, has a short
 * "pushed in" state after a click (vanilla buttons have none), and dims its icon when disabled.
 */
public final class DockButton extends AbstractButton {
	private static final long PRESS_MS = 140;

	private final @Nullable Identifier icon;
	private final int iconSize;
	private final Runnable action;
	private long pressedAt = Long.MIN_VALUE;

	private DockButton(int width, int height, Component message, @Nullable Identifier icon, int iconSize, Runnable action) {
		super(0, 0, width, height, message);
		this.icon = icon;
		this.iconSize = iconSize;
		this.action = action;
	}

	/** Square icon button; the tooltip key is {@code messageKey + ".tooltip"}. */
	public static DockButton icon(String messageKey, Identifier icon, int size, int iconSize, Runnable action) {
		DockButton button = new DockButton(size, size, Component.translatable(messageKey), icon, iconSize, action);
		button.setTooltip(Tooltip.create(Component.translatable(messageKey + ".tooltip")));
		return button;
	}

	public static DockButton text(Component label, int width, int height, Runnable action) {
		return new DockButton(width, height, label, null, 0, action);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		action.run();
	}

	private boolean isPressedVisual() {
		return Util.getMillis() - pressedAt < PRESS_MS;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean pressed = active && isPressedVisual();
		Identifier sprite = !active ? DockSprites.BUTTON_DISABLED
			: pressed ? DockSprites.BUTTON_PRESSED
			: isHoveredOrFocused() ? DockSprites.BUTTON_HIGHLIGHTED
			: DockSprites.BUTTON;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight(), ARGB.white(alpha));

		int sink = pressed ? 1 : 0;
		if (icon != null) {
			int ix = getX() + (getWidth() - iconSize) / 2;
			int iy = getY() + (getHeight() - iconSize) / 2 + sink;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, ix, iy, iconSize, iconSize, ARGB.white(active ? alpha : alpha * 0.4f));
		} else {
			var font = net.minecraft.client.Minecraft.getInstance().font;
			int color = active ? DockColors.WHITE : DockColors.GRAY;
			graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2 + sink, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
