package dev.shadock.shadock.shader;

import dev.shadock.shadock.iris.IrisCompat;
import dev.shadock.shadock.iris.IrisPackInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackScannerTest {
	@TempDir
	Path dir;

	/** Iris stand-in: the list comes from a supplier so tests can change it or make it fail. */
	private final class FakeIris implements IrisCompat {
		final AtomicInteger listCalls = new AtomicInteger();
		volatile Supplier<List<IrisPackInfo>> packs = List::of;

		@Override public boolean isAvailable() { return true; }
		@Override public Optional<String> unavailableReasonKey() { return Optional.empty(); }
		@Override public Path getShaderpacksDirectory() { return dir; }
		@Override public List<IrisPackInfo> getShaderPacks() { listCalls.incrementAndGet(); return packs.get(); }
		@Override public Optional<String> getActiveShaderPackName() { return Optional.empty(); }
		@Override public boolean setActiveShaderPack(String packName) { return false; }
		@Override public boolean disableShaders() { return false; }
		@Override public Optional<String> getLastError() { return Optional.empty(); }
	}

	private static void awaitGeneration(ShaderPackScanner scanner, int generation) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		while (scanner.generation() < generation && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
		assertTrue(scanner.generation() >= generation, "scan did not finish");
	}

	@Test
	void scanMapsZipsFoldersAndValidity() throws InterruptedException {
		FakeIris iris = new FakeIris();
		iris.packs = () -> List.of(
			new IrisPackInfo("BSL.zip", dir.resolve("BSL.zip"), true, true),
			new IrisPackInfo("Folder", dir.resolve("Folder"), false, true),
			new IrisPackInfo("junk.zip", dir.resolve("junk.zip"), true, false));
		ShaderPackScanner scanner = new ShaderPackScanner(iris);
		scanner.requestScan();
		awaitGeneration(scanner, 1);

		assertEquals(ShaderPackScanner.State.READY, scanner.state());
		List<ShaderPack> packs = scanner.packs();
		assertEquals(3, packs.size());
		assertEquals(ShaderPack.Type.ZIP, packs.get(0).type());
		assertEquals("BSL", packs.get(0).displayName());
		assertEquals(ShaderPack.Type.FOLDER, packs.get(1).type());
		assertEquals(ShaderPack.Validity.INVALID, packs.get(2).validity());
		assertFalse(packs.get(2).canActivate());
	}

	@Test
	void failedScanReportsFailedWithoutThrowing() throws InterruptedException {
		FakeIris iris = new FakeIris();
		iris.packs = () -> {
			throw new IllegalStateException("folder unreadable");
		};
		ShaderPackScanner scanner = new ShaderPackScanner(iris);
		scanner.requestScan();
		awaitGeneration(scanner, 1);
		assertEquals(ShaderPackScanner.State.FAILED, scanner.state());
	}

	@Test
	void cacheIsNotRescannedWithoutChanges() throws InterruptedException {
		FakeIris iris = new FakeIris();
		ShaderPackScanner scanner = new ShaderPackScanner(iris);
		scanner.refreshIfDirty();
		awaitGeneration(scanner, 1);
		int calls = iris.listCalls.get();
		for (int i = 0; i < 20; i++) {
			scanner.refreshIfDirty();
		}
		Thread.sleep(100);
		assertEquals(calls, iris.listCalls.get());
	}

	@Test
	void irisOptionFilesAndTempFilesDoNotTriggerRescans() {
		assertFalse(ShaderPackScanner.isRelevant("BSL.zip.txt"));
		assertFalse(ShaderPackScanner.isRelevant("Pack.zip.shadock-tmp"));
		assertTrue(ShaderPackScanner.isRelevant("New Pack.zip"));
		assertTrue(ShaderPackScanner.isRelevant("FolderPack"));
	}
}
