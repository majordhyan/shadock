package dev.shadock.shadock.ui;

import dev.shadock.shadock.iris.IrisCompat;
import dev.shadock.shadock.shader.ShaderImportResult;
import dev.shadock.shadock.shader.ShaderImporter;
import dev.shadock.shadock.shader.ShaderPackScanner;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

// The + button: file picker -> importer -> duplicate prompt -> replace / keep both.
// Lives on the controller rather than the screen so an import keeps going if the dock is closed mid-way.
final class ImportFlow {
	private final DockController dock;
	private final IrisCompat iris;
	private final ShaderPackScanner scanner;
	private final ShaderImporter importer;
	private final Consumer<Runnable> afterNextFrame;

	private @Nullable DockPrompt prompt;
	private boolean pickerOpen;

	ImportFlow(DockController dock, IrisCompat iris, ShaderPackScanner scanner, ShaderImporter importer, Consumer<Runnable> afterNextFrame) {
		this.dock = dock;
		this.iris = iris;
		this.scanner = scanner;
		this.importer = importer;
		this.afterNextFrame = afterNextFrame;
	}

	@Nullable DockPrompt prompt() {
		return prompt;
	}

	boolean isBusy() {
		return pickerOpen || prompt != null || importer.isBusy();
	}

	void start() {
		pickerOpen = true;
		dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.choose_file"));
		NativeFilePicker.openZip(
			path -> {
				pickerOpen = false;
				if (path == null) {
					dock.setStatus(DockStatus.of(DockStatus.Kind.INFO, "shadock.status.import_cancelled"));
				} else {
					dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing"));
					importer.importFile(path, result -> onResult(path, result));
				}
			},
			error -> {
				pickerOpen = false;
				dock.setStatus(DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.import_failed"));
			});
	}

	void cancelPrompt() {
		prompt = null;
		dock.setStatus(DockStatus.of(DockStatus.Kind.INFO, "shadock.status.import_cancelled"));
	}

	private void onResult(Path source, ShaderImportResult result) {
		switch (result.status()) {
			case IMPORTED -> {
				dock.setStatus(DockStatus.of(DockStatus.Kind.SUCCESS, "shadock.status.imported", result.fileName()));
				scanner.requestScan();
			}
			case INVALID -> dock.setStatus(DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.not_a_shader_pack"));
			case FAILED -> dock.setStatus(DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.import_failed"));
			case DUPLICATE -> onDuplicate(source, result.fileName());
			case ALREADY_PRESENT -> dock.setStatus(DockStatus.of(DockStatus.Kind.INFO, "shadock.status.already_installed", result.fileName()));
		}
	}

	private void onDuplicate(Path source, String name) {
		boolean active = isActive(name);
		switch (dock.config().duplicateImportBehavior) {
			case ASK -> {
				dock.setStatus(DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.file_exists"));
				prompt = new DockPrompt(
					Component.translatable(active ? "shadock.prompt.duplicate_active" : "shadock.prompt.duplicate", name),
					List.of(
						new DockPrompt.Choice(Component.translatable("shadock.prompt.replace"), false, () -> replace(source, name)),
						new DockPrompt.Choice(Component.translatable("shadock.prompt.keep_both"), true, () -> keepBoth(source)),
						new DockPrompt.Choice(Component.translatable("shadock.prompt.cancel"), false, this::cancelPrompt)),
					this::cancelPrompt);
			}
			// "Replace" as a setting still asks once; silently overwriting someone's pack is not a default
			case REPLACE -> {
				dock.setStatus(DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.file_exists"));
				prompt = new DockPrompt(
					Component.translatable(active ? "shadock.prompt.replace_confirm_active" : "shadock.prompt.replace_confirm", name),
					List.of(
						new DockPrompt.Choice(Component.translatable("shadock.prompt.replace"), true, () -> replace(source, name)),
						new DockPrompt.Choice(Component.translatable("shadock.prompt.cancel"), false, this::cancelPrompt)),
					this::cancelPrompt);
			}
			case KEEP_BOTH -> keepBoth(source);
			case CANCEL -> dock.setStatus(DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.file_exists"));
		}
	}

	private void keepBoth(Path source) {
		prompt = null;
		dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing"));
		importer.importKeepingBoth(source, result -> onResult(source, result));
	}

	private void replace(Path source, String name) {
		prompt = null;
		if (!isActive(name)) {
			dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing"));
			importer.importReplacing(source, result -> onReplaced(result, false));
			return;
		}
		// Iris holds the active zip open, and Windows won't let us overwrite an open file.
		// Shaders off first, then copy, then load it again.
		dock.setSwitching(true);
		dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.applying"));
		afterNextFrame.accept(() -> {
			iris.disableShaders();
			dock.setSwitching(false);
			dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing"));
			importer.importReplacing(source, result -> onReplaced(result, true));
		});
	}

	private void onReplaced(ShaderImportResult result, boolean reactivate) {
		DockStatus outcome = result.status() == ShaderImportResult.Status.IMPORTED
			? DockStatus.of(DockStatus.Kind.SUCCESS, "shadock.status.imported", result.fileName())
			: DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.import_failed");
		dock.setStatus(outcome);
		scanner.requestScan();
		if (!reactivate || !iris.isAvailable()) {
			return;
		}
		// reload even when the copy failed, the old file is still there and the player expects shaders back on
		dock.setSwitching(true);
		dock.setStatus(DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.applying"));
		afterNextFrame.accept(() -> {
			boolean ok = iris.setActiveShaderPack(result.fileName());
			dock.setSwitching(false);
			dock.setStatus(ok ? outcome : dock.irisFailure());
		});
	}

	private boolean isActive(String name) {
		return dock.activePackName().map(name::equals).orElse(false);
	}
}
