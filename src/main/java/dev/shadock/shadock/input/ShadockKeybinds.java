package dev.shadock.shadock.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.shadock.shadock.Shadock;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

// H, not O: 26.3 put the Friends list on O, and both default bindings fire on one press.
public final class ShadockKeybinds {
	public static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Shadock.MOD_ID, "main"));

	private static KeyMapping openDock;

	private ShadockKeybinds() {
	}

	public static void register() {
		openDock = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.shadock.open_dock", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));
	}

	public static KeyMapping openDock() {
		return openDock;
	}
}
