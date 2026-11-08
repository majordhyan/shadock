package dev.shadock.shadock.shader;

import dev.shadock.shadock.Shadock;
import dev.shadock.shadock.util.FileNames;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class ShaderImporter {
	public enum Validation { VALID, NOT_ZIP, NO_SHADERS, NESTED_ZIP, CORRUPT, UNREADABLE }

	private static final String TEMP_SUFFIX = ".shadock-tmp";

	private final Supplier<Path> targetDir;
	private final Executor mainThread;
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Shadock importer");
		t.setDaemon(true);
		return t;
	});
	private final AtomicBoolean busy = new AtomicBoolean(false);

	public ShaderImporter(Supplier<Path> targetDir, Executor mainThread) {
		this.targetDir = targetDir;
		this.mainThread = mainThread;
	}

	public boolean isBusy() {
		return busy.get();
	}

	public void importFile(Path source, Consumer<ShaderImportResult> callback) {
		String name = source.getFileName().toString();
		run(callback, name, () -> {
			Validation validation = validate(source);
			if (validation != Validation.VALID) {
				Shadock.LOGGER.info("Rejected {}: {}", source, validation);
				return new ShaderImportResult(ShaderImportResult.Status.INVALID, name, validation.name());
			}
			Path dir = targetDir.get();
			Path target = dir.resolve(name);
			if (Files.exists(target)) {
				// Picking a file that is already in the shaderpacks folder is not a duplicate: nothing to do.
				boolean same = Files.isSameFile(source, target);
				return ShaderImportResult.of(same ? ShaderImportResult.Status.ALREADY_PRESENT : ShaderImportResult.Status.DUPLICATE, name);
			}
			copy(source, target);
			return ShaderImportResult.of(ShaderImportResult.Status.IMPORTED, name);
		});
	}

	public void importKeepingBoth(Path source, Consumer<ShaderImportResult> callback) {
		String original = source.getFileName().toString();
		run(callback, original, () -> {
			Path dir = targetDir.get();
			String name = FileNames.firstFreeName(dir, original);
			copy(source, dir.resolve(name));
			return ShaderImportResult.of(ShaderImportResult.Status.IMPORTED, name);
		});
	}

	// caller has to make Iris release the file first, see ImportFlow.replace
	public void importReplacing(Path source, Consumer<ShaderImportResult> callback) {
		String name = source.getFileName().toString();
		run(callback, name, () -> {
			Path target = targetDir.get().resolve(name);
			if (Files.exists(target) && Files.isSameFile(source, target)) {
				return ShaderImportResult.of(ShaderImportResult.Status.IMPORTED, name);
			}
			copy(source, target);
			return ShaderImportResult.of(ShaderImportResult.Status.IMPORTED, name);
		});
	}

	private interface Job {
		ShaderImportResult call() throws IOException;
	}

	private void run(Consumer<ShaderImportResult> callback, String name, Job job) {
		if (!busy.compareAndSet(false, true)) {
			mainThread.execute(() -> callback.accept(new ShaderImportResult(ShaderImportResult.Status.FAILED, name, "busy")));
			return;
		}
		io.execute(() -> {
			ShaderImportResult outcome;
			try {
				outcome = job.call();
			} catch (IOException | RuntimeException e) {
				Shadock.LOGGER.error("Import of {} failed", name, e);
				outcome = new ShaderImportResult(ShaderImportResult.Status.FAILED, name, e.getMessage());
			}
			ShaderImportResult delivered = outcome;
			busy.set(false);
			mainThread.execute(() -> callback.accept(delivered));
		});
	}

	// temp file + move, so Iris never picks up half a zip if the copy dies
	private static void copy(Path source, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
		try {
			Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	// Mirrors Iris.isValidShaderpack: some directory in the zip has to be called "shaders".
	// Zips inside zips are rejected, people download a pack-of-packs and wonder why nothing shows up.
	public static Validation validate(Path file) {
		if (!Files.isRegularFile(file) || !FileNames.isZip(file.getFileName().toString())) {
			return Validation.NOT_ZIP;
		}
		boolean containsZip = false;
		try (ZipFile zip = new ZipFile(file.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				String entry = entries.nextElement().getName().replace('\\', '/');
				if (hasShadersDirectory(entry)) {
					return Validation.VALID;
				}
				if (entry.toLowerCase(Locale.ROOT).endsWith(".zip")) {
					containsZip = true;
				}
			}
		} catch (ZipException e) {
			return Validation.CORRUPT;
		} catch (IOException e) {
			return Validation.UNREADABLE;
		}
		return containsZip ? Validation.NESTED_ZIP : Validation.NO_SHADERS;
	}

	static boolean hasShadersDirectory(String entryName) {
		String[] parts = entryName.split("/");
		// A "shaders" segment counts only if it is a directory: either more path follows or the entry ends with '/'.
		int lastDirIndex = entryName.endsWith("/") ? parts.length - 1 : parts.length - 2;
		for (int i = 0; i <= lastDirIndex; i++) {
			if (parts[i].equals("shaders")) {
				return true;
			}
		}
		return false;
	}
}
