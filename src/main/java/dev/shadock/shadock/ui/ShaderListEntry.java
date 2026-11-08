package dev.shadock.shadock.ui;

import dev.shadock.shadock.shader.ShaderPack;
import dev.shadock.shadock.util.TextUtils;
import net.minecraft.client.gui.Font;

final class ShaderListEntry {
	final ShaderPack pack;
	float animY;

	private String cachedName = "";
	private int cachedWidth = -1;

	ShaderListEntry(ShaderPack pack, float animY) {
		this.pack = pack;
		this.animY = animY;
	}

	String name(Font font, int width) {
		if (width != cachedWidth) {
			cachedWidth = width;
			cachedName = TextUtils.ellipsize(font, pack.displayName(), width);
		}
		return cachedName;
	}

	boolean isTruncated() {
		return !cachedName.equals(pack.displayName());
	}
}
