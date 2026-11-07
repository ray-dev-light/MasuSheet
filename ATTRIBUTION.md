# Asset Attribution

Every non-code asset in this repository (launcher icons and documentation screenshots) is listed here with its author, source, and open-source license.

**Policy (mandatory):** MasuSheet and its upstream GridCraft calculation core contain **no Microsoft, Adobe, Avid, or Autodesk iconography, images, artwork, fonts, templates, themes, table styles, or sample files**. Every asset in this repository is original work under an OSI-approved open-source license (`MIT OR Apache-2.0`). Upstream non-open-source ArtCraft brand files (`docs/brand/`) and unused desktop packaging assets have been removed in this fork.

Generated-in-code spreadsheet themes (`crates/engine/src/cmd/view.rs`), cell styles (`crates/engine/src/cmd/format.rs`), table styles (`crates/engine/src/tables.rs`), chart palettes, and sample workbooks (`crates/engine/src/sample.rs`) are clean-room code rather than binary asset files.

| Asset | Author | Source | License | Notes |
|---|---|---|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | MasuSheet contributors | Original (hand-written Android VectorDrawable XML) | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | Adaptive launcher icon foreground |
| `app/src/main/res/drawable/ic_launcher_background.xml` | MasuSheet contributors | Original (hand-written Android VectorDrawable XML) | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | Adaptive launcher icon background |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | MasuSheet contributors | Original (adaptive-icon XML) | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | Android adaptive launcher icon |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | MasuSheet contributors | Original (adaptive-icon XML) | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | Android round adaptive launcher icon |
| `docs/screenshots/masusheet-overview.png` | MasuSheet contributors | Screenshot of MasuSheet running on Android showing an original sample scatter chart, cell range highlights, 15-tab ribbon, and cell context menu | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | README hero screenshot; contains no third-party content |
| `docs/screenshots/masusheet-pivot-ai-ja.jpg` | MasuSheet contributors | Screenshot of MasuSheet running on Android (Japanese UI) showing an original Inventory & Reorder worksheet, 2-level PivotTable, PivotTable Fields pane, and AI Assistant | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | README gallery screenshot; contains no third-party content |
| `docs/screenshots/masusheet-macro-dark.png` | MasuSheet contributors | Screenshot of MasuSheet running on Android (Dark Mode) showing the `Automate` ribbon tab and Macro & VBA Automation Studio | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | README gallery screenshot; contains no third-party content |
| `docs/screenshots/masusheet-mobile-formula.png` | MasuSheet contributors | Framed portrait smartphone screenshot of MasuSheet running on Android with collapsed ribbon and live formula auto-complete | MIT OR Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`) | README gallery screenshot; contains no third-party content |
