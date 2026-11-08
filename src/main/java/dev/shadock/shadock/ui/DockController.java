package dev.shadock.shadock.ui;

import dev.shadock.shadock.config.ShadockConfig;
import dev.shadock.shadock.config.ShadockConfigManager;
import dev.shadock.shadock.iris.IrisCompat;
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

// One per session, not per screen: the dock gets closed and reopened constantly, and the status line,
// an open prompt or a running import have to survive that.
public final class DockController {
	private final ShadockConfigManager configManager;
	private final IrisCompat iris;
	private final ShaderPackScanner scanner;
	private final ImportFlow imports;

	private DockStatus status = DockStatus.ready();
	private List<ShaderPack> rows = List.of();
	private int rowsGeneration = -1;
	private boolean rowsDirty = true;
	private boolean switching;

	/** Work that must run on the render thread at a safe point, after one frame has shown its status. */
	private @Nullable Runnable pendingWork;
	private int framesSinceRequest;

	public DockController(ShadockConfigManager configManager, IrisCompat iris, ShaderPackScanner scanner, ShaderImporter importer) {
		this.configManager = configManager;
		this.iris = iris;
		this.scanner = scanner;
		this.imports = new ImportFlow(this, iris, scanner, importer, this::runAfterNextFrame);
	}

	public void onDockOpened() {
		scanner.onDockOpened();
		if (!iris.isAvailable() && !isBusy()) {
			status = unavailable();
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

	public ShadockConfig config() {
		return configManager.get();
	}

	public DockStatus status() {
		return status;
	}

	public void setStatus(DockStatus status) {
		this.status = status;
	}

	public @Nullable DockPrompt prompt() {
		return imports.prompt();
	}

	public ShaderPackScanner.State scanState() {
		return scanner.state();
	}

	public Optional<String> activePackName() {
		return iris.getActiveShaderPackName();
	}

	public Path shaderpacksDirectory() {
		return iris.getShaderpacksDirectory();
	}

	public boolean isSwitching() {
		return switching;
	}

	void setSwitching(boolean switching) {
		this.switching = switching;
	}

	// auto-hide, drag start and pack clicks all check this, so anything long-running belongs in here
	public boolean isBusy() {
		return switching || pendingWork != null || imports.isBusy();
	}

	public boolean canDrag() {
		return config().sortMode == ShaderPackSortMode.CUSTOM_ORDER;
	}

	// Rebuilt only when a scan finished or the order changed; the screen compares by identity every frame.
	public List<ShaderPack> rows() {
		int generation = scanner.generation();
		if (rowsDirty || generation != rowsGeneration) {
			rowsGeneration = generation;
			rowsDirty = false;
			List<ShaderPack> packs = scanner.packs();
			ShadockConfig config = config();
			// only prune after a real scan, an empty list before the first scan would wipe the saved order
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

	public void activate(ShaderPack pack) {
		if (!iris.isAvailable()) {
			status = unavailable();
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
			status = ok ? DockStatus.of(DockStatus.Kind.SUCCESS, "shadock.status.applied", pack.displayName()) : irisFailure();
		});
	}

	DockStatus irisFailure() {
		return iris.getLastError()
			.map(message -> DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.iris_error", message))
			.orElseGet(() -> DockStatus.of(DockStatus.Kind.ERROR, "shadock.status.iris_load_failed"));
	}

	private DockStatus unavailable() {
		return new DockStatus(Component.translatable(iris.unavailableReasonKey().orElse("shadock.status.switching_unavailable")), DockStatus.Kind.ERROR);
	}

	public void reorder(int from, int to) {
		if (!canDrag() || from == to) {
			return;
		}
		config().customPackOrder = ShaderPackOrderStore.move(rows(), from, to);
		configManager.save();
		rowsDirty = true;
	}

	public void setSortMode(ShaderPackSortMode mode) {
		config().sortMode = mode;
		configManager.save();
		rowsDirty = true;
	}

	public void toggleSortMode() {
		boolean toAlpha = config().sortMode == ShaderPackSortMode.CUSTOM_ORDER;
		setSortMode(toAlpha ? ShaderPackSortMode.ALPHABETICAL : ShaderPackSortMode.CUSTOM_ORDER);
		status = DockStatus.of(DockStatus.Kind.INFO, toAlpha ? "shadock.status.sort_alpha" : "shadock.status.sort_custom");
	}

	public void startImport() {
		if (!isBusy()) {
			imports.start();
		}
	}
}
