package dev.shadock.shadock.input;

import dev.shadock.shadock.ui.ShadockDockScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

// Vanilla stops delivering key mappings once any screen is open, so the close half of the toggle
// has to be handled by the dock screen itself.
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
