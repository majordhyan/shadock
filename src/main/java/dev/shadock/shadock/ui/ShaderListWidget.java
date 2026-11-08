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

// Not a vanilla AbstractSelectionList on purpose: that one can't animate rows aside during a drag,
// and the drag is the whole point. Draws like one though.
final class ShaderListWidget {
	// a press has to travel this far before it counts as a drag, so shaky clicks still apply the pack
	static final double DRAG_THRESHOLD = 5.0;
	private static final int ROW = DockLayoutManager.ROW_HEIGHT;
	private static final float AUTO_SCROLL_SPEED = 0.2f;
	private static final Component INVALID_TOOLTIP = Component.translatable("shadock.status.invalid_pack");
	private static final Component LOCKED_TOOLTIP = Component.translatable("shadock.status.drag_locked");

	private final DockController controller;
	private final Font font;
	private final ListScroll scroll = new ListScroll();

	private List<ShaderPack> source = List.of();
	private final List<ShaderListEntry> entries = new ArrayList<>();

	private int pressedIndex = -1;
	private double pressX;
	private double pressY;
	private boolean dragging;
	private int dragIndex = -1;
	private int dropIndex = -1;
	private double grabOffset;
	private double lastMouseY;
	private boolean lockHintShown;

	private int cursor = -1; // arrow-key selection, -1 while the mouse is driving

	private DockLayoutManager.Rect area = new DockLayoutManager.Rect(0, 0, 0, 0);

	ShaderListWidget(DockController controller, Font font) {
		this.controller = controller;
		this.font = font;
	}

	boolean isDragging() {
		return dragging || scroll.isThumbHeld();
	}

	boolean isPressing() {
		return pressedIndex >= 0 || scroll.isThumbHeld();
	}

	void sync(List<ShaderPack> rows) {
		if (rows == source) {
			return;
		}
		// carry each pack's current y over so a reorder or rescan animates instead of jumping
		String cursorName = cursor >= 0 && cursor < entries.size() ? entries.get(cursor).pack.fileName() : null;
		Map<String, Float> previousY = new HashMap<>();
		for (ShaderListEntry entry : entries) {
			previousY.put(entry.pack.fileName(), entry.animY);
		}
		source = rows;
		entries.clear();
		cursor = -1;
		for (int i = 0; i < rows.size(); i++) {
			ShaderPack pack = rows.get(i);
			Float y = previousY.get(pack.fileName());
			entries.add(new ShaderListEntry(pack, y != null ? y : i * ROW));
			if (pack.fileName().equals(cursorName)) {
				cursor = i;
			}
		}
		if (dragging) {
			cancelPress();
		}
		pressedIndex = -1;
		scroll.layout(area, entries.size() * ROW);
	}

	void render(GuiGraphicsExtractor graphics, DockLayoutManager.Rect area, int mouseX, int mouseY,
				float dtMs, @Nullable String activeFile, boolean inputBlocked) {
		this.area = area;
		scroll.layout(area, entries.size() * ROW);
		scroll.tick(dtMs);
		if (dragging) {
			autoScroll(dtMs);
			updateDropIndex(lastMouseY);
		}
		for (int i = 0; i < entries.size(); i++) {
			ShaderListEntry entry = entries.get(i);
			entry.animY = DockAnimations.approach(entry.animY, targetSlot(i) * ROW, dtMs, DockAnimations.ROW_TAU_MS);
		}

		float offset = scroll.offset();
		graphics.blit(RenderPipelines.GUI_TEXTURED, DockSprites.LIST_BACKGROUND, area.x(), area.y(),
			0f, offset, area.width(), area.height(), 32, 32);

		int rowWidth = rowWidth();
		graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());

		if (dragging) {
			int gapY = Math.round(area.y() - offset + dropIndex * ROW);
			graphics.fill(area.x(), gapY, area.x() + rowWidth, gapY + ROW, DockColors.DROP_SLOT);
			graphics.fill(area.x(), gapY, area.x() + rowWidth, gapY + 1, DockColors.WHITE);
		}

		boolean mouseInList = !dragging && !inputBlocked && area.contains(mouseX, mouseY) && mouseX < area.x() + rowWidth;
		int hovered = mouseInList ? rowAt(mouseY) : -1;
		// only draw what's on screen, people do keep 100+ packs in there
		int first = Math.max(0, (int) Math.floor(offset / ROW) - 1);
		int last = Math.min(entries.size() - 1, (int) Math.ceil((offset + area.height()) / ROW) + 1);
		for (int i = first; i <= last; i++) {
			if (dragging && i == dragIndex) {
				continue;
			}
			ShaderListEntry entry = entries.get(i);
			int y = Math.round(area.y() - offset + entry.animY);
			drawRow(graphics, entry, area.x(), y, rowWidth, entry.pack.fileName().equals(activeFile), i == hovered, i == cursor);
		}
		graphics.disableScissor();

