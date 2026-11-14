# LaunchOS design system

One source for both apps: LaunchOS (the launcher's Mi Box edition, `launcher/mibox`) and LaunchOS TV (`mibox/tv`).
Both builds add `res/` and `java/` from here to their own sources, so a value changed here changes both apps.

- `res/values/lo_colors.xml`: the palette. One black, three surfaces, three text levels, lines, cream for the brand.
  The accent (`lo_accent`) is the one thing each app defines itself: gold for LaunchOS, coral for LaunchOS TV.
- `res/values/lo_dimens.xml`: radii (12, 20, pill), the 4/8 spacing steps and the page frame (48 × 27 dp, Google
  TV's safe area).
- `res/values/lo_type.xml`: the type scale (14, 16, 20, 28, 40 sp; nothing smaller but the 13 sp label).
- `res/drawable/lo_focus_*.xml`, `res/color/lo_*_on_focus.xml`: the one focus look. A focused row, button or chip
  turns white with dark text; a picture tile gets a white outline. `Focus.lift` adds the 1.05 scale.
- `java/.../Motion.java`: two curves and three durations; `Focus.java`; `Monogram.java`: the "lo" mark, drawn as it
  is written on the start-up screens. `res/drawable/lo_mark.xml` is the same mark as a vector.

Fonts are not referenced here: each app sets Inter its own way.
