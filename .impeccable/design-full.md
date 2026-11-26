---
name: Quran Time
description: The printed Madinah Mushaf's own apparatus, moved into an Android reader that leaves the page pristine.
colors:
  paper-ivory: "#F4EEDD"
  paper-sepia: "#EBDDC2"
  night-ground: "#121212"
  ink-black: "#231F20"
  ink-sepia: "#4A3B28"
  ink-night: "#E6E1D6"
  mushaf-green: "#2F6B4F"
  mushaf-green-night: "#1D4434"
  green-night-accent: "#8FCBA9"
  on-green-night-accent: "#0B2A1E"
  on-field: "#F7F1E3"
  on-field-sepia: "#F4EEDD"
  on-field-night: "#EDE6D3"
  gilt: "#A97F33"
  gilt-sepia: "#9E7630"
  gilt-night: "#C9A45A"
  chrome-ivory: "#EBE3CF"
  chrome-sepia: "#E0CFAF"
  chrome-night: "#1A1A18"
  recite-highlight: "#2E7D5B55"
  recite-highlight-sepia: "#B07D2B55"
  recite-highlight-night: "#4FB98A66"
typography:
  book-title:
    fontFamily: "Amiri, serif"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: 1.1
  book-numeral:
    fontFamily: "Amiri, serif"
    fontSize: "22px"
    fontWeight: 700
    lineHeight: 1.15
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22px"
    fontWeight: 400
    lineHeight: 1.27
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.33
  roundel-caption:
    fontFamily: "Roboto, sans-serif"
    fontSize: "10px"
    fontWeight: 600
    lineHeight: 1
rounded:
  field: "4px"
  full: "9999px"
spacing:
  hair: "4px"
  tight: "6px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "40px"
components:
  surah-cartouche:
    backgroundColor: "{colors.mushaf-green}"
    textColor: "{colors.on-field}"
    typography: "{typography.book-title}"
    padding: "8px 34px"
    height: "48px"
  roundel-juz:
    textColor: "{colors.ink-black}"
    typography: "{typography.book-numeral}"
    size: "48px"
  roundel-folio:
    textColor: "{colors.ink-black}"
    typography: "{typography.book-numeral}"
    size: "44px"
  navigation-bar:
    backgroundColor: "{colors.chrome-ivory}"
    textColor: "{colors.ink-black}"
    typography: "{typography.label}"
  bottom-sheet:
    backgroundColor: "{colors.chrome-ivory}"
  button-filled:
    backgroundColor: "{colors.mushaf-green}"
    textColor: "{colors.on-field}"
    rounded: "{rounded.full}"
    height: "40px"
  list-row-selected:
    backgroundColor: "#2F6B4F1F"
    textColor: "{colors.ink-black}"
    height: "76px"
  input-page-number:
    textColor: "{colors.ink-black}"
    typography: "{typography.book-numeral}"
    rounded: "{rounded.field}"
---

# Design System: Quran Time

## Overview

**Creative North Star: "The Madinah Mushaf Edition"**

The interface is the printed Mushaf's own apparatus. The illuminated surah cartouche, the juz roundel, the gilt page frame and the printed folio number leave the book and become the app's chrome, so the frame reads like the book while the page itself stays exactly as MP3Quran draws it. Everything around the page is quiet: book paper, black ink, one green field, and gilt used only as fine line.

Density is low and the page dominates. The reading screen has no app bar, only a single header row at the page's own ground colour (juz roundel at the start, cartouche in the centre, actions at the end), then the page, then its folio roundel, then the bottom navigation. Secondary screens (bookmarks, more, the jump sheet) are plain Material 3 surfaces carried in the paper palette, with the book face and roundels reappearing wherever a surah, juz or page is named. The direction explicitly refuses the familiar green app bar with a gold arabesque band.

Three themes ship as complete palettes, not tints: ivory paper, sepia paper, and night ground. Night is a dark ground with brighter gilt hairlines; gold is never inverted into a fill.

