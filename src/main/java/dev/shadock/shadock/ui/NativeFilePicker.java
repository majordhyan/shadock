package dev.shadock.shadock.ui;

import dev.shadock.shadock.Shadock;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

// SDL3 dialog instead of TinyFD: 26.3 runs its window on SDL, the dialog is async so the game keeps
// rendering, and SDL deals with macOS wanting dialogs on the main thread.
public final class NativeFilePicker {
	private NativeFilePicker() {
	}

	// both callbacks land on the render thread
	public static void openZip(Consumer<@Nullable Path> onResult, Consumer<String> onError) {
		Minecraft minecraft = Minecraft.getInstance();

		// SDL reads the filter strings when the dialog opens and may keep them until the callback,
		// so they are allocated off-heap and freed only in the callback.
		ByteBuffer filterName = MemoryUtil.memUTF8("Shader packs (*.zip)");
		ByteBuffer filterPattern = MemoryUtil.memUTF8("zip");
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.get(0).set(filterName, filterPattern);

		AtomicReference<SDL_DialogFileCallback> self = new AtomicReference<>();
		SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
			String chosen = null;
			String error = null;
			if (fileList == 0L) {
				error = SDLError.SDL_GetError();
			} else {
				long first = MemoryUtil.memGetAddress(fileList);
				if (first != 0L) {
					chosen = MemoryUtil.memUTF8(first);
				}
			}

			MemoryUtil.memFree(filterName);
			MemoryUtil.memFree(filterPattern);
			filters.free();
			SDL_DialogFileCallback cb = self.get();
			if (cb != null) {
				cb.free();
			}

			String finalChosen = chosen;
			String finalError = error;
			minecraft.execute(() -> {
				if (finalError != null) {
					Shadock.LOGGER.error("File picker failed: {}", finalError);
					onError.accept(finalError);
				} else {
					onResult.accept(finalChosen == null ? null : Path.of(finalChosen));
				}
			});
		});
		self.set(callback);

		try {
			// TODO: remember the last folder a pack was imported from instead of always starting at the OS default
			SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, minecraft.getWindow().handle(), filters, (CharSequence) null, false);
		} catch (Throwable t) {
			Shadock.LOGGER.error("Could not open the file picker", t);
			MemoryUtil.memFree(filterName);
			MemoryUtil.memFree(filterPattern);
			filters.free();
			callback.free();
			onError.accept(String.valueOf(t.getMessage()));
		}
	}
}
