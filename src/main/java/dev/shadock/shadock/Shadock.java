package dev.shadock.shadock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants. Shadock is client-only, so there is no common entrypoint. */
public final class Shadock {
	public static final String MOD_ID = "shadock";
	public static final Logger LOGGER = LoggerFactory.getLogger("Shadock");

	private Shadock() {
	}
}
