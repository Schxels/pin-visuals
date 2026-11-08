# Pin Visuals (Fabric, Minecraft 26.2, clientseitig)

Build: JDK 25, dann `.\gradlew.bat build`. Die fertige Mod liegt in `build/libs/pin-visuals-1.0.0.jar`
(die Datei ohne `-sources`). Zum Testen: `.\gradlew.bat runClient`.

Benutzung: Taste O (aenderbar unter Steuerung) oeffnet Pin Visuals.
Packs werden aus dem resourcepacks-Ordner gelesen (ZIP oder entpackter Ordner).
Links eine Sammlung waehlen, in der Mitte auf einen Stil klicken: er wird sofort angewendet.
Daraus entsteht das Pack "PinVisuals-Mix", das automatisch ganz oben aktiviert wird.

## Mitgelieferte Pack-Sammlung

Alle ZIPs in `packs-library/` werden beim Bauen unter `/packlib` in die JAR gepackt.
Beim ersten Oeffnen fragt die Mod, ob sie in den resourcepacks-Ordner des Spielers kopiert werden sollen
("Alle installieren", "Auswaehlen" oder "Nein danke"). Spaeter geht das jederzeit ueber "PACKS LADEN" unten.
Gleichnamige Dateien im Ordner des Spielers werden nie ueberschrieben.

`packs-library/` enthaelt *verkleinerte Stil-Packs* (damit die JAR unter 5 MB bleibt): nur die Texturen
der Sammlungen (Crystal, Totem, Tools ...), Texturen hoechstens 128 px breit, Icon 32x32.
Die vollen Originale liegen in `packs-original/` (werden nicht eingebaut).

Packs aendern: Originale in `packs-original/` legen/loeschen, dann neu erzeugen und bauen:

    .\gradlew.bat compileJava
    Remove-Item packs-library -Recurse
    java -cp build/classes/java/main tools/PackSlimmer.java packs-original packs-library 128
    .\gradlew.bat build

Alte Texturpfade (environment/sun.png, moon_phases.png, entity/shield.png, models/armor/...) werden beim
Anwenden automatisch zusaetzlich unter den Pfaden von Minecraft 26.2 abgelegt.
Achtung: Die Packs stammen von fremden Autoren. Die JAR mit diesen Packs nur weitergeben,
wenn die Autoren das erlauben.

## Design

Default is the dark purple "Nebula" theme (configs older than uiVersion 6 are switched to it once).
Layout: sidebar on the left (Sets, Cosmetics, Extras, Loadout, Favorites | Themes, Tweaks, Settings | Get Packs;
icon-only with tooltips in small windows), header (title, search, pack folder, close), the collection rail (item
icons, scrolls with arrows / mouse wheel), the preview panel (stage with spotlight and turning rings, name, accent
swatches, opacity, Apply + back-to-vanilla), the card grid with All / Favorites and a dot pager, and the loadout
dock at the bottom (everything equipped; click to jump there). Tweaks = the options dialog (totem, low fire,
low shield), Settings = reset all, pack folder, reset look. Themes replaces the preview panel.
Look: the window background is a nebula texture painted once from the theme colours (`nebulaTexture`), soft
lights and shadows are one radial texture drawn tinted (`softGlow`), panels are translucent "glass"; titles use
the bundled Silkscreen font. Light themes skip the nebula.

Performance: Minecraft's GUI searches backwards through all earlier elements for every element it gets (layering),
so thousands of small rectangles made the menu run at ~5 FPS. All plain fills therefore go through `FillBatch`
(one GUI element per run of fills, flushed before any text/image/item; needs the accessor mixin
`GuiGraphicsAccess`). Gradients are drawn in bands, wing previews are baked into small textures, and the hit
buttons are input-only (`addWidget`). Rule for new drawing code: use `FillBatch.fill(g, ...)` and
`FillBatch.flush(g).blit/text(...)`, never `g.fill` directly.
1.21.11: `GuiGraphics`, `submitGuiElement`, state classes in `net.minecraft.client.gui.render.state`.
26.3: `RenderPipeline` is `com.mojang.renderpearl.api.pipeline.RenderPipeline`.

