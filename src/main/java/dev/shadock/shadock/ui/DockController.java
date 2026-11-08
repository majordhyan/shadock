package dev.shadock.shadock.ui;

import dev.shadock.shadock.config.ShadockConfig;
import dev.shadock.shadock.config.ShadockConfigManager;
import dev.shadock.shadock.iris.IrisCompat;
import dev.shadock.shadock.shader.ShaderImportResult;
import dev.shadock.shadock.shader.ShaderImporter;
import dev.shadock.shadock.shader.ShaderPack;
import dev.shadock.shadock.shader.ShaderPackOrderStore;
import dev.shadock.shadock.shader.ShaderPackScanner;
import dev.shadock.shadock.shader.ShaderPackSortMode;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Session-wide dock state and actions. The screen is only a view: closing and reopening the dock
 * keeps the status line, a pending prompt, and an import that is still running.
 */
public final class DockController {
	private final ShadockConfigManager configManager;
	private final IrisCompat iris;
	private final ShaderPackScanner scanner;
	private final ShaderImporter importer;

	private DockStatus status = DockStatus.ready();
	private List<ShaderPack> rows = List.of();
	private int rowsGeneration = -1;
	private boolean rowsDirty = true;
	private @Nullable DockPrompt prompt;
	private boolean filePickerOpen;
	private boolean switching;

	/** Work that must run on the render thread at a safe point, after one frame has shown its status. */
	private @Nullable Runnable pendingWork;
	private int framesSinceRequest;

	public DockController(ShadockConfigManager configManager, IrisCompat iris, ShaderPackScanner scanner, ShaderImporter importer) {
		this.configManager = configManager;
		this.iris = iris;
		this.scanner = scanner;
		this.importer = importer;
	}

	// ---- lifecycle -------------------------------------------------------------------------------

	public void onDockOpened() {
		scanner.onDockOpened();
		if (!iris.isAvailable() && !isBusy()) {
			status = new DockStatus(Component.translatable(iris.unavailableReasonKey().orElse("shadock.status.switching_unavailable")), DockStatus.Kind.ERROR);
		}
	}

	/**
	 * Called every client tick, the same safe point where Iris handles its own reload key.
	 * Pending work also runs when the dock was closed before its frame was drawn.
	 */
	public void tick(boolean dockVisible) {
		if (dockVisible) {
			scanner.refreshIfDirty();
		}
		if (pendingWork != null && (framesSinceRequest >= 1 || !dockVisible)) {
			Runnable work = pendingWork;
			pendingWork = null;
			work.run();
		}
	}

	/** Called after each dock frame, so pending work only runs once its status has been drawn. */
	public void onFrameRendered() {
		if (pendingWork != null) {
			framesSinceRequest++;
		}
	}

	private void runAfterNextFrame(Runnable work) {
		pendingWork = work;
		framesSinceRequest = 0;
	}

	// ---- state for the view ------------------------------------------------------------------------

	public ShadockConfig config() {
		return configManager.get();
	}

	public DockStatus status() {
		return status;
	}

	public @Nullable DockPrompt prompt() {
		return prompt;
	}

	public ShaderPackScanner.State scanState() {
		return scanner.state();
	}

	public boolean irisAvailable() {
		return iris.isAvailable();
	}

	public Optional<String> activePackName() {
		return iris.getActiveShaderPackName();
	}

	public boolean isSwitching() {
		return switching;
	}

	public boolean isFilePickerOpen() {
		return filePickerOpen;
	}

	/** True while anything is in progress that must not be interrupted (auto-hide, drag, clicks). */
	public boolean isBusy() {
		return switching || filePickerOpen || prompt != null || importer.isBusy() || pendingWork != null;
	}

	public boolean canDrag() {
		return config().sortMode == ShaderPackSortMode.CUSTOM_ORDER;
	}