**Key Characteristics:**
- The Mushaf SVG page is untouched content; only its ink colour changes per theme.
- Book apparatus (cartouche, roundels, double gilt rules, eight-petal rosettes) is the only ornament.
- Amiri for anything the book itself would print (surah names, juz and page numerals); Material type for controls.
- Flat tonal surfaces, edge-to-edge system bars, no shadows on the reading screen.
- RTL first; the next page is always to the left, in both languages.

## Colors

A book palette: warm paper, near-black ink, one Mushaf green and a thin gilt, each re-tuned per theme rather than inverted.

### Primary
- **Mushaf Green** (mushaf-green): the illuminated field of the surah cartouche, the Material primary in light and sepia (filled buttons, selected bookmark icon), and at 14% the navigation indicator and at 12% the selected row in the jump sheet. Sepia uses the same green.
- **Night Field Green** (mushaf-green-night): the cartouche field in the dark theme, deeper so the gilt rule carries the outline.
- **Night Accent Green** (green-night-accent, with on-green-night-accent): the Material primary in dark theme only, for interactive controls; the cartouche never uses it.

### Secondary
- **Gilt** (gilt / gilt-sepia / gilt-night): the hairline colour of the book apparatus. Darkened for light and sepia paper so the 0.6 to 1.4px rules stay visible; lifted to a brighter gold on night ground. Also the Material tertiary, and at 35% alpha the outlineVariant.

### Neutral
- **Ivory Paper** (paper-ivory): the reading ground and scaffold in light theme.
- **Sepia Paper** (paper-sepia): the reading ground in sepia theme.
- **Night Ground** (night-ground): the reading ground in dark theme.
- **Ink** (ink-black / ink-sepia / ink-night): the colour the SVG page's letters are drawn in (`srcIn` colour filter) for each theme. Interface text uses the Material onSurface derived from the scheme, not these.
- **Chrome** (chrome-ivory / chrome-sepia / chrome-night): bars and sheets around the page: navigation bar, bottom sheets, dialogs, Material surface and surfaceContainer. One step off the paper so chrome reads as separate from the page without a shadow.
- **On Field** (on-field / on-field-sepia / on-field-night): the surah name inside the cartouche, a paper tone on green.
- **Recitation Highlight** (recite-highlight, -sepia, -night): the wash laid on the recited ayah during timed playback: the whole ayah at 38% of this colour, the recited part at full strength (see The Reading Sweep). With the tapped-ayah mark, the only marks ever made on the page, and both are temporary.

### Named Rules
**The Gilt Is Line Rule.** Gilt appears only as hairline double rules, roundel rims, cartouche borders and rosettes. It is never an icon colour, never a text colour, never a fill.

**The Re-tuned, Not Inverted Rule.** Each theme defines its own paper, ink, field, gilt and chrome. Dark theme is a night ground with brighter gilt hairlines, never inverted gold or a gold fill.

**The Ink Only Rule.** Theme switching changes the page's ink colour and nothing else about the page.

## Typography

**Book Font:** Amiri (bundled, OFL; Regular and Bold), serif fallback
**Control Font:** Roboto via Material 3 defaults

**Character:** Amiri is a Naskh book face, so the surah name and the numerals read as print from the same tradition as the Mushaf page; Roboto stays in its lane for buttons, tabs, labels and explanatory text.

### Hierarchy
- **Book Title** (Amiri 700, Material titleMedium 16px, line-height 1.1 in the cartouche): surah names in the cartouche, surah and juz titles in the jump sheet, bookmark titles.
- **Book Numeral** (Amiri 700, sized as a fraction of the roundel: 0.4 of the ring for one or two digits, 0.3 for three, 0.34 under a caption; 22px in the page-number field): juz and page numerals, always in the reader's locale digits.
- **Title** (Roboto 400, 22px): Material app bar titles on secondary screens.
- **Body** (Roboto 400, 16px, 1.5): empty states, offline message, list subtitles at bodyMedium.
- **Label** (Roboto 500, 12px): navigation bar labels, tabs, buttons at labelLarge.
- **Roundel Caption** (Roboto 600, 0.2 of the ring with a 10px floor, line-height 1): the short "juz" word above the juz numeral.

### Named Rules
**The Printed Word Rule.** If the printed Mushaf would print it (a surah name, a juz number, a page number), it is set in Amiri Bold. Everything that operates the app is Material type.