## Crystal filter

`PackIndex.goodCrystal`: a pack only appears under Crystal if it changes `entity/end_crystal/end_crystal.png`
itself (item-only "3D crystal" packs would be previewed with some other texture), at least 15% of the vanilla
crystal shape is opaque (no invisible crystals; thin outline crystals are allowed) and at most 30% lies outside
the vanilla layout.
PackSlimmer keeps `textures/entity/` files in full size (shrinking erased 1px crystal outlines).

## Low fire / Low shield (OPTIONS)

`ScreenEffectRendererMixin` moves the fire overlay down (`Tweaks.LOW_FIRE_OFFSET`), `ItemInHandRendererMixin` the
first-person shield (`LOW_SHIELD_OFFSET`). Targets: 26.2/26.3 `submitFire`, 26.1/1.21.11 `renderFire`;
26.2 `ItemInHandRenderer.submitArmWithItem`, 26.1/1.21.11 `renderArmWithItem`,
26.3 `FirstPersonHandsAndItemsRenderer.submitArmWithItem`.

## Kill effects and Spotify lyrics (Extras)

Both are `Cosmetics.Kind` values (KILL, LYRICS) shown as the groups `fx_kill` / `fx_lyrics` under Extras.
- `KillEffects`: a kill = another player dies within 10 s of your hit (`AttackEntityCallback`), or within 2.5 s and
  12 blocks of an end crystal you hit / a respawn anchor you used. Only players count. Effects are client-only
  particles and sounds (Lightning uses a client-only visual bolt with a negative entity id). Picking an effect in
  the menu plays it 4 blocks in front of you. 26.2/26.3 use `EntityTypes.LIGHTNING_BOLT`, 26.1/1.21.11
  `EntityType`; 26.3 `createFireworks` has an extra boolean (sound).
- `Lyrics`: Windows only. A hidden PowerShell helper (`config/texturestudio/spotify-now-playing.ps1`, ends with the
  game) reads the Spotify session of the Windows media controls (title, artist, position); without the desktop
  app it takes a playing browser session instead (Spotify web player / installed web app in Chrome, Edge,
  Firefox, Brave, Opera, Vivaldi). Synced lyrics come
  from LRCLIB (lrclib.net) at runtime; no lyrics are stored in the mod or on disk. `LyricsHud` (Fabric
  `HudElementRegistry`) draws them about 22 degrees above the horizon with a karaoke fill.

## Colour themes (accent)

Each accent theme is a three-colour gradient (`Theme.color -> c2 -> c3`, `acc2()` / `acc3()`, drawn with `hgrad3`):
Ocean, Glass, Sunset, Crimson, Nebula, Citrus, Sakura, Aurora (white "Mono" was removed). Glass is `glassy`: the
window is see-through (`opacity()` max 55 %), no nebula, pale frosted panels with a bright rim.
Since uiVersion 8 the colour theme tints the whole (dark) menu: `applyPalette` uses `mix(accent, acc3)`, the
nebula is painted from the three theme colours, `ensurePalette()` rebuilds when the theme changes. Themes ->
MENU THEME shows the gradient themes as tiles (`paintGradientTile`), CUSTOM COLOR keeps the hue slider. The old
light/dark menu themes are no longer offered (configs are switched to the dark one once).
Lyrics option "Visible through blocks" (`MixConfig.lyricsThroughBlocks`, switch in the lyrics preview panel): when
off, `LyricsHud.skyMask` casts 20 rays (96 blocks) through columns of the text and draws only the free parts
(scissor).
Lyrics (`LyricsHud`): about 34 degrees above the horizon, Nunito, see-through with a faint halo (no backdrop), and
the lines follow the view direction only slowly so they drift a little when turning.

## Armor HUD (Extras) and FPS tweaks (Tweaks -> FPS)

- `ArmorHud` (Kind ARMOR, group `fx_armor`): worn armor stacked left of the hotbar; styles Bars / Glass (percent +
  bar) and Compact (remaining points). Green > 60 %, yellow > 30 %, red below, blinking under 15 %.
