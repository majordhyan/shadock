package dev.shadock.shadock;

import dev.shadock.shadock.config.ShadockConfigManager;
import dev.shadock.shadock.input.DockToggleHandler;
import dev.shadock.shadock.input.ShadockKeybinds;
import dev.shadock.shadock.iris.IrisCompat;
import dev.shadock.shadock.iris.IrisCompatFactory;
import dev.shadock.shadock.shader.ShaderImporter;
import dev.shadock.shadock.shader.ShaderPackScanner;
import dev.shadock.shadock.ui.DockController;
import dev.shadock.shadock.ui.ShadockDockScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class ShadockClient implements ClientModInitializer {
	private static ShadockConfigManager config;
	private static IrisCompat iris;
	private static ShaderPackScanner scanner;
	private static ShaderImporter importer;
	private static DockController dock;

	@Override
	public void onInitializeClient() {
		config = new ShadockConfigManager(FabricLoader.getInstance().getConfigDir());
		config.load();

		iris = IrisCompatFactory.create();
		scanner = new ShaderPackScanner(iris);
		importer = new ShaderImporter(iris::getShaderpacksDirectory, task -> Minecraft.getInstance().execute(task));
		dock = new DockController(config, iris, scanner, importer);

		ShadockKeybinds.register();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			DockToggleHandler.onClientTick(minecraft);
			dock.tick(minecraft.gui.screen() instanceof ShadockDockScreen);
		});
	}

	public static ShadockConfigManager config() {
		return config;
	}

	public static DockController dock() {
		return dock;
	}
}