	/** Displayed rows, rebuilt only when a scan finished or the order/sort mode changed. */
	public List<ShaderPack> rows() {
		int generation = scanner.generation();
		if (rowsDirty || generation != rowsGeneration) {
			rowsGeneration = generation;
			rowsDirty = false;
			List<ShaderPack> packs = scanner.packs();
			ShadockConfig config = config();
			if (scanner.state() == ShaderPackScanner.State.READY) {
				List<String> pruned = ShaderPackOrderStore.prune(config.customPackOrder, packs);
				if (pruned.size() != config.customPackOrder.size()) {
					config.customPackOrder = pruned;
					configManager.save();
				}
			}
			rows = ShaderPackOrderStore.sort(packs, config.customPackOrder, config.sortMode);
		}
		return rows;
	}

	public void invalidateRows() {
		rowsDirty = true;
	}

	public void setStatus(DockStatus status) {
		this.status = status;
	}

	// ---- shader switching -----------------------------------------------------------------------

	public void activate(ShaderPack pack) {
		if (!iris.isAvailable()) {
			status = new DockStatus(Component.translatable(iris.unavailableReasonKey().orElse("shadock.status.switching_unavailable")), DockStatus.Kind.ERROR);
			return;
		}
		if (isBusy()) {
			return;
		}
		if (!pack.canActivate()) {
			status = DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.invalid_pack");
			return;
		}
		if (activePackName().map(pack.fileName()::equals).orElse(false)) {
			status = DockStatus.of(DockStatus.Kind.INFO, "shadock.status.already_active");
			return;
		}
		if (!Files.exists(pack.path())) {
			status = DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.pack_missing");
			scanner.requestScan();
			return;
		}

		switching = true;
		status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.applying");
		runAfterNextFrame(() -> {
			boolean ok = iris.setActiveShaderPack(pack.fileName());
			switching = false;
			status = ok
				? DockStatus.of(DockStatus.Kind.SUCCESS, "shadock.status.applied", pack.displayName())
				: irisFailure();
		});
	}