- FPS tab of the Tweaks dialog (NoRisk etc. already ship Sodium, Lithium, culling mods, so these only cover what
  they do not): no explosion particles and fewer hit particles (`ParticleEngineMixin`, `createParticle` +
  `createTrackingEmitter`), calm end crystals (`EndCrystalRendererMixin`, age 0), hide dropped items over 16 blocks
  (`EntityRendererMixin.shouldRender`; 26.3 has an extra float argument). "FPS Boost" turns all of them plus
  "no totem particles" on or off.

## Spotify overlay and hit colour (Extras)

- `SpotifyHud` (Kind SPOTIFY, group `fx_spotify`): "now playing" card in the top left (switch in the preview panel:
  top right, `MixConfig.spotifyRight`). Styles Glass Card / Dark Card (cover, song, artist, time, progress bar,
  equalizer bars, pause icon) and Minimal (one line + thin bar). Uses the same Windows media helper as the lyrics;
  the helper now also runs when only the overlay is on, and lyrics are only fetched when lyrics are on.
- Album cover: the helper writes a 64x64 PNG once per song to `%TEMP%\pinvisuals-covers\cover-0/1.png` and prints
  `COVER\t<title|artist>\t<file>`. Windows PowerShell 5.1 cannot cast the WinRT thumbnail stream, so
  `AsStreamForRead` is called via reflection. The texture is `texturestudio:spotify/cover`.
- `HitColor` (Kind HIT, group `fx_hit`): repaints rows 0..7 (the red "hurt" rows) of the 16x16 entity colour
  overlay texture (`OverlayTextureMixin` grabs it after the constructor). Affects every mob and player hit flash.
  Rainbow re-uploads the texture every tick.
- `PopChams` (Kind POP, group `fx_pop`): `LivingEntityPopMixin` catches entity event 35 (totem used) for other
  players and spawns a client-only `RemotePlayer` copy (negative id, random UUID, otherwise the level refuses it,
  invisible) in the same pose. `CosmeticLayer` draws that copy as an emissive translucent model in the picked colour;
  it rises 0.9 blocks, grows a little and fades out over 1.3 s. Clicking a style spawns a preview ghost of yourself
  3 blocks in front. 26.3's `submitModel` has no crumbling argument. The "Ghost time" slider (0.5 .. 5 s,
  `MixConfig.popSeconds`) sets how long a ghost stays; it stays clear and fades out at the end.
- `Atmosphere` (Kind ATMO, group `fx_atmo`): coloured mood around you with sliders Range (8 .. 96 blocks) and
  Strength (10 .. 100 %). `FogRendererMixin` pulls Minecraft's fog in to the range and tints it (also sky and clouds,
  not under water / lava); 26.x changes the returned `FogData`, 1.21.11 changes the arguments of `updateBuffer`.
  Around the player (never closer than 5.5 blocks, so puffs do not cover the screen) it spawns coloured dust mist,
  tinted swirls, smoke and one style particle (snowflakes, cherry petals, flames, fireflies ...), plus a faint
  coloured vignette on the HUD. Styles: Mystic Haze, Deep Ocean, Ember Storm, Frost, Sakura, Toxic Swamp,
  Firefly Night, Void.

## Pig packs

PigPurp, PigBlack, PigWeen, PigWhite, PigError, PigPack (supplied by the user), originals in `packs-original/`.

## XP Bottle

Collection `xpbottle` (`item/experience_bottle`). The bundled packs got the XP bottle texture added from the
14 originals that have one (the slimmer only kept the collections that existed back then).

### Nunito (default menu font since uiVersion 7)

Nunito Bold (SIL OFL 1.1, `font/nunito.ttf`, static 800 weight from Google Fonts, converted from WOFF to TTF).
It is a smooth font: titles use it too (instead of Silkscreen), and the menu's fake bold (text drawn twice, 1 px
apart) is skipped for it (`MenuFonts.smooth`). Configs older than uiVersion 7 are switched to it once.

### Schriften

