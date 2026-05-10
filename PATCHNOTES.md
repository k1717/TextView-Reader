# TextView Reader 2.0.4

## Bookmark UI and stability

- Main bookmark folders default to collapsed/shrunk.
- Bookmark folder expand/shrink remains fast.
- Bookmark edit dialogs use a more rounded bordered custom style.
- TXT bookmark memo edit now has **Cancel / Clear memo / Save**.
- PDF bookmark memo edit now has **Cancel / Clear memo / Save**.
- EPUB/Word bookmark memo edit now has **Cancel / Clear memo / Save**.
- Main bookmark delete/edit dialogs use the stable custom dialog path to reduce hard-edge and hard-landing visual glitches.
- Bookmark opening uses shared navigation with null/empty file-path protection.

## Theme refresh fix

- Viewers reload theme state after returning from Settings or the theme editor.
- TXT, PDF, EPUB, and Word More dialogs refresh active theme colors before drawing.
- PDF More dismisses stale dialog instances before opening Settings or File Info.
- Active theme saving uses synchronous commit to avoid immediate-return race behavior.
