# Pin Visuals – Website v3: Brief, Richtung, System, Aufbau

## 0. Brief
- **Projekt:** Pin Visuals 2.0.0, eine Fabric-Mod für Minecraft 1.21.11, 26.1, 26.2 und 26.3. 39 Features, nur clientseitig.
- **Publikum:** Minecraft-Spieler (oft PvP, oft mit NoRisk, Modrinth oder Prism), die ihr Spiel schöner machen wollen und wissen wollen: Was kann die Mod, läuft sie auf meiner Version, wo ist die Datei?
- **Hauptaktion:** Mod herunterladen.
- **Ton in 3 Wörtern:** präzise, trocken, stolz.
- **Referenzen:** gedruckte Bedienungsanleitungen alter Spielmodule, Museums-Bildtafeln, Schweizer Feldführer.
- **Abschnitte:** Start, Einleitung, Features (Auswahl + vollständiges Register), Zahlen + Installation, Kolophon/FAQ, Download, Footer.
- **Echte Inhalte:** Screenshots aus dem Spiel, Feature-Liste, Versionen, Dateigrößen, Tasten, Speicherorte.
- **Stack:** HTML + CSS + Vanilla-JS, GSAP 3.12.5 + ScrollTrigger und Lenis 1.1.13 per CDN.

## 1. Richtung
**Konzept:** Das gedruckte Handbuch zu einer Mod, die es nur auf Bildschirmen gibt. Ein Feldführer auf warmem Papier, in dem die Screenshots wie nummerierte Bildtafeln stehen („Tafel 04 — Batterie statt Risse“).

**Die ersten 3 Sekunden:** Ruhe und Ernst. Großer Serifentitel auf Papier, daneben eine einzige große Spielszene, die über den Rand läuft. Es fühlt sich an wie ein aufgeschlagenes Buch, nicht wie ein Launcher.

**Was ich bewusst nicht mache:**
1. Keine Verläufe, kein Leuchten, kein Glas. Farbe kommt nur aus den Screenshots selbst. Die Seite ist Papier und Tinte.
2. Keine Kartenraster mit Icons. Features stehen als Liste wie in einem Register, mit Nummern und Haarlinien.
3. Nichts zentriert. Alles hängt an einem 12-Spalten-Raster mit bewusst schiefen Spannen.

## 2. Designsystem
- **Farben:** Papier `#F1EDE4`, Fläche `#E8E3D7`, Tinte `#17150F`, gedämpft `#5E584B`, Akzent Redstone `#B8321A` (nur Links, ein Knopf, Tafel-Nummern).
- **Schrift:** Instrument Serif (Titel, auch kursiv), Hanken Grotesk (Text), JetBrains Mono (Etiketten, Zahlen).
- **Skala:** Display `clamp(3.4rem, 9vw, 9.5rem)`, H2 `clamp(2.2rem, 4.6vw, 4.6rem)`, Text 1.0625rem, Etiketten 0.72rem mit +0.08em Laufweite.
- **Raum:** 8px-Basis `--s1`…`--s10`, 12 Spalten, max. 1440px, Abschnitte `clamp(6rem, 12vw, 12rem)`.
- **Radien:** 0 überall, außer Knöpfen (volle Pille).
- **Linien:** 1px Haarlinien in Tinte bei 18 % statt Kästen und Schatten.

## 3. Aufbau
1. **Start:** Titel „Minecraft, neu belichtet.“ über 7 Spalten, rechts Tafel 01 (Butterfly-Inspect), die über den Rand läuft. Eine Zeile Text, ein Knopf. Zeilen gleiten maskiert hoch.
2. **Einleitung:** ein großer Absatz über 9 Spalten, Wörter werden beim Scrollen von 20 % auf 100 % dunkel.
3. **Ausgewählte Features:** nummerierte Liste mit 8 Einträgen. Beim Überfahren folgt eine Bildtafel dem Mauszeiger.
4. **Bildtafeln:** zwei große Tafeln, versetzt, mit Clip-Wipe und leichtem Parallax.
5. **Register:** alle 39 Features dicht in Spalten, nach Kategorie, wie ein Buchindex.
6. **Zahlen + Installation:** 39 / 4 / 8,3 MB / 0, dann vier nummerierte Schritte an Haarlinien.
7. **Download:** Versionstabelle mit Datei, Java, Fabric API, Link.
8. **Kolophon:** wie die Mod gebaut und getestet ist, dazu die FAQ als nüchterne Absätze.
9. **Schluss:** riesiges „Drück O.“ mit einem Knopf.
10. **Footer:** dicht, kleine Schrift, Tasten, Speicherorte, „Nach oben“.

## 6. Bewegung (8 Stück)
Lader (Zähler 00→39, einmal pro Sitzung, überspringbar) · maskierte Titelzeilen · Lenis-Scrollen · Text-Reveals (24px, einmal) · Bild-Wipe mit Skalierung 1.15→1 · Wörter-Scrub · leichtes Parallax (nur Desktop) · Bildtafel folgt dem Zeiger in der Liste. Dazu ein Menü, das beim Runterscrollen verschwindet und beim Hochscrollen zurückkommt. Bei „Bewegung reduzieren“ ist alles statisch.
