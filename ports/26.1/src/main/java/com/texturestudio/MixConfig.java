package com.texturestudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;

/** Gespeicherte Auswahl: Sammlungs-ID -> Pack-ID (der aktive Stil), dazu Favoriten und Farbthema. */
public final class MixConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public Map<String, String> styles = new TreeMap<>();
	/** Eintraege der Form "sammlung|pack" */
	public Set<String> favorites = new TreeSet<>();
	/** Preset-ID (glass, halloween, ...) oder "custom" fuer die frei gewaehlte Farbe {@link #customColor}. */
	public String accent = "amethyst";
	public int customColor = 0xFF9B5CFF;
	/** Deckkraft des Fensters in Prozent (40..100). */
	public int opacity = 95;
	/** Alt (vor den Menue-Themen): true = hell, false = dunkel. Wird nur noch zum Umstellen gelesen. */
	public boolean lightMode = false;
	/** Farbthema des ganzen Menues (weiss, grau, rosa, mint, dunkel, navy, lila = Nebula, wald, glut). */
	public String menuTheme = "lila";

	/** Aktuelles Menue-Thema; aeltere Konfigurationen werden aus lightMode uebernommen. */
	public String menuTheme() {
		return menuTheme != null ? menuTheme : lightMode ? "weiss" : "dunkel";
	}
	/** Schrift des Menues (siehe MenuFonts). */
	public String font = "nunito";
	/** Getragene Cosmetics (Umhang, Hut, Fluegel); "none" = nichts. */
	public String cape = "none";
	public String hat = "none";
	public String wings = "none";
	/** Kill effect (see KillEffects) and Spotify lyrics style (see Lyrics); "none" = off. */
	public String killEffect = "none";
	public String lyrics = "none";
	/** Lyrics are drawn over blocks (true) or hidden where blocks cover the sky (false). */
	public boolean lyricsThroughBlocks = true;
	/** Tweaks (see Tweaks). */
	public boolean noTotemPop;
	public boolean noTotemParticles;
	public boolean lowFire;
	public boolean lowShield;
	public boolean noExplosionParticles;
	public boolean fewerHitParticles;
	public boolean calmCrystals;
	public boolean hideFarItems;
	/** Armor HUD style (see ArmorHud); "none" = off. */
	public String armorHud = "none";
	/** Spotify overlay style (see SpotifyHud); "none" = off. */
	public String spotify = "none";
	/** Spotify overlay in the top right instead of the top left. */
	public boolean spotifyRight = false;
	/** Hit flash colour (see HitColor); "none" = vanilla red. */
	public String hitColor = "none";
	/** Totem pop ghost colour (see PopChams); "none" = off. */
	public String popChams = "none";

	/** Wurde der Spieler schon gefragt, ob er die mitgelieferten Packs installieren will? */
	public boolean packOfferDone;
	/** Version der Oberflaeche; aeltere Konfigurationen bekommen das neue Standardthema. */
	public int uiVersion = 8;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("texturestudio.json");
	}

	public static MixConfig load() {
		Path f = file();
		if (Files.isRegularFile(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				MixConfig c = GSON.fromJson(r, MixConfig.class);
				if (c != null) {
					if (c.styles == null) {
						c.styles = new TreeMap<>();
					}
					if (c.favorites == null) {
						c.favorites = new TreeSet<>();
					}
					if (c.accent == null || c.uiVersion < 6) {
						// aeltere Konfigurationen bekommen einmalig das neue dunkle Nebula-Standardthema
						c.accent = "amethyst";
						c.customColor = 0xFF9B5CFF;
						c.opacity = 95;
						c.lightMode = false;
						c.menuTheme = "lila";
						c.uiVersion = 6;
					}
					if (c.uiVersion < 7) {
						// once: the new smooth menu font (Nunito)
						c.font = "nunito";
						c.uiVersion = 7;
					}
					if (c.uiVersion < 8) {
						// once: the colour theme now tints the whole (dark) menu
						c.menuTheme = "lila";
						if ("mono".equals(c.accent)) {
							c.accent = "aurora";
						}
						c.uiVersion = 8;
					}
					if (c.opacity < 40 || c.opacity > 100) {
						c.opacity = 95;
					}
					return c;
				}
			} catch (IOException | RuntimeException ignored) {
				// kaputte Datei -> leer starten
			}
		}
		return new MixConfig();
	}

	public void save() {
		try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
			GSON.toJson(this, w);
		} catch (IOException ignored) {
			// Speichern ist best effort
		}
	}
}
