package com.texturestudio;

import java.nio.file.Path;

/** Ein Texturepack im resourcepacks-Ordner (ZIP oder entpackter Ordner). */
public record PackInfo(String id, Path path, boolean zip) {

	public String displayName() {
		String n = id;
		if (zip && n.toLowerCase().endsWith(".zip")) {
			n = n.substring(0, n.length() - 4);
		}
		// Farbcodes (§x), fuehrende "!"-Sortierzeichen und doppelte Leerzeichen entfernen
		String clean = n.replaceAll("§.", "").replaceFirst("^[!\\s]+", "").replaceAll("\\s+", " ").trim();
		return clean.isEmpty() ? n : clean;
	}

	/** Kurzer Name fuer enge Buttons. */
	public String shortName(int max) {
		String n = displayName();
		return n.length() <= max ? n : n.substring(0, Math.max(1, max - 1)) + "…";
	}
}
