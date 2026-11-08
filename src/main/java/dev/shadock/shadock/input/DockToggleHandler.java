package dev.shadock.shadock.input;

import dev.shadock.shadock.ui.ShadockDockScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Opens the dock from gameplay, and closes it when the same key is pressed again.
 * While any screen is open, vanilla does not deliver key mappings, so the dock checks the key itself.
 */
public final class DockToggleHandler {
	private DockToggleHandler() {
	}

	/** Called every client tick. Key presses only register here when no screen is open. */
	public static void onClientTick(Minecraft minecraft) {
		while (ShadockKeybinds.openDock().consumeClick()) {
			if (minecraft.gui.screen() == null && minecraft.level != null && minecraft.player != null) {
				minecraft.gui.setScreen(new ShadockDockScreen());
			}
		}
	}

	/** True if this key event is the dock key and should close the dock. */
	public static boolean isToggleKey(KeyEvent event, GuiEventListener focused) {
		if (focused != null && focused.capturesInput()) {
			// The player is typing in a text field; the key is text, not a toggle.
			return false;
		}
		return ShadockKeybinds.openDock().matches(event);
	}

	public static boolean isToggleMouse(MouseButtonEvent event) {
		return ShadockKeybinds.openDock().matchesMouse(event);
	}
}
