package dev.shadock.shadock.shader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaderPackOrderStoreTest {
	private static ShaderPack pack(String name) {
		return ShaderPack.of(Path.of(name), ShaderPack.Type.ZIP, ShaderPack.Validity.VALID);
	}

	private final List<ShaderPack> packs = List.of(pack("bsl.zip"), pack("Photon.zip"), pack("Complementary.zip"), pack("aurora"));

	@Test
	void alphabeticalIsCaseInsensitive() {
		List<String> sorted = ShaderPackOrderStore.names(ShaderPackOrderStore.sort(packs, List.of(), ShaderPackSortMode.ALPHABETICAL));
		assertEquals(List.of("aurora", "bsl.zip", "Complementary.zip", "Photon.zip"), sorted);
	}

	@Test
	void customOrderFollowsSavedNames() {
		List<String> saved = List.of("Photon.zip", "aurora", "bsl.zip", "Complementary.zip");
		assertEquals(saved, ShaderPackOrderStore.names(ShaderPackOrderStore.sort(packs, saved, ShaderPackSortMode.CUSTOM_ORDER)));
	}

	@Test
	void newPacksAppearFirstAndMissingNamesAreSkipped() {
		List<String> saved = List.of("gone.zip", "Photon.zip", "bsl.zip");
		List<String> sorted = ShaderPackOrderStore.names(ShaderPackOrderStore.sort(packs, saved, ShaderPackSortMode.CUSTOM_ORDER));
		assertEquals(List.of("aurora", "Complementary.zip", "Photon.zip", "bsl.zip"), sorted);
	}

	@Test
	void pruneRemovesMissingPacks() {
		assertEquals(List.of("Photon.zip"), ShaderPackOrderStore.prune(List.of("gone.zip", "Photon.zip"), packs));
	}

	@Test
	void moveDownAndUp() {
		assertEquals(List.of("Photon.zip", "Complementary.zip", "bsl.zip", "aurora"), ShaderPackOrderStore.move(packs, 0, 2));
		assertEquals(List.of("aurora", "bsl.zip", "Photon.zip", "Complementary.zip"), ShaderPackOrderStore.move(packs, 3, 0));
	}

	@Test
	void moveClampsOutOfRangeTarget() {
		assertEquals(List.of("Photon.zip", "Complementary.zip", "aurora", "bsl.zip"), ShaderPackOrderStore.move(packs, 0, 99));
	}
}
