package dev.shadock.shadock.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

// Scroll state for the pack list. Wheel and keyboard set a target and the offset eases toward it;
// dragging the thumb sets both, because easing behind the cursor feels like lag.
final class ListScroll {
	static final int BAR_WIDTH = DockLayoutManager.SCROLLBAR_WIDTH;

	private float offset;
	private float target;
	private boolean thumbHeld;
	private int contentHeight;
	private DockLayoutManager.Rect view = new DockLayoutManager.Rect(0, 0, 0, 0);

	void layout(DockLayoutManager.Rect view, int contentHeight) {
		this.view = view;
		this.contentHeight = contentHeight;
		clamp();
	}

	float offset() {
		return offset;
	}

	boolean canScroll() {
		return max() > 0;
	}

	boolean isThumbHeld() {
		return thumbHeld;
	}

	void tick(float dtMs) {
		clamp();
		offset = thumbHeld ? target : DockAnimations.approach(offset, target, dtMs, DockAnimations.SCROLL_TAU_MS);
	}

	void nudge(float pixels) {
		target += pixels;
		clamp();
	}

	void reveal(float top, float height) {
		if (top < target) {
			target = top;
		} else if (top + height > target + view.height()) {
			target = top + height - view.height();
		}
		clamp();
	}

	boolean isOverBar(double mouseX) {
		return canScroll() && mouseX >= view.right() - BAR_WIDTH;
	}

	void grabThumb(double mouseY) {
		thumbHeld = true;
		moveThumb(mouseY);
	}

	void moveThumb(double mouseY) {
		int thumb = thumbHeight();
		float travel = Math.max(1, view.height() - thumb);
		target = (float) ((mouseY - view.y() - thumb / 2.0) / travel * max());
		clamp();
	}

	void releaseThumb() {
		thumbHeld = false;
	}

	void draw(GuiGraphicsExtractor graphics) {
		if (!canScroll()) {
			return;
		}
		int x = view.right() - BAR_WIDTH;
		int thumb = thumbHeight();
		int thumbY = view.y() + Math.round((view.height() - thumb) * (offset / max()));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.SCROLLER_BACKGROUND, x, view.y(), BAR_WIDTH, view.height());
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.SCROLLER, x, thumbY, BAR_WIDTH, thumb);
	}

	private float max() {
		return Math.max(0, contentHeight - view.height());
	}

	private int thumbHeight() {
		int content = Math.max(1, contentHeight);
		return Math.max(8, Math.min(view.height(), view.height() * view.height() / content));
	}

	private void clamp() {
		target = Math.max(0, Math.min(max(), target));
		offset = Math.max(0, Math.min(max(), offset));
	}
}
