# Design revision: reading along and the players

Extends "The Madinah Mushaf Edition" (the design system in the Flutter
repo's DESIGN.md). Same palette, faces and rules; these are the new pieces.

## The reading sweep (recitation highlight)

- The recited ayah gets a soft wash in the theme's recitation colour: the
  whole ayah faintly (38% of the colour's strength), and the recited part at
  full strength. The recited part fills **right to left, line by line**, in
  time with the recitation (ayah start/end times), with a feathered leading
  edge about one word wide.
- The wash follows the ayah's outline with its corners rounded to a fifth of
  a text line, so it reads as ink, not a box.
- One ayah hands over to the next with a 260 ms cross-fade.
- **Reduce motion** (Remove animations): no sweep; the whole ayah is lit at
  once, and changes cut.
- Cost: progress is read only while drawing, so only the highlight layer
  redraws (no recomposition) while audio plays.

## The tapped ayah

While an ayah's actions are open, it is marked with a faint wash and a gilt
hairline (1.2 dp) around its rounded outline. With the recitation wash,
these are the only marks ever made on the page, and both are temporary.

## The play seal

The play/pause control everywhere: the cartouche's green field as a circle
with its double gilt rule (1.4 dp outer, 0.8 dp inner), icon in on-field.
44 dp in the mini player, 76 dp in the full player.

## Mini player

A gilt hairline across the top carries the green progress line (left to
right in every language). The surah number sits in a 44 dp roundel (a gilt
ring with a sound mark for radio and clips); the surah name is in Amiri
Bold; then the seal and Next.

## Full player

While a timed recitation plays, the page being recited (with the sweep) is
the centrepiece, in its gilt frame with its folio; tapping it opens that
page in the Mushaf. Otherwise a 132 dp roundel with the surah number. The
seek bar is a slim green line on a gilt hairline with a small seal for a
thumb (Material's slider keeps touch and TalkBack). The preview uses the
reading-size page image scaled down; it never makes images of its own size.

## Back to recitation

A chrome pill with a gilt hairline border, in the folio band under the page
frame: it may cover the page number while shown, never the text.
