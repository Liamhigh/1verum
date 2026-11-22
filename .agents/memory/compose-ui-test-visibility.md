---
name: Compose UI test visibility
description: A Robolectric Compose test interaction can miss a clickable node when it is outside the scroll viewport.
---

In Compose UI tests, explicitly call `performScrollTo()` before clicking a control inside vertically scrollable content.

**Why:** A test that matched an off-screen button and called `performClick()` did not trigger its handler; scrolling it into view first made the interaction reliable.

**How to apply:** Before `performClick()` on a node inside a `verticalScroll` container, use `performScrollTo()` on that node.
