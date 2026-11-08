package dev.shadock.shadock.config;

import dev.shadock.shadock.shader.ShaderPackSortMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadockConfigManagerTest {
	@TempDir
	Path configDir;

	@Test
	void createsDefaultsOnFirstLaunch() {
		ShadockConfigManager manager = new ShadockConfigManager(configDir);
		manager.load();
		assertTrue(Files.isRegularFile(manager.file()));
		assertEquals(ShaderPackSortMode.CUSTOM_ORDER, manager.get().sortMode);
		assertEquals(5, manager.get().autoHideDelaySeconds);
		assertEquals(DuplicateImportBehavior.ASK, manager.get().duplicateImportBehavior);
	}

	@Test
	void malformedJsonFallsBackToDefaults() throws IOException {
		Path file = configDir.resolve("shadock").resolve("shadock.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{ this is : not json ]");
		ShadockConfigManager manager = new ShadockConfigManager(configDir);
		manager.load();
		assertEquals(ShaderPackSortMode.CUSTOM_ORDER, manager.get().sortMode);
		assertTrue(Files.exists(file.resolveSibling("shadock.json.broken")));
	}

	@Test
	void invalidValuesAreRepaired() throws IOException {
		Path file = configDir.resolve("shadock").resolve("shadock.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
			{"sortMode": "SIDEWAYS", "autoHideDelaySeconds": 7, "customPackOrder": ["a.zip", "a.zip", "", "b"]}
			""");
		ShadockConfigManager manager = new ShadockConfigManager(configDir);
		manager.load();
		assertEquals(ShaderPackSortMode.CUSTOM_ORDER, manager.get().sortMode);
		assertEquals(5, manager.get().autoHideDelaySeconds);
		assertEquals(List.of("a.zip", "b"), manager.get().customPackOrder);
	}

	@Test
	void savedOrderSurvivesReload() {
		ShadockConfigManager manager = new ShadockConfigManager(configDir);
		manager.load();
		manager.get().customPackOrder = List.of("Photon.zip", "BSL.zip");
		manager.get().sortMode = ShaderPackSortMode.ALPHABETICAL;
		manager.save();

		ShadockConfigManager reloaded = new ShadockConfigManager(configDir);
		reloaded.load();
		assertEquals(List.of("Photon.zip", "BSL.zip"), reloaded.get().customPackOrder);
		assertEquals(ShaderPackSortMode.ALPHABETICAL, reloaded.get().sortMode);
	}
}
