package dev.shadock.shadock.mixin;

import dev.shadock.shadock.iris.IrisErrorCapture;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Iris only logs most load failures. handleException is the one place the actual exception passes
// through, so this is the only way to show "Iris error: ..." instead of a generic message.
// require = 0 so a renamed method on some future Iris costs us the detail, not the whole mod.
@Mixin(value = Iris.class, remap = false)
public abstract class IrisErrorCaptureMixin {
	@Inject(method = "handleException", at = @At("HEAD"), require = 0)
	private static void shadock$captureError(Exception e, CallbackInfo ci) {
		IrisErrorCapture.record(e);
	}
}