THEMES -> SCHRIFT blaettert durch: Minecraft, Uniform (beide aus dem Spiel), Silkscreen, VT323 und
Press Start. Die drei letzten liegen als TTF unter `assets/texturestudio/font/` und stehen unter der
SIL Open Font License 1.1 (Lizenztexte in `font/licenses/`), duerfen also mit der Mod weitergegeben werden.

## Cosmetics

In der Liste links unter Capes, Hüte und Flügel. Sie sind rein clientseitig: nur der Spieler selbst
sieht sie (in der Welt in der dritten Person und in der drehenden Vorschau rechts), andere Spieler nicht.

- Umhaenge: Texturen `textures/cosmetics/cape_*.png` (128x64), erzeugt mit
  `java tools/CosmeticArt.java src/main/resources/assets/texturestudio/textures/cosmetics`
- Huete und Fluegel: kleine Pixel-Modelle direkt im Code (`Cosmetics.java`), gezeichnet von `CosmeticLayer`.
  Ein eigener Umhang blendet den Vanilla-/Mojang-Umhang aus; mit Elytra werden Umhang und Fluegel nicht gezeichnet.

Feather wings (Angel, Seraph, Raven, Phoenix) are HD (0.5 px per pixel) with a back feather layer and
a covert layer on both sides for depth.

## Options (footer button OPTIONS)

- No totem pop animation: `GameRendererMixin` cancels `displayItemActivation` for the totem.
- No totem particles: `ParticleEngineMixin` cancels the totem particle emitter.

## Skies

The Sky collection only shows custom skybox packs (OptiFine/MCPatcher sky folders, legacy FabricSkyBoxes,
`assets/imgs`), see `Group.isSkyData`. Sun/moon textures of normal packs are no longer offered.
The library contains exactly five skies (supplied by the user): Better Sky, Cloudy Nights, Ethereal Sea,
Midnight Gotham, Plum Twilight. They are built with
`java tools/SkyPacker.java <extracted packs dir> packs-library 768 nodither`
(768 px wide sheets, 256 colors, MCPatcher copies dropped, FabricSkyBoxes faces pointed at the shared sheet).
To draw them, Pin Visuals bundles **Skyboxify 3.4** and its required library **YACL** (unchanged official jars,
SHA1-checked against Modrinth) via Fabric jar-in-jar: `bundled/` + `include` in build.gradle (each port has the
jars for its Minecraft version). Licenses and source links: `META-INF/third-party/` (Skyboxify GPL-3.0,
YACL LGPL-3.0). If a player already has a newer Skyboxify/YACL installed, Fabric uses the newer one.

## Other Minecraft versions

This folder builds 26.2. Copies for other versions live in `../ports/<version>` (own gradle.properties,
same features); finished jars are collected in `../releases/pin-visuals-2.0.0-<version>.jar`.
Differences per port (apply again when changing shared code):
- 26.1: `mc.screen` / `mc.setScreen` instead of `mc.gui.screen()` / `mc.gui.setScreen`, `Items.BLUE_BANNER`.
- 26.3: `submitModel` without the crumbling argument, `PoseStack.rotateDegrees`, `fullResources().getResource`,
  `KeyMapping(name, key, category)`, totem mixin targets `LocalPlayer.displayItemActivation`.
- 1.21.11: remapping Loom (`fabric-loom-remap`, Mojang mappings, Java 21), `GuiGraphics` + `render(...)`,
  `drawString`/`renderFakeItem`/`submitEntityRenderState`, `KeyBindingHelper`,
  `LivingEntityFeatureRendererRegistrationCallback`, render types `entityCutout` (cull) / `entityCutoutNoCull`.

## Language

The whole menu is English (strings in the code, no translation files besides `en_us.json`).

## Extra packs from Modrinth

10 packs with open licenses were added; sources and licenses: `packs-original/CREDITS-modrinth.txt`
(also shipped in the jar as `packlib/CREDITS-modrinth.txt`).

## Sammlungen Trident und Wind Charge

Trident: `item/trident` und die Modell-Textur (alter Pfad `entity/trident.png` wird beim Mix nach
`entity/trident/trident.png` kopiert). Wind Charge: Item, Geschoss und die Gust-Partikel.
