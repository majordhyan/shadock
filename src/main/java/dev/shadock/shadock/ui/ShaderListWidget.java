package dev.shadock.shadock.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.shadock.shadock.shader.ShaderPack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Scrollable pack list drawn like a vanilla selection list, with click-to-activate,
 * drag-to-reorder, a vanilla scrollbar and keyboard navigation.
 * Only visible rows are drawn, and entries are rebuilt only when the controller's row list changes.
 */
final class ShaderListWidget {
	/** Pixels the mouse must move before a press becomes a drag (spec: 4–6). */
	static final double DRAG_THRESHOLD = 5.0;
	private static final int ROW = DockLayoutManager.ROW_HEIGHT;
	private static final int SCROLLBAR = DockLayoutManager.SCROLLBAR_WIDTH;
	private static final float AUTO_SCROLL_SPEED = 0.2f;
	private static final Component INVALID_TOOLTIP = Component.translatable("shadock.status.invalid_pack");
	private static final Component LOCKED_TOOLTIP = Component.translatable("shadock.status.drag_locked");

	private final DockController controller;
	private final Font font;

	private List<ShaderPack> source = List.of();
	private final List<ShaderListEntry> entries = new ArrayList<>();

	private float scroll;
	private float targetScroll;
	private boolean draggingScrollbar;

	private int pressedIndex = -1;
	private double pressX;
	private double pressY;
	private boolean dragging;
	private int dragIndex = -1;
	private int dropIndex = -1;
	private double grabOffset;
	private double lastMouseY;
	private boolean lockHintShown;

	/** Keyboard cursor (arrow keys), or -1 when the mouse is in charge. */
	private int cursor = -1;

	private DockLayoutManager.Rect area = new DockLayoutManager.Rect(0, 0, 0, 0);

	ShaderListWidget(DockController controller, Font font) {
		this.controller = controller;
		this.font = font;
	}

	boolean isDragging() {
		return dragging || draggingScrollbar;
	}

	boolean isPressing() {
		return pressedIndex >= 0 || draggingScrollbar;
	}

	/** Re-syncs with the controller's rows, keeping each pack's on-screen position for smooth motion. */
	void sync(List<ShaderPack> rows) {
		if (rows == source) {
			return;
		}
		String cursorName = cursor >= 0 && cursor < entries.size() ? entries.get(cursor).pack.fileName() : null;
		Map<String, Float> previous = new HashMap<>();
		for (ShaderListEntry entry : entries) {
			previous.put(entry.pack.fileName(), entry.animY);
		}
		source = rows;
		entries.clear();
		cursor = -1;
		for (int i = 0; i < rows.size(); i++) {
			ShaderPack pack = rows.get(i);
			Float y = previous.get(pack.fileName());
			entries.add(new ShaderListEntry(pack, y != null ? y : i * ROW));
			if (pack.fileName().equals(cursorName)) {
				cursor = i;
			}
		}
		if (dragging) {
			cancelPress();
		}
		pressedIndex = -1;
		clampScroll();
	}

	// ---- rendering -------------------------------------------------------------------------------

	void render(GuiGraphicsExtractor graphics, DockLayoutManager.Rect area, int mouseX, int mouseY,
				float dtMs, @Nullable String activeFile, boolean inputBlocked) {
		this.area = area;
		updateScroll(dtMs);
		if (dragging) {
			autoScroll(dtMs);
			updateDropIndex(lastMouseY);
		}
		for (int i = 0; i < entries.size(); i++) {
			ShaderListEntry entry = entries.get(i);
			entry.animY = DockAnimations.approach(entry.animY, targetSlot(i) * ROW, dtMs, DockAnimations.ROW_TAU_MS);
		}

		// Vanilla in-world list background.
		graphics.blit(RenderPipelines.GUI_TEXTURED, DockSprites.LIST_BACKGROUND, area.x(), area.y(),
			0f, scroll, area.width(), area.height(), 32, 32);

		int rowWidth = rowWidth();
		graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());

