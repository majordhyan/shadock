package dev.shadock.shadock.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNamesTest {
	@Test
	void freeNameIsReturnedUnchanged() {
		assertEquals("BSL.zip", FileNames.firstFreeName("BSL.zip", Set.<String>of()::contains));
	}

	@Test
	void keepBothFollowsNumberedPattern() {
		Set<String> taken = Set.of("BSL.zip", "BSL (2).zip", "BSL (3).zip");
		assertEquals("BSL (4).zip", FileNames.firstFreeName("BSL.zip", taken::contains));
		assertEquals("BSL (2).zip", FileNames.firstFreeName("BSL.zip", Set.of("BSL.zip")::contains));
	}

	@Test
	void suffixGoesBeforeUppercaseExtension() {
		assertEquals("Pack (2).ZIP", FileNames.firstFreeName("Pack.ZIP", Set.of("Pack.ZIP")::contains));
	}

	@Test
	void namesWithSpacesAndDots() {
		Set<String> taken = Set.of("Complementary Unbound r5.3.zip");
		assertEquals("Complementary Unbound r5.3 (2).zip", FileNames.firstFreeName("Complementary Unbound r5.3.zip", taken::contains));
	}

	@Test
	void displayNameStripsZip() {
		assertEquals("Photon v1.2", FileNames.displayName("Photon v1.2.zip"));
		assertEquals("MyFolderPack", FileNames.displayName("MyFolderPack"));
	}
}