		// the dragged row goes last and outside the scissor so it can hang half off the list edge
		if (dragging && dragIndex >= 0 && dragIndex < entries.size()) {
			int y = (int) Math.round(Math.max(area.y() - ROW / 2.0, Math.min(area.bottom() - ROW / 2.0, lastMouseY - grabOffset)));
			ShaderListEntry entry = entries.get(dragIndex);
			graphics.fill(area.x() + 2, y + 2, area.x() + rowWidth + 2, y + ROW + 2, DockColors.SHADOW);
			drawSelection(graphics, area.x(), y, rowWidth, DockColors.SELECTION_OUTLINE);
			drawRowContent(graphics, entry, area.x(), y, rowWidth, entry.pack.fileName().equals(activeFile), true);
		}

		scroll.draw(graphics);

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

		int nameWidth = width - 13 - Math.max(markerWidth, 12) - 1;
		int color = invalid ? DockColors.DARK_GRAY : active || emphasized ? DockColors.WHITE : DockColors.GRAY;
		graphics.text(font, entry.name(font, nameWidth), x + 13, y + 2, color, true);
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

	private int rowWidth() {
		return scroll.canScroll() ? area.width() - ListScroll.BAR_WIDTH - 1 : area.width();
	}

	// where row i sits while a drag is going on: everything between the old and new slot shifts by one
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
		int index = (int) Math.floor((mouseY - area.y() + scroll.offset()) / ROW);
		return index >= 0 && index < entries.size() ? index : -1;
	}

	private void autoScroll(float dtMs) {
		if (lastMouseY < area.y() + ROW) {
			scroll.nudge(-AUTO_SCROLL_SPEED * dtMs);
		} else if (lastMouseY > area.bottom() - ROW) {
			scroll.nudge(AUTO_SCROLL_SPEED * dtMs);
		}
	}

	private void updateDropIndex(double mouseY) {
		double rowTop = mouseY - grabOffset - area.y() + scroll.offset();
		dropIndex = (int) Math.max(0, Math.min(entries.size() - 1, Math.round(rowTop / ROW)));
	}

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT || !area.contains(mouseX, mouseY)) {
			return false;
		}
		if (scroll.isOverBar(mouseX)) {
			scroll.grabThumb(mouseY);
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
		grabOffset = mouseY - (area.y() - scroll.offset() + index * ROW);
		lockHintShown = false;
		return true;
	}

	boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (scroll.isThumbHeld()) {
			scroll.moveThumb(mouseY);
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

	boolean mouseReleased(double mouseX, double mouseY, int button, Consumer<ShaderPack> onClick) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (scroll.isThumbHeld()) {
			scroll.releaseThumb();
			return true;
		}
		if (pressedIndex < 0) {
			return false;
		}
		int pressed = pressedIndex;
		pressedIndex = -1;

		if (dragging) {
			updateDropIndex(mouseY);
			// start the dropped row's animation from where it was let go, not its old slot
			entries.get(dragIndex).animY = (float) (mouseY - grabOffset - area.y() + scroll.offset());
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

	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!area.contains(mouseX, mouseY)) {
			return false;
		}
		scroll.nudge((float) (-amount * ROW * 1.5));
		return true;
	}

	void cancelPress() {
		dragging = false;
		dragIndex = -1;
		dropIndex = -1;
		pressedIndex = -1;
		scroll.releaseThumb();
	}

	void moveCursor(int delta, @Nullable String activeFile) {
		if (entries.isEmpty()) {
			return;
		}
		// first arrow press starts from the active pack, which is where people are looking
		if (cursor < 0) {
			cursor = indexOf(activeFile);
			if (cursor < 0) {
				cursor = delta > 0 ? -1 : entries.size();
			}
		}
		cursor = Math.max(0, Math.min(entries.size() - 1, cursor + delta));
		scroll.reveal(cursor * ROW, ROW);
	}

	@Nullable ShaderPack cursorPack() {
		return packAt(cursor);
	}

	@Nullable ShaderPack packAt(int index) {
		return index >= 0 && index < entries.size() ? entries.get(index).pack : null;
	}

	void moveCursorPack(int delta) {
		if (!controller.canDrag()) {
			controller.setStatus(DockStatus.of(DockStatus.Kind.INFO, "shadock.status.drag_locked"));
			return;
		}
		if (cursor < 0 || controller.isBusy()) {
			return;
		}
		int target = Math.max(0, Math.min(entries.size() - 1, cursor + delta));
		if (target == cursor) {
			return;
		}
		controller.reorder(cursor, target);
		cursor = target;
		scroll.reveal(cursor * ROW, ROW);
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
