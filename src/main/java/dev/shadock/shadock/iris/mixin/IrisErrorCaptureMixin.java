package dev.shadock.shadock.iris.mixin;

import dev.shadock.shadock.iris.IrisErrorCapture;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the exception Iris reports when a pack fails to load or compile, so the dock can show it.
 * {@code require = 0}: if a future Iris renames the method, Shadock still loads (it just shows a generic error).
 */
@Mixin(value = Iris.class, remap = false)
public abstract class IrisErrorCaptureMixin {
	@Inject(method = "handleException", at = @At("HEAD"), require = 0)
	private static void shadock$captureError(Exception e, CallbackInfo ci) {
		IrisErrorCapture.record(e);
	}
}
