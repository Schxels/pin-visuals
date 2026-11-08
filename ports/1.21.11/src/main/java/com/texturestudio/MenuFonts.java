package com.texturestudio;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Waehlbare Schriften fuer das Menue. Jede Schrift ist ein normaler {@link Font}, dessen Standard-Schrift auf die
 * gewaehlte umgeleitet wird; so funktionieren alle Breitenberechnungen und Textfelder ohne Sonderfaelle.
 * Die drei Pixel-Schriften stehen unter der SIL Open Font License (see META-INF/third-party/fonts).
 */
public final class MenuFonts {

	public record Entry(String id, String name, FontDescription font) {
	}

	public static final List<Entry> ALL = List.of(
			new Entry("minecraft", "Minecraft", FontDescription.DEFAULT),
			new Entry("uniform", "Uniform", res("minecraft", "uniform")),
			new Entry("silkscreen", "Silkscreen", res(TextureStudioClient.MOD_ID, "silkscreen")),
			new Entry("vt323", "VT323", res(TextureStudioClient.MOD_ID, "vt323")),
			new Entry("pressstart", "Press Start", res(TextureStudioClient.MOD_ID, "pressstart")),
			new Entry("nunito", "Nunito", res(TextureStudioClient.MOD_ID, "nunito")));

	/** Smooth (non-pixel) fonts: already bold, so the menu does not draw them twice for bold text. */
	public static boolean smooth(String id) {
		return "nunito".equals(id);
	}

	private static final Map<String, Font> CACHE = new HashMap<>();
	private static Font cachedBase;

	private MenuFonts() {
	}

	private static FontDescription res(String ns, String path) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath(ns, path));
	}

	public static Entry byId(String id) {
		for (Entry e : ALL) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return ALL.get(0);
	}

	/** Font, der Standard-Text in der gewaehlten Schrift zeichnet (faellt bei Fehlern auf die normale zurueck). */
	public static Font get(String id) {
		Font base = Minecraft.getInstance().font;
		if (base != cachedBase) {
			CACHE.clear(); // Ressourcen neu geladen
			cachedBase = base;
		}
		Entry e = byId(id);
		if (e.font() == FontDescription.DEFAULT) {
			return base;
		}
		return CACHE.computeIfAbsent(e.id(), k -> wrap(base, e.font()));
	}

	private static Font wrap(Font base, FontDescription target) {
		try {
			Field f = Font.class.getDeclaredField("provider");
			f.setAccessible(true);
			Font.Provider inner = (Font.Provider) f.get(base);
			return new Font(new Font.Provider() {
				@Override
				public GlyphSource glyphs(FontDescription d) {
					return inner.glyphs(FontDescription.DEFAULT.equals(d) ? target : d);
				}

				@Override
				public EffectGlyph effect() {
					return inner.effect();
				}
			});
		} catch (ReflectiveOperationException | RuntimeException ex) {
			TextureStudioClient.LOGGER.warn("Schrift konnte nicht geladen werden", ex);
			return base;
		}
	}
}
