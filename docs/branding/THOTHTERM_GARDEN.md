# ThothTerm Garden identity

ThothTerm Garden is the product-family system for ThothTerm Terminal Emulator
and distro editions. Its mark is an original five-petal geometric flower built
around a small `>_` terminal seed. It does not reuse a distro logo and does not
imply endorsement by Canonical or another distributor.

## Palette and editions

- Family foundation: midnight `#07111F`, navy `#10243A`, teal `#39D9C5`.
- Ubuntu flavor: amber `#F2A23A`, orange `#E88332`, warm highlight `#FFB44D`.
- Future editions should preserve the same petal geometry and terminal seed,
  changing only the edition flavor colors. No future edition is specified here.

## Icon use

Keep all meaningful foreground geometry within the central 66% adaptive-icon
safe zone. The five-petal silhouette must remain legible before the terminal
seed; at very small sizes, omit the seed rather than shrinking it. Adaptive,
round, legacy, and themed monochrome assets must share the same silhouette.
The source SVGs in this directory are the reusable masters.

## Terminal welcome

The shell representation uses only ASCII so cell width is deterministic. Keep
it compact, use amber for the upper petals and teal for the lower petals and
terminal seed, and switch to a stacked information layout on narrow terminals.
The art is identity, not a substitute for semantic text: the product name and
runtime details must remain readable without color.
