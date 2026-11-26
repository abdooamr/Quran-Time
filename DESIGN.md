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

<!-- impeccable:design-map -->
Name: Quran Time
Colors: paper-ivory #F4EEDD, paper-sepia #EBDDC2, night-ground #121212, ink-black #231F20, ink-sepia #4A3B28, ink-night #E6E1D6, mushaf-green #2F6B4F, mushaf-green-night #1D4434, green-night-accent #8FCBA9, on-green-night-accent #0B2A1E (+12 more in the frontmatter)
Fonts: Amiri (book-title, book-numeral); Roboto (title, body, label, roundel-caption)
Spacing: hair 4px, tight 6px, sm 8px, md 16px, lg 24px, xl 40px
Radius: field 4px, full 9999px
North Star: "The Madinah Mushaf Edition"
Rules:
- The Gilt Is Line Rule. Gilt appears only as hairline double rules, roundel rims, cartouche borders and rosettes.
- The Re-tuned, Not Inverted Rule. Each theme defines its own paper, ink, field, gilt and chrome.
- The Ink Only Rule. Theme switching changes the page's ink colour and nothing else about the page.
- The Printed Word Rule. If the printed Mushaf would print it (a surah name, a juz number, a page number), it is set in Amiri Bold.
- The Phone Decides Rule. Interface text follows the system font scale; roundels grow with it (up to 1.3x their base size, so the juz roundel never towers over the cartouche) so numerals never clip.
- The Paper Has No Shadow Rule. The page and its apparatus never cast or receive a shadow; separation is tone and gilt line.
Don't:
- Don't place anything over the page except the recitation wash during timed playback and, while its actions are open, the tapped-ayah mark.
- Don't use gilt for icons, text, or fills.
- Don't invert the dark theme into gold on black; night is a dark ground with brighter gilt hairlines.
- Don't use a green app bar with a gold arabesque band.
- Don't add shadows to the page or its apparatus.
- Don't set controls, buttons or labels in Amiri.
Do:
- Do leave the Mushaf SVG page uncovered, uncropped and undecorated; recolour only its ink per theme (ink-black, ink-sepia, ink-night).
- Do fit the whole page into the available space and draw the gilt frame outside it.
- Do keep gilt to double hairline rules, roundel rims, cartouche borders and rosettes.
- Do set surah names and juz and page numerals in Amiri Bold, in the reader's locale digits.
- Do use the cartouche and roundels wherever a surah, juz or page is named.
- Do define every new colour for all three themes, re-tuned per theme.
- (+2 more)
Full: .impeccable/design-full.md (read a section when you need its detail)