**The Phone Decides Rule.** Interface text follows the system font scale; roundels grow with it (up to 1.3x their base size, so the juz roundel never towers over the cartouche) so numerals never clip.

## Layout

A single portrait column. The reading screen stacks a header row (8px start and end, 6px top and bottom), the swipeable page, and the system navigation bar; content sits inside the top safe area but draws behind both system bars, which are transparent with icon brightness following the theme and contrast enforcement off.

The header is three slots: a fixed 104px start slot holding the juz roundel, a flexible centre holding the cartouche, and a fixed 104px end slot holding bookmark, jump and any extra actions, with 6px gutters between. Fixed side widths keep the cartouche centred whatever the actions are.

The page is fitted, never cropped: its scale is the smaller of the available width and the available height after reserving 16px for the gilt frame and 52px for the folio roundel, with 6px horizontal and 4px vertical padding per page. The frame and folio are centred together. The page can zoom up to 4x inside its frame.

Spacing is small and even: 4 and 6px around the page apparatus, 8px header insets, 16px between stacked controls, 24px sheet content padding, 40px around empty-state copy. Jump-sheet rows have a fixed 76px extent so the sheet opens scrolled to the current surah or juz. All targets are at least 48dp.

## Elevation & Depth

Flat. The reading screen has no shadows; depth comes from tonal separation only, with chrome one step darker (light, sepia) or lighter (night) than the page ground. Bottom sheets carry no surface tint. The gilt double rule, not a shadow, is what sets the page off from its ground.

### Named Rules
**The Paper Has No Shadow Rule.** The page and its apparatus never cast or receive a shadow; separation is tone and gilt line.

## Shapes

Two geometries come from the book. The cartouche is a lozenge: a rectangle whose short ends come to points at half its height, with mitred joins. Roundels are true circles. The page frame is square-cornered with rosette knots at each corner.

Every ornament is drawn as a **double rule**: an outer rule of about 1.2 to 1.4px and an inner rule of 0.6 to 0.8px, set 3 to 3.5px apart. Rosettes are eight small petals around a centre dot (radius 3.2 to 4px); roundels replace the rosette with eight petals spaced around the outer rim.

Material controls keep their own shapes: full-pill filled buttons and navigation indicator, 4px outlined text field.

## Components

### Surah Cartouche (signature)
The illuminated surah heading, centred in the reading header.
- **Shape:** lozenge, min height 48px, padding 8px vertical and 34px horizontal.
- **Fill and rules:** field green fill, outer gilt rule 1.4px at 1px inset, inner gilt rule 0.8px at 4.5px inset, a 4px rosette 12px in from each point.
- **Text:** surah name in Book Title on on-field, single line, ellipsis.
- **Behaviour:** tapping opens the jump sheet (ink ripple clipped to the lozenge); TalkBack announces the surah name and the jump hint.

### Roundel (signature)
A printed numeral ring for juz and page numbers.
- **Shape:** double gilt circle (1.3px outer, 0.7px inner, 3.5px apart) with eight 1.6px petals on the rim.
- **Sizes:** 48px for the juz in the header (with caption), 44px for the folio under the page and for list leadings in the jump sheet and bookmarks.
- **Text:** numeral in Book Numeral, coloured onSurface; never gilt.
- **Scaling:** grows with the system text scale, clamped to 1.3x.

### Gilt Frame (signature)
A double gilt hairline drawn just outside the page, never over it: 1.2px outer rule, 0.6px inner rule, 2.4px rosettes at the inner rule's four corners (kept inside the outer rule, away from the screen edge), a 6px gap to the page.

### Navigation
- **Bottom navigation:** Material 3 NavigationBar on chrome, field green indicator at 14% (55% on night), Material outline icons, Roboto labels.
- **Reading header:** no app bar; see Layout. Actions are standard Material IconButtons; bookmark toggles to the filled icon in primary green.
- **Secondary screens:** plain Material AppBar on the paper ground.