	private DockStatus irisFailure() {
		return iris.getLastError()
			.map(message -> DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.iris_error", message))
			.orElseGet(() -> DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.iris_load_failed"));
	}

	// ---- ordering --------------------------------------------------------------------------------

	public void reorder(int from, int to) {
		if (!canDrag() || from == to) {
			return;
		}
		ShadockConfig config = config();
		config.customPackOrder = ShaderPackOrderStore.move(rows(), from, to);
		configManager.save();
		rowsDirty = true;
	}

	public void toggleSortMode() {
		setSortMode(config().sortMode == ShaderPackSortMode.CUSTOM_ORDER ? ShaderPackSortMode.ALPHABETICAL : ShaderPackSortMode.CUSTOM_ORDER);
		status = DockStatus.of(DockStatus.Kind.INFO,
			config().sortMode == ShaderPackSortMode.CUSTOM_ORDER ? "shadock.status.sort_custom" : "shadock.status.sort_alpha");
	}

	public java.nio.file.Path shaderpacksDirectory() {
		return iris.getShaderpacksDirectory();
	}

	public void setSortMode(ShaderPackSortMode mode) {
		config().sortMode = mode;
		configManager.save();
		rowsDirty = true;
	}

	// ---- import ----------------------------------------------------------------------------------

	public void startImport() {
		if (isBusy()) {
			return;
		}
		filePickerOpen = true;
		status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.choose_file");
		NativeFilePicker.openZip(
			path -> {
				filePickerOpen = false;
				if (path == null) {
					status = DockStatus.of(DockStatus.Kind.INFO, "shadock.status.import_cancelled");
				} else {
					beginImport(path);
				}
			},
			error -> {
				filePickerOpen = false;
				status = DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.import_failed");
			});
	}

	private void beginImport(Path source) {
		status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing");
		importer.importFile(source, result -> onImportResult(source, result));
	}

	private void onImportResult(Path source, ShaderImportResult result) {
		switch (result.status()) {
			case IMPORTED -> {
				status = DockStatus.of(DockStatus.Kind.SUCCESS, "shadock.status.imported", result.fileName());
				scanner.requestScan();
			}
			case INVALID -> status = DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.not_a_shader_pack");
			case FAILED -> status = DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.import_failed");
			case CANCELLED -> status = DockStatus.of(DockStatus.Kind.INFO, "shadock.status.import_cancelled");
			case DUPLICATE -> onDuplicate(source, result.fileName());
			case ALREADY_PRESENT -> status = DockStatus.of(DockStatus.Kind.INFO, "shadock.status.already_installed", result.fileName());
		}
	}

	private void onDuplicate(Path source, String name) {
		boolean active = activePackName().map(name::equals).orElse(false);
		switch (config().duplicateImportBehavior) {
			case ASK -> {
				status = DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.file_exists");
				prompt = new DockPrompt(
					Component.translatable(active ? "shadock.prompt.duplicate_active" : "shadock.prompt.duplicate", name),
					List.of(
						new DockPrompt.Choice(Component.translatable("shadock.prompt.replace"), false, () -> replace(source, name)),
						new DockPrompt.Choice(Component.translatable("shadock.prompt.keep_both"), true, () -> keepBoth(source)),
						new DockPrompt.Choice(Component.translatable("shadock.prompt.cancel"), false, this::cancelPrompt)),
					this::cancelPrompt);
			}
			case REPLACE -> {
				status = DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.file_exists");
				prompt = new DockPrompt(
					Component.translatable(active ? "shadock.prompt.replace_confirm_active" : "shadock.prompt.replace_confirm", name),
					List.of(
						new DockPrompt.Choice(Component.translatable("shadock.prompt.replace"), true, () -> replace(source, name)),
						new DockPrompt.Choice(Component.translatable("shadock.prompt.cancel"), false, this::cancelPrompt)),
					this::cancelPrompt);
			}
			case KEEP_BOTH -> keepBoth(source);
			case CANCEL -> status = DockStatus.of(DockStatus.Kind.WARNING, "shadock.status.file_exists");
		}
	}

	public void cancelPrompt() {
		prompt = null;
		status = DockStatus.of(DockStatus.Kind.INFO, "shadock.status.import_cancelled");
	}

	private void keepBoth(Path source) {
		prompt = null;
		status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing");
		importer.importKeepingBoth(source, result -> onImportResult(source, result));
	}

	private void replace(Path source, String name) {
		prompt = null;
		boolean wasActive = activePackName().map(name::equals).orElse(false);
		if (!wasActive) {
			status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing");
			importer.importReplacing(source, result -> onReplaced(result, false));
			return;
		}
		// Iris keeps the active ZIP open (locked on Windows): turn shaders off, replace, load it again.
		switching = true;
		status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.applying");
		runAfterNextFrame(() -> {
			iris.disableShaders();
			switching = false;
			status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.importing");
			importer.importReplacing(source, result -> onReplaced(result, true));
		});
	}

	private void onReplaced(ShaderImportResult result, boolean reactivate) {
		if (result.status() == ShaderImportResult.Status.IMPORTED) {
			status = DockStatus.of(DockStatus.Kind.SUCCESS, "shadock.status.imported", result.fileName());
		} else {
			status = DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.import_failed");
		}
		scanner.requestScan();
		if (!reactivate || !iris.isAvailable()) {
			return;
		}
		// Load the pack again, whether it is the new file or (after a failed copy) the old one.
		DockStatus afterImport = status;
		switching = true;
		status = DockStatus.of(DockStatus.Kind.BUSY, "shadock.status.applying");
		runAfterNextFrame(() -> {
			boolean ok = iris.setActiveShaderPack(result.fileName());
			switching = false;
			status = ok ? afterImport : irisFailure();
		});
	}
}
