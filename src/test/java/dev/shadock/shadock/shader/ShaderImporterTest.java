package dev.shadock.shadock.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderImporterTest {
	@TempDir
	Path dir;

	private Path zip(String name, String... entries) throws IOException {
		Path file = dir.resolve(name);
		try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (String entry : entries) {
				zip.putNextEntry(new ZipEntry(entry));
				zip.closeEntry();
			}
		}
		return file;
	}

	@Test
	void shadersAtRootIsValid() throws IOException {
		assertEquals(ShaderImporter.Validation.VALID, ShaderImporter.validate(zip("a.zip", "shaders/final.fsh")));
	}

	@Test
	void shadersInsideTopFolderIsValid() throws IOException {
		assertEquals(ShaderImporter.Validation.VALID, ShaderImporter.validate(zip("b.zip", "Pack-master/shaders/composite.fsh")));
	}

	@Test
	void explicitDirectoryEntryIsValid() throws IOException {
		assertEquals(ShaderImporter.Validation.VALID, ShaderImporter.validate(zip("c.zip", "shaders/")));
	}

	@Test
	void resourcePackIsRejected() throws IOException {
		assertEquals(ShaderImporter.Validation.NO_SHADERS, ShaderImporter.validate(zip("rp.zip", "pack.mcmeta", "assets/minecraft/textures/a.png")));
	}

	@Test
	void fileNamedShadersIsNotADirectory() throws IOException {
		assertEquals(ShaderImporter.Validation.NO_SHADERS, ShaderImporter.validate(zip("d.zip", "docs/shaders")));
	}

	@Test
	void nestedZipIsRejected() throws IOException {
		assertEquals(ShaderImporter.Validation.NESTED_ZIP, ShaderImporter.validate(zip("outer.zip", "Inner Shader.zip", "readme.txt")));
	}

	@Test
	void corruptFileIsRejected() throws IOException {
		Path file = dir.resolve("broken.zip");
		Files.writeString(file, "this is not a zip");
		ShaderImporter.Validation result = ShaderImporter.validate(file);
		assertTrue(result == ShaderImporter.Validation.CORRUPT || result == ShaderImporter.Validation.UNREADABLE, result.name());
	}

	@Test
	void nonZipIsRejected() throws IOException {
		Path file = dir.resolve("pack.rar");
		Files.writeString(file, "x");
		assertEquals(ShaderImporter.Validation.NOT_ZIP, ShaderImporter.validate(file));
	}

	private static ShaderImportResult await(java.util.function.Consumer<java.util.function.Consumer<ShaderImportResult>> start) throws Exception {
		java.util.concurrent.CompletableFuture<ShaderImportResult> future = new java.util.concurrent.CompletableFuture<>();
		start.accept(future::complete);
		return future.get(10, java.util.concurrent.TimeUnit.SECONDS);
	}

	@Test
	void importFlowHandlesNewDuplicateSameFileAndKeepBoth() throws Exception {
		Path packs = Files.createDirectories(dir.resolve("shaderpacks"));
		Path downloads = Files.createDirectories(dir.resolve("downloads"));
		Path source = zip("Cool Pack.zip", "shaders/final.fsh");
		Path download = Files.move(source, downloads.resolve("Cool Pack.zip"));
		ShaderImporter importer = new ShaderImporter(() -> packs, Runnable::run);

		ShaderImportResult first = await(cb -> importer.importFile(download, cb));
		assertEquals(ShaderImportResult.Status.IMPORTED, first.status());
		assertTrue(Files.exists(packs.resolve("Cool Pack.zip")));

		ShaderImportResult again = await(cb -> importer.importFile(download, cb));
		assertEquals(ShaderImportResult.Status.DUPLICATE, again.status());

		ShaderImportResult same = await(cb -> importer.importFile(packs.resolve("Cool Pack.zip"), cb));
		assertEquals(ShaderImportResult.Status.ALREADY_PRESENT, same.status());

		ShaderImportResult both = await(cb -> importer.importKeepingBoth(download, cb));
		assertEquals("Cool Pack (2).zip", both.fileName());
		ShaderImportResult both2 = await(cb -> importer.importKeepingBoth(download, cb));
		assertEquals("Cool Pack (3).zip", both2.fileName());

		ShaderImportResult invalid = await(cb -> {
			try {
				importer.importFile(zip("rp.zip", "pack.mcmeta"), cb);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		assertEquals(ShaderImportResult.Status.INVALID, invalid.status());
		assertFalse(Files.exists(packs.resolve("rp.zip")));
		try (var listing = Files.list(packs)) {
			assertTrue(listing.noneMatch(p -> p.toString().endsWith(".shadock-tmp")), "temp files must be cleaned up");
		}
	}

	@Test
	void shadersSegmentRules() {
		assertTrue(ShaderImporter.hasShadersDirectory("shaders/a.fsh"));
		assertTrue(ShaderImporter.hasShadersDirectory("x/y/shaders/"));
		assertFalse(ShaderImporter.hasShadersDirectory("shaders"));
		assertFalse(ShaderImporter.hasShadersDirectory("myshaders/a.fsh"));
	}
}