		if (dragging) {
			int gapY = Math.round(area.y() - scroll + dropIndex * ROW);
			graphics.fill(area.x(), gapY, area.x() + rowWidth, gapY + ROW, DockColors.DROP_SLOT);
			graphics.fill(area.x(), gapY, area.x() + rowWidth, gapY + 1, DockColors.WHITE);
		}

		boolean mouseInList = !dragging && !inputBlocked && area.contains(mouseX, mouseY) && mouseX < area.x() + rowWidth;
		int hovered = mouseInList ? rowAt(mouseY) : -1;
		int first = Math.max(0, (int) Math.floor(scroll / ROW) - 1);
		int last = Math.min(entries.size() - 1, (int) Math.ceil((scroll + area.height()) / ROW) + 1);
		for (int i = first; i <= last; i++) {
			if (dragging && i == dragIndex) {
				continue;
			}
			ShaderListEntry entry = entries.get(i);
			int y = Math.round(area.y() - scroll + entry.animY);
			drawRow(graphics, entry, area.x(), y, rowWidth, entry.pack.fileName().equals(activeFile), i == hovered, i == cursor);
		}
		graphics.disableScissor();

		if (dragging && dragIndex >= 0 && dragIndex < entries.size()) {
			int y = (int) Math.round(Math.max(area.y() - ROW / 2.0, Math.min(area.bottom() - ROW / 2.0, lastMouseY - grabOffset)));
			ShaderListEntry entry = entries.get(dragIndex);
			graphics.fill(area.x() + 2, y + 2, area.x() + rowWidth + 2, y + ROW + 2, DockColors.SHADOW);
			drawSelection(graphics, area.x(), y, rowWidth, DockColors.SELECTION_OUTLINE);
			drawRowContent(graphics, entry, area.x(), y, rowWidth, entry.pack.fileName().equals(activeFile), true);
		}

		drawScrollbar(graphics);

		if (hovered >= 0) {
			tooltipFor(graphics, entries.get(hovered), mouseX, mouseY);
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, ShaderListEntry entry, int x, int y, int width,
						 boolean active, boolean hovered, boolean cursorHere) {
		if (active) {
			drawSelection(graphics, x, y, width, DockColors.SELECTION_OUTLINE);
		} else if (cursorHere) {
			drawSelection(graphics, x, y, width, DockColors.SELECTION_OUTLINE_UNFOCUSED);
		} else if (hovered) {
			graphics.fill(x, y, x + width, y + ROW, DockColors.ROW_HOVER);
		}
		drawRowContent(graphics, entry, x, y, width, active, hovered || cursorHere);
	}

	/** The vanilla selection box: a 1 px outline around a black fill. */
	private static void drawSelection(GuiGraphicsExtractor graphics, int x, int y, int width, int outline) {
		graphics.fill(x, y, x + width, y + ROW, outline);
		graphics.fill(x + 1, y + 1, x + width - 1, y + ROW - 1, DockColors.SELECTION_FILL);
	}

	private void drawRowContent(GuiGraphicsExtractor graphics, ShaderListEntry entry, int x, int y, int width,
								boolean active, boolean emphasized) {
		ShaderPack pack = entry.pack;
		boolean invalid = pack.validity() == ShaderPack.Validity.INVALID;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
			pack.type() == ShaderPack.Type.ZIP ? DockSprites.ROW_ZIP : DockSprites.ROW_FOLDER, x + 2, y + 2, 8, 8);

