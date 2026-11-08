package dev.shadock.shadock.config;

import dev.shadock.shadock.shader.ShaderPackSortMode;

import java.util.ArrayList;
import java.util.List;

/** Plain settings object, serialized as JSON by {@link ShadockConfigManager}. */
public final class ShadockConfig {
	/** Auto-hide choices shown in settings. 0 means the dock never auto-hides. */
	public static final int[] AUTO_HIDE_CHOICES = {0, 3, 5, 10};

	public ShaderPackSortMode sortMode = ShaderPackSortMode.CUSTOM_ORDER;
	public int autoHideDelaySeconds = 5;
	public boolean dockAnimation = true;
	public DuplicateImportBehavior duplicateImportBehavior = DuplicateImportBehavior.ASK;
	public DockPosition dockPosition = DockPosition.BOTTOM_LEFT;
	/** Exact shader pack file/folder names, in the player's custom order. */
	public List<String> customPackOrder = new ArrayList<>();

	/** Replaces missing or out-of-range values with defaults. Returns true if anything changed. */
	public boolean sanitize() {
		boolean changed = false;
		if (sortMode == null) {
			sortMode = ShaderPackSortMode.CUSTOM_ORDER;
			changed = true;
		}
		if (duplicateImportBehavior == null) {
			duplicateImportBehavior = DuplicateImportBehavior.ASK;
			changed = true;
		}
		if (dockPosition == null) {
			dockPosition = DockPosition.BOTTOM_LEFT;
			changed = true;
		}
		if (!isValidAutoHide(autoHideDelaySeconds)) {
			autoHideDelaySeconds = 5;
			changed = true;
		}
		if (customPackOrder == null) {
			customPackOrder = new ArrayList<>();
			changed = true;
		} else {
			List<String> cleaned = new ArrayList<>();
			for (String name : customPackOrder) {
				if (name != null && !name.isBlank() && !cleaned.contains(name)) {
					cleaned.add(name);
				}
			}
			if (cleaned.size() != customPackOrder.size()) {
				customPackOrder = cleaned;
				changed = true;
			} else if (!(customPackOrder instanceof ArrayList)) {
				customPackOrder = cleaned;
			}
		}
		return changed;
	}

	private static boolean isValidAutoHide(int seconds) {
		for (int choice : AUTO_HIDE_CHOICES) {
			if (choice == seconds) {
				return true;
			}
		}
		return false;
	}
}