### The Reading Sweep (signature)
The recitation highlight, drawn as ink spreading through the ayah.
- **Shape:** the ayah's outline (whole text lines, stair-stepped) with corners rounded to a fifth of a line.
- **Fill:** the whole ayah faintly; the recited part at full strength, filling right to left, line by line, in time with the ayah's start and end, with a feathered leading edge about one word wide.
- **Handover:** the old ayah's wash dissolves as the next appears, 260ms.
- **Reduce motion:** no sweep; the whole ayah is lit at once, and changes cut.

### Tapped Ayah Mark
While an ayah's actions sheet is open: a faint wash and a 1.2dp gilt hairline around its rounded outline. Gone when the sheet closes.

### Play Seal (signature)
Play and pause everywhere: the cartouche's green field as a circle with its double gilt rule (1.4px outer, 0.8px inner), icon in on-field. 44px in the mini player, 76px in the full player.

### Mini Player
Docked above the navigation bar on chrome. A gilt hairline across the top carries the green progress line (left to right in every language). Surah number in a 44px roundel (a gilt ring with a sound mark for radio and clips), surah name in Book Title, reciter in Roboto, then the seal and Next.

### Full Player
While a timed recitation plays, the recited page (in its gilt frame, with its folio and the sweep) is the centrepiece; tapping it opens that page in the Mushaf. Otherwise a 132px roundel with the surah number. Below: cartouche, reciter, a seek line (green on a gilt hairline, a small seal for a thumb), the transport with the seal, then speed, repeat and sleep pills.

### Back to Recitation
A chrome pill with a 0.8px gilt hairline border and a green sound mark, shown after the reader swipes away from the recited page, in the folio band under the page frame.

### Buttons
- **Filled:** Material 3 filled button, full pill, primary green with on-field text (night: green-night-accent with its dark on-colour). Used for the jump sheet's go action.
- **Tonal:** Material 3 filled tonal button for retry on the offline state.

### Lists
- **Rows:** Material 3 ListItem with a 44px roundel leading, Book Title title, Roboto subtitle. Selected row (current surah or juz): field green at 12%, text stays onSurface.
- **Dividers:** 1px Material dividers, indented 72px in bookmarks.

### Inputs / Fields
- **Page number field:** outlined, 4px radius, the entered number in Amiri Bold 22px, numeric keyboard, Material error text for an out-of-range page.

### Jump Sheet
Modal bottom sheet at 88% height with drag handle and safe area, chrome background, three tabs (surahs, juz, page). Lists open scrolled to the current entry.

### Motion
The reading sweep advances with the recitation and hands over between ayat in 260ms; the full player's page cross-fades over 320ms when the recitation turns the page. On a page turn the juz roundel and the cartouche fade through to the new value: fade in with scale from 0.92 to 1.0 over 260ms. Programmatic jumps of three pages or fewer animate the page view over 320ms ease-out cubic; longer jumps cut. With reduce-motion on, every one of these is an instant cut. Loading shows nothing for 300ms, then a 28px spinner.

## Do's and Don'ts

### Do:
- **Do** leave the Mushaf SVG page uncovered, uncropped and undecorated; recolour only its ink per theme (ink-black, ink-sepia, ink-night).
- **Do** fit the whole page into the available space and draw the gilt frame outside it.
- **Do** keep gilt to double hairline rules, roundel rims, cartouche borders and rosettes.
- **Do** set surah names and juz and page numerals in Amiri Bold, in the reader's locale digits.
- **Do** use the cartouche and roundels wherever a surah, juz or page is named.
- **Do** define every new colour for all three themes, re-tuned per theme.
- **Do** fade-through header changes over 260ms and cut instantly under reduce-motion.
- **Do** keep system bars transparent and edge-to-edge, with icon brightness matching the theme.

### Don't:
- **Don't** place anything over the page except the recitation wash during timed playback and, while its actions are open, the tapped-ayah mark. Floating controls (Back to recitation) sit in the folio band, never over the text.
- **Don't** use gilt for icons, text, or fills.
- **Don't** invert the dark theme into gold on black; night is a dark ground with brighter gilt hairlines.
- **Don't** use a green app bar with a gold arabesque band.
- **Don't** add shadows to the page or its apparatus.
- **Don't** set controls, buttons or labels in Amiri.
