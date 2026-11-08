package dev.shadock.shadock.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.shadock.shadock.ShadockClient;
import dev.shadock.shadock.input.ShadockKeybinds;
import dev.shadock.shadock.shader.ShaderPack;
import dev.shadock.shadock.shader.ShaderPackScanner;
import dev.shadock.shadock.ui.DockController;
import dev.shadock.shadock.ui.DockLayoutManager;
import dev.shadock.shadock.ui.ShadockDockScreen;
import dev.shadock.shadock.ui.ShadockSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;

/**
 * Drives Shadock in a real client: opens the dock, switches shader packs through it, drags a row and
 * opens settings, asserting the results and capturing release screenshots along the way.
 *
 * <p>Needs real shader packs in the test run's shaderpacks folder (see build.gradle).
 */
public final class ShadockClientGameTest implements FabricClientGameTest {
	private static final int WIDTH = 1600;
	private static final int HEIGHT = 900;
	private static final int GUI_SCALE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		Path out = FabricLoader.getInstance().getGameDir().resolve("shadock-screenshots");

		// The default dock key must not collide with any other default binding (vanilla 26.3 uses O for Friends).
		String clash = context.computeOnClient(mc -> {
			var ours = ShadockKeybinds.openDock();
			for (var other : mc.options.keyMappings) {
				// Debug bindings only fire as F3+key combos, so they never steal a plain key press.
				boolean debugCombo = other.getCategory() == net.minecraft.client.KeyMapping.Category.DEBUG;
				if (other != ours && !debugCombo && other.getDefaultKey().equals(ours.getDefaultKey())) {
					return other.getName();
				}
			}
			return null;
		});
		check(clash == null, "default Open Shader Dock key is unique (clashes with " + clash + ")");
		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(GUI_SCALE);
			mc.resizeGui();
		});

		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(settings -> settings.setSeed(System.getProperty("shadock.seed", "shadock")))
			.create()) {
			world.getServer().runCommand("time set 1500");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamemode creative @a");
			for (String item : List.of("spyglass", "grass_block", "glass", "torch", "oak_log", "lantern", "cherry_sapling", "amethyst_cluster")) {
				world.getServer().runCommand("give @a minecraft:" + item);
			}
			world.getServer().runCommand("tp @a ~ ~12 ~");
			world.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyHotbarSlots[8]);
			context.getInput().lookAt(Float.parseFloat(System.getProperty("shadock.yaw", "150")), 6f);
			context.waitTicks(60);
			context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));

			DockController dock = ShadockClient.dock();

			// 1. Open the dock with the O key, exactly as a player would.
			context.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			context.setScreen(() -> null);
			context.waitTicks(2);
			context.getInput().pressKey(ShadockKeybinds.openDock());
			context.waitTicks(5);
			Object screen = context.computeOnClient(mc -> mc.gui.screen());
			check(screen instanceof ShadockDockScreen, "the Open Shader Dock key opens the dock (screen was " + screen + ")");
			context.waitFor(mc -> dock.scanState() == ShaderPackScanner.State.READY, 400);
			check(!dock.rows().isEmpty(), "the dock lists shader packs");

			// 2. Apply a pack through the dock and verify Iris actually loaded it.
			apply(context, dock, "Complementary");
			context.waitTicks(80);
			shot(context, out, "01-dock-complementary");

			// 3. Switch to another pack.
			apply(context, dock, "BSL");
			context.waitTicks(80);
			shot(context, out, "02-dock-bsl");

			// 4. Clicking the active pack must not reload.
			ShaderPack active = find(dock, "BSL");
			context.runOnClient(mc -> dock.activate(active));
			context.waitTicks(2);
			check(!dock.isSwitching(), "re-selecting the active pack does not reload");

			// 5. Drag the third row upward with real mouse input; the order must change.
			List<String> before = names(dock);
			DockLayoutManager.Layout layout = context.computeOnClient(mc -> DockLayoutManager.compute(
				mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), dock.rows().size(), 0));
			int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
			double rowX = (layout.list().x() + 40) * scale;
			double rowY = (layout.list().y() + DockLayoutManager.ROW_HEIGHT * 2 + 6) * scale;
			context.getInput().setCursorPos(rowX, rowY);
			context.waitTicks(2);
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			for (int i = 0; i < 6; i++) {
				context.getInput().moveCursor(0, -3 * scale);
				context.waitTick();
			}
			context.waitTicks(4);
			shot(context, out, "03-drag-to-reorder");
			for (int i = 0; i < 4; i++) {
				context.getInput().moveCursor(0, -3 * scale);
				context.waitTick();
			}
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(10);
			check(!names(dock).equals(before), "dragging a row changes the custom order");
			check(dock.activePackName().map(n -> n.contains("BSL")).orElse(false), "dragging never switches the pack");

			// 6. One more pack, with the cursor moved away so no row shows a hover state.
			context.getInput().setCursorPos(WIDTH - 10, 10);
			apply(context, dock, "Photon");
			context.waitTicks(80);
			shot(context, out, "04-dock-photon");

			// 7. Settings screen (opened from the gear button in normal play).
			context.setScreen(() -> new ShadockSettingsScreen(new ShadockDockScreen()));
			context.waitTicks(10);
			shot(context, out, "05-settings");
			context.setScreen(() -> null);
			context.waitTicks(5);
		}
	}

	private static void apply(ClientGameTestContext context, DockController dock, String namePart) {
		if (!(context.computeOnClient(mc -> mc.gui.screen()) instanceof ShadockDockScreen)) {
			context.setScreen(ShadockDockScreen::new);
		}
		ShaderPack pack = find(dock, namePart);
		context.runOnClient(mc -> dock.activate(pack));
		context.waitFor(mc -> !dock.isSwitching() && !dock.isBusy(), 2400);
		check(dock.activePackName().map(n -> n.equals(pack.fileName())).orElse(false), "Iris loaded " + pack.fileName());
	}

	private static ShaderPack find(DockController dock, String namePart) {
		return dock.rows().stream()
			.filter(p -> p.fileName().toLowerCase().contains(namePart.toLowerCase()) && p.canActivate())
			.findFirst()
			.orElseThrow(() -> new AssertionError("No shader pack containing '" + namePart + "' in the test shaderpacks folder"));
	}

	private static List<String> names(DockController dock) {
		return dock.rows().stream().map(ShaderPack::fileName).toList();
	}

	private static void shot(ClientGameTestContext context, Path out, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(WIDTH, HEIGHT).withDestinationDir(out));
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Check failed: " + what);
		}
	}

	@SuppressWarnings("unused")
	private static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
