# Pin Visuals

Client-side Fabric mod for Minecraft PvP visuals: mix texture-pack styles per collection (crystals, totems, tools, sky ...),
cosmetics (capes, hats, wings) and extras like kill effects, Spotify lyrics and overlay, Armor HUD, hit colour and pop chams.

| Folder | Minecraft |
| --- | --- |
| `texture-studio/` | 26.2 (main project, details in its README) |
| `ports/1.21.11/` | 1.21.11 |
| `ports/26.1/` | 26.1 |
| `ports/26.3/` | 26.3 |

## Building

JDK 25 and Gradle 9, then in a project folder:

    gradle build

The mod jar ends up in `build/libs/`.

## Not included in this repository

- **Texture packs** (`packs-library/`, `packs-original/`): they are made by other authors and are not redistributed
  here. Put your own pack ZIPs into `packs-library/` before building if you want them bundled.
- **Bundled mods** (`bundled/`): the build expects the unchanged jars of
  [Skyboxify](https://modrinth.com/mod/skyboxify) and [YetAnotherConfigLib](https://modrinth.com/mod/yacl) for the
  matching Minecraft version in `bundled/` (see `build.gradle` for the exact file names).
- Release jars.

Fonts in `src/main/resources/assets/texturestudio/font/` are under the SIL Open Font License (licences in
`META-INF/third-party/fonts/`).
