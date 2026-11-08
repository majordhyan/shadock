package dev.shadock.shadock.ui;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import dev.shadock.shadock.ShadockClient;
import dev.shadock.shadock.input.DockToggleHandler;
import dev.shadock.shadock.shader.ShaderPack;
import dev.shadock.shadock.shader.ShaderPackScanner;
import dev.shadock.shadock.shader.ShaderPackSortMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ShadockDockScreen extends Screen {
	private final DockController controller;
	private ShaderListWidget list;
	private DockStatusRenderer statusRenderer;
	private DockLayoutManager.Layout layout;

	private DockButton closeButton;
	private DockButton importButton;
	private DockButton folderButton;
	private DockButton sortCustomButton;
	private DockButton sortAlphaButton;
	private DockButton keyButton;
	private DockButton settingsButton;

	private final PromptOverlay promptOverlay = new PromptOverlay(this::addRenderableWidget, this::removeWidget);

	// != -1 while the slide-out plays; the screen only really closes when it finishes
	private long closingAt = -1;

	private long openedAt = -1;
	private long lastFrameAt;
	private long idleSince;
	private boolean openAnimationDone;
	private @Nullable DockStatus lastSeenStatus;
	private int countForLabel = -1;
	private String countLabel = "";

	public ShadockDockScreen() {
		super(Component.translatable("shadock.dock.title"));
		this.controller = ShadockClient.dock();
	}

	@Override
	protected void init() {
		// init() runs on first open and again after a resize or returning from Key Binds / Settings.
		statusRenderer = new DockStatusRenderer(font);
		if (list == null) {
			list = new ShaderListWidget(controller, font);
		}
		long now = Util.getMillis();
		if (openedAt < 0) {
			openedAt = now;
			lastFrameAt = now;
			openAnimationDone = !controller.config().dockAnimation;
			controller.onDockOpened();
		}
		idleSince = now;
		countForLabel = -1;

		closeButton = addRenderableWidget(DockButton.icon("shadock.button.close", DockSprites.CLOSE,
			DockLayoutManager.CLOSE_SIZE, 10, this::onClose));

		importButton = addRenderableWidget(iconButton("shadock.button.import", DockSprites.IMPORT, controller::startImport));
		folderButton = addRenderableWidget(iconButton("shadock.button.folder", DockSprites.FOLDER, this::openFolder));
		sortCustomButton = addRenderableWidget(iconButton("shadock.button.sort.custom", DockSprites.SORT_CUSTOM, controller::toggleSortMode));
		sortAlphaButton = addRenderableWidget(iconButton("shadock.button.sort.alpha", DockSprites.SORT_ALPHA, controller::toggleSortMode));
		keyButton = addRenderableWidget(iconButton("shadock.button.key",
			DockSprites.KEY, () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))));
		settingsButton = addRenderableWidget(iconButton("shadock.button.settings",
			DockSprites.SETTINGS, () -> minecraft.gui.setScreen(new ShadockSettingsScreen(this))));

		promptOverlay.reset();
	}

	private DockButton iconButton(String key, Identifier sprite, Runnable action) {
		return DockButton.icon(key, sprite, DockLayoutManager.BUTTON_SIZE, 12, action);
	}

	private void openFolder() {
		Path packsDir = controller.shaderpacksDirectory();
		// opening Explorer/Finder can stall for a second on some systems, keep it off the render thread
		CompletableFuture.runAsync(() -> Blaze3D.openPath(packsDir));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// No dimming or blur: the dock is a small panel over the live world.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float dtMs = Math.min(100f, now - lastFrameAt);
		lastFrameAt = now;

		if (!minecraft.isWindowActive()) {
			// A release that happens while the window is in the background never reaches us.
			list.cancelPress();
		}

		List<ShaderPack> rows = controller.rows();
		list.sync(rows);
		DockPrompt prompt = controller.prompt();
		layout = DockLayoutManager.compute(width, height, rows.size(), prompt != null ? PromptOverlay.MIN_ROWS : 0);
		promptOverlay.sync(prompt);
		positionWidgets(prompt != null);

		float slide = 0f;
		float offscreen = -(layout.panel().right() + 4);
		if (closingAt >= 0) {
			float progress = DockAnimations.easeInCubic((now - closingAt) / DockAnimations.CLOSE_MS);
			if (progress >= 1f) {
				finishClose();
				return;
			}
			slide = progress * offscreen;
		} else if (!openAnimationDone) {
			float progress = DockAnimations.easeOutCubic((now - openedAt) / DockAnimations.OPEN_MS);
			openAnimationDone = progress >= 1f;
			slide = (1f - progress) * offscreen;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(slide, 0f);

		DockLayoutManager.Rect p = layout.panel();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.PANEL, p.x(), p.y(), p.width(), p.height());
		drawHeader(graphics, rows.size());

		DockLayoutManager.Rect l = layout.list();
		graphics.blit(RenderPipelines.GUI_TEXTURED, DockSprites.HEADER_SEPARATOR, l.x(), l.y() - 2, 0f, 0f, l.width(), 2, 32, 2);
		String active = controller.activePackName().orElse(null);
		if (prompt != null) {
			promptOverlay.render(graphics, font, l, prompt);
		} else if (rows.isEmpty()) {
			drawEmptyState(graphics);
		} else {
			list.render(graphics, l, mouseX, mouseY, dtMs, active, false);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, DockSprites.FOOTER_SEPARATOR, l.x(), l.bottom(), 0f, 0f, l.width(), 2, 32, 2);

		Component fullStatus = statusRenderer.render(graphics, layout.status(), active, controller.status());
		if (fullStatus != null && layout.status().contains(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, fullStatus, mouseX, mouseY);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.pose().popMatrix();

		updateAutoHide(now, mouseX, mouseY);
		controller.onFrameRendered();
	}

	private void positionWidgets(boolean promptOpen) {
		DockLayoutManager.Rect c = layout.close();
		closeButton.setX(c.x());
		closeButton.setY(c.y());

		boolean custom = controller.config().sortMode == ShaderPackSortMode.CUSTOM_ORDER;
		sortCustomButton.visible = custom;
		sortAlphaButton.visible = !custom;

		AbstractWidget[] toolbar = {importButton, folderButton, custom ? sortCustomButton : sortAlphaButton, keyButton, settingsButton};
		for (int i = 0; i < toolbar.length; i++) {
			DockLayoutManager.Rect r = layout.toolbarButton(i);
			toolbar[i].setX(r.x());
			toolbar[i].setY(r.y());
		}
		boolean busy = controller.isBusy();
		importButton.active = !busy && !promptOpen;
		sortCustomButton.active = !busy && !promptOpen;
		sortAlphaButton.active = !busy && !promptOpen;
		keyButton.active = !promptOpen;
		settingsButton.active = !promptOpen;
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int count) {
		DockLayoutManager.Rect h = layout.header();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.LOGO, h.x(), h.y() + 1, 12, 12);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DockSprites.WORDMARK, h.x() + 16, h.y() + 3,
			DockSprites.WORDMARK_WIDTH, DockSprites.WORDMARK_HEIGHT);
		if (count != countForLabel) {
			countForLabel = count;
			countLabel = Component.translatable(count == 1 ? "shadock.dock.count.one" : "shadock.dock.count", count).getString();
		}
		int countX = h.x() + 16 + DockSprites.WORDMARK_WIDTH + 5;
		if (countX + font.width(countLabel) < layout.close().x() - 2) {
			graphics.text(font, countLabel, countX, h.y() + 3, DockColors.GRAY, false);
		}
	}

	private void drawEmptyState(GuiGraphicsExtractor graphics) {
		DockLayoutManager.Rect l = layout.list();
		graphics.blit(RenderPipelines.GUI_TEXTURED, DockSprites.LIST_BACKGROUND, l.x(), l.y(), 0f, 0f, l.width(), l.height(), 32, 32);
		String key = switch (controller.scanState()) {
			case NOT_SCANNED, SCANNING -> "shadock.status.scanning";
			case FAILED -> "shadock.status.no_packs_found";
			case READY -> "shadock.status.no_packs_installed";
		};
		int cx = l.x() + l.width() / 2;
		graphics.centeredText(font, Component.translatable(key), cx, l.y() + l.height() / 2 - 9, DockColors.WHITE);
		if (controller.scanState() == ShaderPackScanner.State.READY) {
			graphics.centeredText(font, Component.translatable("shadock.dock.import_hint"), cx, l.y() + l.height() / 2 + 2, DockColors.GRAY);
		}
	}

	private void updateAutoHide(long now, int mouseX, int mouseY) {
		int delay = controller.config().autoHideDelaySeconds;
		// A new message restarts the timer, so an error or result is never hidden before it can be read.
		DockStatus status = controller.status();
		if (status != lastSeenStatus) {
			lastSeenStatus = status;
			idleSince = now;
		}
		// Paused while interacting, while busy, and while the game window is in the background.
		boolean interacting = list.isDragging() || list.isPressing() || controller.isBusy() || !minecraft.isWindowActive();
		if (delay <= 0 || interacting || layout.panel().contains(mouseX, mouseY)) {
			idleSince = now;
			return;
		}
		if (now - idleSince >= delay * 1000L) {
			onClose();
		}
	}

	@Override
	public void onClose() {
		if (closingAt >= 0) {
			return;
		}
		if (!controller.config().dockAnimation) {
			finishClose();
			return;
		}
		list.cancelPress();
		closingAt = Util.getMillis();
	}

	private void finishClose() {
		if (minecraft.gui.screen() == this) {
			minecraft.gui.setScreen(null);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		idleSince = Util.getMillis();
		if (closingAt >= 0) {
			return true;
		}
		if (layout == null) {
			return false;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && DockToggleHandler.isToggleMouse(event)) {
			onClose();
			return true;
		}
		// Vanilla widgets first (buttons, prompt choices).
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (controller.prompt() == null && list.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return layout.panel().contains(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		idleSince = Util.getMillis();
		if (list != null && list.mouseDragged(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		idleSince = Util.getMillis();
		if (list != null && list.mouseReleased(event.x(), event.y(), event.button(), this::onPackClicked)) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		idleSince = Util.getMillis();
		return controller.prompt() == null && list != null && list.mouseScrolled(x, y, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		idleSince = Util.getMillis();
		if (closingAt >= 0) {
			return true;
		}
		DockPrompt prompt = controller.prompt();
		if (event.isEscape()) {
			if (list.isDragging()) {
				list.cancelPress();
				return true;
			}
			if (prompt != null) {
				prompt.onEscape().run();
				return true;
			}
		}
		if (DockToggleHandler.isToggleKey(event, getFocused())) {
			onClose();
			return true;
		}
		if (prompt == null && handleListKey(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	private boolean handleListKey(KeyEvent event) {
		String active = controller.activePackName().orElse(null);
		if (event.isUp() || event.isDown()) {
			int delta = event.isUp() ? -1 : 1;
			if (event.hasShiftDown()) {
				list.moveCursorPack(delta);
			} else {
				list.moveCursor(delta, active);
			}
			clearFocus();
			return true;
		}
		if (event.isConfirmation() && list.cursorPack() != null && getFocused() == null) {
			onPackClicked(list.cursorPack());
			return true;
		}
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9 && !event.hasControlDown() && !event.hasAltDown()) {
			ShaderPack pack = list.packAt(key - InputConstants.KEY_1);
			if (pack != null) {
				onPackClicked(pack);
				return true;
			}
		}
		return false;
	}

	private void onPackClicked(ShaderPack pack) {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
		controller.activate(pack);
	}
}
