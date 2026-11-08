package dev.shadock.shadock.ui;

import net.minecraft.network.chat.Component;

import java.util.List;

public record DockPrompt(Component message, List<Choice> choices, Runnable onEscape) {
	public record Choice(Component label, boolean primary, Runnable action) {
	}
}