		// Right-hand marker: invalid warning, active checkmark, or a drag handle / lock on hover.
		int markerWidth = 0;
		if (invalid) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.ROW_INVALID, x + width - 10, y + 2, 8, 8);
			markerWidth = 11;
		} else if (active) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.CHECKMARK, x + width - 11, y + 2, 9, 8);
			markerWidth = 12;
		} else if (emphasized) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, controller.canDrag() ? DockSprites.ROW_GRIP : DockSprites.ROW_LOCK,
				x + width - 10, y + 2, 8, 8);
			markerWidth = 11;
		}

		int nameX = x + 13;
		int nameWidth = width - 13 - Math.max(markerWidth, 12) - 1;
		int color = invalid ? DockColors.DARK_GRAY : active ? DockColors.WHITE : emphasized ? DockColors.WHITE : DockColors.GRAY;
		graphics.text(font, entry.name(font, nameWidth), nameX, y + 2, color, true);
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics) {
		float max = maxScroll();
		if (max <= 0) {
			return;
		}
		int x = area.right() - SCROLLBAR;
		int thumbHeight = thumbHeight();
		int thumbY = area.y() + Math.round((area.height() - thumbHeight) * (scroll / max));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.SCROLLER_BACKGROUND, x, area.y(), SCROLLBAR, area.height());
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.SCROLLER, x, thumbY, SCROLLBAR, thumbHeight);
	}

	private void tooltipFor(GuiGraphicsExtractor graphics, ShaderListEntry entry, int mouseX, int mouseY) {
		if (entry.pack.validity() == ShaderPack.Validity.INVALID) {
			graphics.setTooltipForNextFrame(font, INVALID_TOOLTIP, mouseX, mouseY);
		} else if (entry.isTruncated()) {
			graphics.setTooltipForNextFrame(font, Component.literal(entry.pack.displayName()), mouseX, mouseY);
		} else if (!controller.canDrag() && mouseX >= area.x() + rowWidth() - 12) {
			graphics.setTooltipForNextFrame(font, LOCKED_TOOLTIP, mouseX, mouseY);
		}
	}

	// ---- geometry --------------------------------------------------------------------------------

	private int rowWidth() {
		return maxScroll() > 0 ? area.width() - SCROLLBAR - 1 : area.width();
	}

	private int thumbHeight() {
		int content = Math.max(1, entries.size() * ROW);
		return Math.max(8, Math.min(area.height(), area.height() * area.height() / content));
	}

	/** Where row {@code i} should be while a drag is in progress (others make room at the drop slot). */
	private int targetSlot(int i) {
		if (!dragging || i == dragIndex) {
			return i;
		}
		if (dragIndex < i && i <= dropIndex) {
			return i - 1;
		}
		if (dropIndex <= i && i < dragIndex) {
			return i + 1;
		}
		return i;
	}

	private int rowAt(double mouseY) {
		int index = (int) Math.floor((mouseY - area.y() + scroll) / ROW);
		return index >= 0 && index < entries.size() ? index : -1;
	}

	private float maxScroll() {
		return Math.max(0, entries.size() * ROW - area.height());
	}

	private void clampScroll() {
		targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
	}

	private void updateScroll(float dtMs) {
		clampScroll();
		scroll = draggingScrollbar ? targetScroll : DockAnimations.approach(scroll, targetScroll, dtMs, DockAnimations.SCROLL_TAU_MS);
	}

	private void autoScroll(float dtMs) {
		if (lastMouseY < area.y() + ROW) {
			targetScroll -= AUTO_SCROLL_SPEED * dtMs;
		} else if (lastMouseY > area.bottom() - ROW) {
			targetScroll += AUTO_SCROLL_SPEED * dtMs;
		}
		clampScroll();
	}

	private void updateDropIndex(double mouseY) {
		double rowTop = mouseY - grabOffset - area.y() + scroll;
		dropIndex = (int) Math.max(0, Math.min(entries.size() - 1, Math.round(rowTop / ROW)));
	}

	private void ensureVisible(int index) {
		float top = index * ROW;
		if (top < targetScroll) {
			targetScroll = top;
		} else if (top + ROW > targetScroll + area.height()) {
			targetScroll = top + ROW - area.height();
		}
		clampScroll();
	}

	private void scrollbarTo(double mouseY) {
		float max = maxScroll();
		float travel = Math.max(1, area.height() - thumbHeight());
		targetScroll = (float) ((mouseY - area.y() - thumbHeight() / 2.0) / travel * max);
		clampScroll();
	}

	// ---- mouse -----------------------------------------------------------------------------------

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT || !area.contains(mouseX, mouseY)) {
			return false;
		}
		if (maxScroll() > 0 && mouseX >= area.right() - SCROLLBAR) {
			draggingScrollbar = true;
			scrollbarTo(mouseY);
			return true;
		}
		cursor = -1;
		int index = rowAt(mouseY);
		if (index < 0) {
			return true;
		}
		pressedIndex = index;
		pressX = mouseX;
		pressY = mouseY;
		lastMouseY = mouseY;
		grabOffset = mouseY - (area.y() - scroll + index * ROW);
		lockHintShown = false;
		return true;
	}

	boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (draggingScrollbar) {
			scrollbarTo(mouseY);
			return true;
		}
		if (pressedIndex < 0) {
			return false;
		}
		lastMouseY = mouseY;
		if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) >= DRAG_THRESHOLD) {
			if (!controller.canDrag()) {
				if (!lockHintShown) {
					controller.setStatus(DockStatus.of(DockStatus.Kind.INFO, "shadock.status.drag_locked"));
					lockHintShown = true;
				}
				return true;
			}
			if (controller.isBusy()) {
				return true;
			}
			dragging = true;
			dragIndex = pressedIndex;
			dropIndex = pressedIndex;
		}
		if (dragging) {
			updateDropIndex(mouseY);
		}
		return true;
	}

	/** Ends a press: commits a drag, or activates the row if it was a plain click. */
	boolean mouseReleased(double mouseX, double mouseY, int button, Consumer<ShaderPack> onClick) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		if (pressedIndex < 0) {
			return false;
		}
		int pressed = pressedIndex;
		pressedIndex = -1;

		if (dragging) {
			updateDropIndex(mouseY);
			ShaderListEntry moved = entries.get(dragIndex);
			moved.animY = (float) (mouseY - grabOffset - area.y() + scroll);
			int from = dragIndex;
			int to = dropIndex;
			dragging = false;
			dragIndex = -1;
			dropIndex = -1;
			controller.reorder(from, to);
			return true;
		}

		if (Math.hypot(mouseX - pressX, mouseY - pressY) < DRAG_THRESHOLD && rowAt(mouseY) == pressed && pressed < entries.size()) {
			onClick.accept(entries.get(pressed).pack);
		}
		return true;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!area.contains(mouseX, mouseY)) {
			return false;
		}
		targetScroll -= (float) (scrollY * ROW * 1.5);
		clampScroll();
		return true;
	}

	/** Drops any press or drag without changing the order (Escape, focus lost, or the list changed). */
	void cancelPress() {
		dragging = false;
		dragIndex = -1;
		dropIndex = -1;
		pressedIndex = -1;
		draggingScrollbar = false;
	}

	// ---- keyboard --------------------------------------------------------------------------------

	/** Moves the keyboard cursor. Starts on the active pack (or the top) the first time. */
	void moveCursor(int delta, @Nullable String activeFile) {
		if (entries.isEmpty()) {
			return;
		}
		if (cursor < 0) {
			cursor = indexOf(activeFile);
			if (cursor < 0) {
				cursor = delta > 0 ? -1 : entries.size();
			}
		}
		cursor = Math.max(0, Math.min(entries.size() - 1, cursor + delta));
		ensureVisible(cursor);
	}

	@Nullable ShaderPack cursorPack() {
		return cursor >= 0 && cursor < entries.size() ? entries.get(cursor).pack : null;
	}

	@Nullable ShaderPack packAt(int index) {
		return index >= 0 && index < entries.size() ? entries.get(index).pack : null;
	}

	/** Moves the pack under the keyboard cursor up or down (Shift + arrow). */
	void moveCursorPack(int delta) {
		if (cursor < 0 || !controller.canDrag() || controller.isBusy()) {
			if (!controller.canDrag()) {
				controller.setStatus(DockStatus.of(DockStatus.Kind.INFO, "shadock.status.drag_locked"));
			}
			return;
		}
		int target = Math.max(0, Math.min(entries.size() - 1, cursor + delta));
		if (target == cursor) {
			return;
		}
		controller.reorder(cursor, target);
		cursor = target;
		ensureVisible(cursor);
	}

	private int indexOf(@Nullable String fileName) {
		if (fileName == null) {
			return -1;
		}
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).pack.fileName().equals(fileName)) {
				return i;
			}
		}
		return -1;
	}
}
