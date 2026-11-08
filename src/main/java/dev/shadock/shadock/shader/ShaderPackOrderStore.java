package dev.shadock.shadock.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the scanned pack list into the displayed order.
 * Pure logic with no Minecraft or file-system access, so it is unit-tested.
 */
public final class ShaderPackOrderStore {
	public static final Comparator<ShaderPack> ALPHABETICAL =
		Comparator.comparing((ShaderPack p) -> p.displayName().toLowerCase(Locale.ROOT)).thenComparing(ShaderPack::fileName);

	private ShaderPackOrderStore() {
	}

	/**
	 * Custom order: packs not in the saved order (new or freshly imported) come first, A–Z,
	 * followed by the saved order. Saved names with no matching pack are skipped.
	 */
	public static List<ShaderPack> sort(List<ShaderPack> packs, List<String> savedOrder, ShaderPackSortMode mode) {
		List<ShaderPack> result = new ArrayList<>(packs);
		if (mode == ShaderPackSortMode.ALPHABETICAL) {
			result.sort(ALPHABETICAL);
			return result;
		}

		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < savedOrder.size(); i++) {
			index.putIfAbsent(savedOrder.get(i), i);
		}

		List<ShaderPack> unknown = new ArrayList<>();
		List<ShaderPack> known = new ArrayList<>();
		for (ShaderPack pack : result) {
			(index.containsKey(pack.fileName()) ? known : unknown).add(pack);
		}
		unknown.sort(ALPHABETICAL);
		known.sort(Comparator.comparingInt(p -> index.get(p.fileName())));

		result.clear();
		result.addAll(unknown);
		result.addAll(known);
		return result;
	}

	/** Saved order with entries for packs that no longer exist removed. */
	public static List<String> prune(List<String> savedOrder, List<ShaderPack> packs) {
		List<String> existing = new ArrayList<>();
		for (ShaderPack pack : packs) {
			existing.add(pack.fileName());
		}
		List<String> pruned = new ArrayList<>(savedOrder);
		pruned.retainAll(existing);
		return pruned;
	}

	/** Moves one entry and returns the new order as file names. */
	public static List<String> move(List<ShaderPack> displayed, int from, int to) {
		List<ShaderPack> copy = new ArrayList<>(displayed);
		if (from < 0 || from >= copy.size()) {
			return names(copy);
		}
		ShaderPack moved = copy.remove(from);
		copy.add(Math.max(0, Math.min(to, copy.size())), moved);
		return names(copy);
	}

	public static List<String> names(List<ShaderPack> packs) {
		List<String> names = new ArrayList<>(packs.size());
		for (ShaderPack pack : packs) {
			names.add(pack.fileName());
		}
		return names;
	}
}
