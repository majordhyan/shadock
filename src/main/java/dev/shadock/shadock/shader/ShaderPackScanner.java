package dev.shadock.shadock.shader;

import dev.shadock.shadock.Shadock;
import dev.shadock.shadock.iris.IrisCompat;
import dev.shadock.shadock.iris.IrisPackInfo;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caches the pack list. Scans run on a background thread; the dock reads the latest snapshot.
 * A rescan happens only on first open, after an import, on request, or when the folder changes.
 */
public final class ShaderPackScanner {
	public enum State { NOT_SCANNED, SCANNING, READY, FAILED }

	private final IrisCompat iris;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Shadock scanner");
		t.setDaemon(true);
		return t;
	});
	private final AtomicBoolean dirty = new AtomicBoolean(true);
	private final AtomicBoolean scanning = new AtomicBoolean(false);
	private final AtomicBoolean rescanQueued = new AtomicBoolean(false);

	private volatile List<ShaderPack> packs = List.of();
	private volatile State state = State.NOT_SCANNED;
	/** Increments each time a scan finishes, so the UI knows when to rebuild its rows. */
	private volatile int generation;
	private boolean watcherStarted;
	/** True while the folder watcher thread is alive and registered. */
	private volatile boolean watching;

	public ShaderPackScanner(IrisCompat iris) {
		this.iris = iris;
	}

	public List<ShaderPack> packs() {
		return packs;
	}

	public State state() {
		return state;
	}

	public int generation() {
		return generation;
	}

	public boolean isDirty() {
		return dirty.get();
	}

	/**
	 * Called when the dock opens. With a working folder watcher this only scans if something changed;
	 * without one (unsupported file system, or the folder was deleted) it rescans so the list is never stale.
	 */
	public void onDockOpened() {
		if (!watching) {
			dirty.set(true);
		}
		refreshIfDirty();
	}

	/** Scans if the cache is stale. Cheap to call every tick. */
	public void refreshIfDirty() {
		if (dirty.get()) {
			requestScan();
		}
	}

	/** Forces a rescan (after an import or a manual refresh). */
	public void requestScan() {
		startWatcher();
		dirty.set(false);
		if (!scanning.compareAndSet(false, true)) {
			rescanQueued.set(true);
			return;
		}
		if (state == State.NOT_SCANNED) {
			state = State.SCANNING;
		}
		executor.execute(this::scan);
	}

	private void scan() {
		try {
			do {
				rescanQueued.set(false);
				List<ShaderPack> result = new ArrayList<>();
				for (IrisPackInfo info : iris.getShaderPacks()) {
					result.add(ShaderPack.of(info.path(),
						info.zip() ? ShaderPack.Type.ZIP : ShaderPack.Type.FOLDER,
						info.valid() ? ShaderPack.Validity.VALID : ShaderPack.Validity.INVALID));
				}
				packs = List.copyOf(result);
				state = State.READY;
				generation++;
			} while (rescanQueued.get());
		} catch (RuntimeException e) {
			Shadock.LOGGER.error("Shader pack scan failed", e);
			state = State.FAILED;
			generation++;
		} finally {
			scanning.set(false);
		}
		if (rescanQueued.getAndSet(false)) {
			requestScan();
		}
	}

	/** Watches the folder and marks the cache dirty on changes. Falls back silently if unsupported. */
	private synchronized void startWatcher() {
		if (watcherStarted) {
			return;
		}
		watcherStarted = true;
		Path dir = iris.getShaderpacksDirectory();
		try {
			WatchService watch = FileSystems.getDefault().newWatchService();
			dir.register(watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY);
			watching = true;
			Thread thread = new Thread(() -> watchLoop(watch), "Shadock folder watcher");
			thread.setDaemon(true);
			thread.start();
		} catch (IOException | UnsupportedOperationException e) {
			Shadock.LOGGER.info("Folder watching unavailable for {}; the list refreshes each time the dock opens", dir);
		}
	}

	private void watchLoop(WatchService watch) {
		try {
			while (true) {
				WatchKey key = watch.take();
				boolean relevant = false;
				for (WatchEvent<?> event : key.pollEvents()) {
					if (event.kind() == StandardWatchEventKinds.OVERFLOW || !(event.context() instanceof Path name) || isRelevant(name.toString())) {
						relevant = true;
					}
				}
				if (relevant) {
					dirty.set(true);
				}
				if (!key.reset()) {
					// The folder itself was removed or became inaccessible.
					break;
				}
			}
		} catch (InterruptedException | ClosedWatchServiceException e) {
			Thread.currentThread().interrupt();
		} finally {
			try {
				watch.close();
			} catch (IOException ignored) {
				// Nothing else to release.
			}
			synchronized (this) {
				watching = false;
				watcherStarted = false;
			}
			dirty.set(true);
		}
	}

	/**
	 * Iris writes {@code <pack>.txt} option files on every load, and imports use temp files.
	 * Neither changes the pack list, so they must not trigger a rescan of every ZIP.
	 */
	static boolean isRelevant(String fileName) {
		String lower = fileName.toLowerCase(java.util.Locale.ROOT);
		return !lower.endsWith(".txt") && !lower.endsWith(".shadock-tmp") && !lower.endsWith(".tmp");
	}
}
