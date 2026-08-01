# Deep Research UI Guide

**Version:** 1.0.0  
**Status:** IMPLEMENTED  
**Date:** July 22, 2026  
**Framework:** Jetpack Compose

---

## Overview

Complete Jetpack Compose UI for the Gemma 3 Admin Hub deep research feature. Provides a user-friendly interface for:

- Entering research queries
- Selecting evidence from the vault
- Tracking real-time analysis progress
- Reviewing forensic findings
- Providing verification feedback
- Monitoring engine health and synchronization

---

## Architecture

### Screens

```
┌─────────────────────────────────────┐
│   DeepResearchScreen                │
│   (Query Input & Evidence Selection) │
└────────────────┬────────────────────┘
                 │ [Start Research]
                 ▼
┌─────────────────────────────────────┐
│   ResearchProgressScreen            │
│   (Real-time Progress Tracking)     │
└────────────────┬────────────────────┘
                 │ [Complete]
                 ▼
┌─────────────────────────────────────┐
│   ResearchResultsScreen             │
│   (Results Review & Feedback)       │
└────────────────┬────────────────────┘
                 │ [Submit Feedback]
                 ▼
    [Sync to Website Hub]
    [Export Sealed Report]
                 │
                 ▼
┌─────────────────────────────────────┐
│   EngineStatsScreen                 │
│   (Admin Dashboard & Sync Status)   │
└─────────────────────────────────────┘
```

### ViewModels

**DeepResearchViewModel**
- Manages research state machine
- Handles session lifecycle
- Tracks poll status
- Submits verification feedback

**EngineStatsViewModel**
- Manages sync state
- Tracks rule updates
- Monitors engine health

### Navigation

**DeepResearchNavigator**
- Routes between screens based on `DeepResearchViewModel.ResearchState`
- Handles transitions and state management
- Integrates with ViewModels

**EngineStatsNavigator**
- Routes to engine statistics dashboard
- Manages admin operations

---

## Screen Details

### 1. DeepResearchScreen

**Purpose:** Entry point for deep research queries

**Components:**
- Query text field (multiline)
- Evidence selector (modal dialog picker)
- Optional context field
- Submit button (enabled when query + evidence selected)
- Tips section

**Usage:**
```kotlin
DeepResearchScreen(
    vaultEvidenceIds = listOf("doc1", "doc2", "doc3"),
    jurisdiction = "SA",
    onStartResearch = { query, evidenceIds, jurisdiction ->
        // Start research
    }
)
```

**Key Features:**
- Large text field for natural language queries
- Multi-select evidence picker with "FolderOpen" icon
- Real-time validation (button disabled until ready)
- Helpful tips at bottom
- Clear button to reset form

**Example Queries:**
- "Find all contradictions about wire transfers to offshore accounts"
- "Identify patterns in false statements about asset ownership"
- "Extract timeline gaps that indicate consciousness of guilt"

---

### 2. ResearchProgressScreen

**Purpose:** Show live progress while Gemma 3 analyzes

**Components:**
- Animated loading indicator (rotating Psychology icon)
- Elapsed time counter
- 5-stage progress indicator
- Current query display
- Cancel button

**Usage:**
```kotlin
ResearchProgressScreen(
    sessionId = "RSH-123456789",
    query = "Find all contradictions about wire transfers...",
    startedAt = Instant.now(),
    onCancel = { /* cancel */ }
)
```

**Stages:**
1. **Vault Loading** (0-10s) — Retrieving evidence documents
2. **Query Processing** (10-25s) — Parsing query with Gemma 3
3. **Evidence Synthesis** (26-45s) — Synthesizing narrative
4. **Contradiction Detection** (46-60s) — Extracting contradictions
5. **Rule Inference** (60+s) — Inferring new rules

**Visual Indicators:**
- Animated circle with pulsing icon
- Stage numbers with check marks (complete) or dots (active)
- Description text for each stage
- Color coding (gold = active, green = complete, muted = pending)

---

### 3. ResearchResultsScreen

**Purpose:** Display and review research findings

**Tabs:**
1. **Narrative** — Full research narrative from Gemma 3
2. **Contradictions** — List of extracted contradictions
3. **Rules** — Suggested rules to prevent similar contradictions

**Components:**
- Confidence badge (VERY_HIGH/HIGH/MODERATE/LOW)
- Tab bar for navigation
- Results cards with details
- Verification feedback dialog
- Export sealed report button

**Usage:**
```kotlin
ResearchResultsScreen(
    sessionId = "RSH-123456789",
    narrative = "Research narrative text...",
    contradictionsFound = 5,
    newRulesSuggested = 3,
    confidence = "HIGH",
    verificationUrl = "https://verumglobal.foundation/verify...",
    onSubmitFeedback = { approved, corrections, comments ->
        // Submit feedback
    },
    onExportReport = {
        // Export as sealed PDF
    }
)
```

**Narrative Tab:**
- Full text from Gemma 3 research
- Every claim anchored to evidence
- Confidence levels for each finding

**Contradictions Tab:**
- Card for each contradiction
- Shows: title, respondent, evidence anchor, confidence
- Clickable for detail view
- "+" indicator for additional contradictions

**Rules Tab:**
- Card for each suggested rule
- Shows: rule name, pattern, confidence
- Rules ranked by confidence
- "+" indicator for additional rules

**Feedback Dialog:**
- Checkbox to approve findings
- Text field for comments/corrections
- Submit button sends to verification hub
- Auto-queues for website sync

---

### 4. EngineStatsScreen

**Purpose:** Admin dashboard for engine health and sync status

**Tabs:**
1. **Overview** — Health indicators, recent activity, compliance
2. **Sync Status** — Website sync and rule update status
3. **System** — Version info, statistics, system configuration

**Components:**
- Stat cards (active researches, feedback queue, rule updates)
- Health indicators (Nine-Brain, Gemma 3, Feedback Sync)
- Activity rows with icons and values
- Compliance checks (Constitutional rules)
- Sync status rows
- System information table

**Usage:**
```kotlin
EngineStatsScreen(
    onSyncToWebsite = {
        // Manually trigger sync
    },
    onCheckRuleUpdates = {
        // Check for new rules
    }
)
```

**Health Indicators:**
- Nine-Brain Engine → ACTIVE
- Gemma 3 Admin Hub → ACTIVE
- Feedback Sync → IDLE/SYNCING

**Compliance Checks:**
- Truth over Probability ✓ COMPLIANT
- Evidence before Narrative ✓ COMPLIANT
- Mandatory Contradiction Disclosure ✓ COMPLIANT

**Sync Status:**
- Feedback Queue → # packets ready
- Last Sync → timestamp or "Never"
- Connection → ONLINE/OFFLINE

**System Info:**
- Constitution Version → 6.0
- Nine-Brain Version → v1.0
- Gemma 3 Model → admin-hub
- Platform → Android 13+
- App Version → 6.0

---

## State Machine

### DeepResearchViewModel.ResearchState

```
Idle
  │
  ├─ showQueryInput() ──→ QueryInput
  │                          │
  │                          ├─ startResearch() ──→ InProgress
  │                          │                         │
  │                          │                         └─ [polling] ──→ Complete
  │                          │                                             │
  │                          └─ [error] ──────────→ Error
  │
  └─ [error] ──────────────→ Error
                                │
                                └─ retry() ──→ QueryInput
```

**State Transitions:**
- `Idle` → `QueryInput`: Call `showQueryInput()`
- `QueryInput` → `InProgress`: Call `startResearch(query, evidenceIds, jurisdiction)`
- `InProgress` → `Complete`: Automatic (via polling)
- `InProgress` → `Error`: Timeout or failure
- `Error` → `QueryInput`: Call `showQueryInput()`
- `Complete` → `Idle`: Call `reset()`

---

## ViewModel Integration

### DeepResearchViewModel

```kotlin
val researchVm = viewModel<DeepResearchViewModel>()

// Observe state
val state by researchVm.state.collectAsState()

// Start research
researchVm.startResearch(
    query = "Find contradictions...",
    vaultEvidenceIds = listOf("doc1", "doc2"),
    jurisdiction = "SA",
    userContext = "AllFuels case"
)

// Submit verification feedback
researchVm.submitVerificationFeedback(
    userApproved = true,
    corrections = emptyList(),
    comments = "Findings match our manual analysis"
)

// Export report
researchVm.exportResearchReport(sessionId)

// Cancel or reset
researchVm.cancelResearch()
researchVm.reset()
```

### EngineStatsViewModel

```kotlin
val statsVm = viewModel<EngineStatsViewModel>()

// Observe sync state
val syncState by statsVm.syncState.collectAsState()

// Observe rule update state
val ruleUpdateState by statsVm.ruleUpdateState.collectAsState()

// Sync feedback
statsVm.syncFeedbackToWebsite()

// Check for rule updates
statsVm.checkForRuleUpdates(jurisdiction = "SA")

// Get current stats
val stats = statsVm.getEngineStats()
```

---

## Theme & Styling

### Color Palette

| Name | Hex | Usage |
|------|-----|-------|
| VoBackground | #040D1B | Page background |
| VoSurface | #0A1526 | Card backgrounds |
| VoSurfaceAlt | #0F3460 | Accent cards |
| VoGold | #D4A843 | Primary CTAs, accents |
| VoAccentBlue | #4A7EC7 | Links, secondary actions |
| VoGreen | #22c55e | Verified, success |
| VoRed | #ef4444 | Errors, fraud |
| VoTextPrimary | #E8E6E1 | Main text |
| VoTextMuted | #8A94A6 | Secondary text |

### Typography

**Fonts:**
- Cormorant Garamond (headings)
- Source Sans 3 (body)
- JetBrains Mono (monospace)

**Sizes:**
- H1: 28sp bold
- H2: 20sp bold
- Body: 13-14sp
- Small: 11-12sp
- Tiny: 10sp

---

## Integration with MainActivity

Add deep research to your main navigation:

```kotlin
// In MainActivity composable
var currentScreen by remember { mutableStateOf("home") }

when (currentScreen) {
    "deep_research" -> {
        DeepResearchNavigator(
            vaultEvidenceIds = vaultEvidenceIds,
            onNavigate = { route ->
                if (route is DeepResearchRoute.Stats) {
                    currentScreen = "stats"
                }
            }
        )
    }
    "stats" -> {
        EngineStatsNavigator()
    }
}

// In TopBar or navigation menu:
NavigationButton(
    label = "Deep Research",
    onClick = { currentScreen = "deep_research" }
)

NavigationButton(
    label = "Engine Stats",
    onClick = { currentScreen = "stats" }
)
```

---

## Dialog Components

### EvidenceSelectorDialog

Modal for selecting multiple vault documents:

```kotlin
var showSelector by remember { mutableStateOf(false) }
var selectedIds by remember { mutableStateOf(emptyList<String>()) }

if (showSelector) {
    EvidenceSelectorDialog(
        allEvidence = allDocIds,
        selectedIds = selectedIds,
        onSelectionChange = { selectedIds = it },
        onDismiss = { showSelector = false }
    )
}
```

### FeedbackDialog

Collect user verification feedback:

```kotlin
FeedbackDialog(
    onSubmit = { approved, corrections ->
        // Submit feedback
    },
    onDismiss = { /* close */ },
    feedbackNotes = notes,
    onNotesChange = { notes = it }
)
```

---

## Testing

### Unit Tests

```kotlin
class DeepResearchViewModelTest {
    @Test
    fun testStartResearch() {
        val vm = DeepResearchViewModel()
        vm.startResearch(
            query = "Test query",
            vaultEvidenceIds = listOf("doc1"),
            jurisdiction = "SA"
        )
        assertTrue(vm.state.value is DeepResearchViewModel.ResearchState.InProgress)
    }
}

class EngineStatsViewModelTest {
    @Test
    fun testSyncFeedback() {
        val vm = EngineStatsViewModel()
        vm.syncFeedbackToWebsite()
        assertTrue(vm.syncState.value.isSyncing)
    }
}
```

### UI Tests (Roborazzi)

```kotlin
@Composable
fun PreviewDeepResearchScreen() {
    DeepResearchScreen(
        vaultEvidenceIds = listOf("Evidence_001", "Evidence_002"),
        jurisdiction = "SA",
        onStartResearch = { _, _, _ -> }
    )
}

@Composable
fun PreviewResearchProgressScreen() {
    ResearchProgressScreen(
        sessionId = "RSH-1234567890",
        query = "Find contradictions about wire transfers...",
        onCancel = {}
    )
}
```

---

## Accessibility

All screens follow Material 3 accessibility guidelines:

- ✓ Minimum touch target size (48dp)
- ✓ Color contrast > 4.5:1 for text
- ✓ Icons have content descriptions
- ✓ Meaningful button labels
- ✓ Focus indicators visible
- ✓ State changes announced (Compose automatically)

---

## Performance Notes

- **Lazy loading:** Results use lazy column patterns
- **Image optimization:** Icons are vector (no bitmaps)
- **Composition scoping:** State updates only trigger necessary recompositions
- **Memory:** ViewModels retained across configuration changes
- **Coroutines:** All async work on Dispatchers.Default

---

## Common Use Cases

### Start a Deep Research Query

```kotlin
DeepResearchScreen(
    vaultEvidenceIds = vaultFiles.map { it.name },
    jurisdiction = "SA",
    onStartResearch = { query, evidenceIds, jurisdiction ->
        viewModel.startResearch(query, evidenceIds, jurisdiction)
    }
)
```

### Review Research Results

```kotlin
// When research completes, automatically navigates to:
ResearchResultsScreen(
    sessionId = sessionId,
    narrative = result.narrative,
    contradictionsFound = result.contradictionsFound.size,
    newRulesSuggested = result.newRulesSuggested.size,
    confidence = result.confidence,
    verificationUrl = result.verificationUrl,
    onSubmitFeedback = { approved, corrections, comments ->
        viewModel.submitVerificationFeedback(approved, corrections, comments)
    }
)
```

### Check Engine Health

```kotlin
EngineStatsScreen(
    onSyncToWebsite = {
        statsViewModel.syncFeedbackToWebsite()
    },
    onCheckRuleUpdates = {
        statsViewModel.checkForRuleUpdates()
    }
)
```

---

## Troubleshooting

**Screen not updating:**
- Ensure ViewModel is using `MutableStateFlow` and `.update { }`
- Check that Composable is collecting state with `collectAsState()`

**Navigation not working:**
- Verify route changes are triggering state updates
- Check that `DeepResearchNavigator` is wired to observe `state`

**Styles looking wrong:**
- Import VoGold, VoBackground, etc. from `theme/Theme.kt`
- Use `Modifier.clip(RoundedCornerShape(8.dp))` before background

**Buttons disabled:**
- Check validation logic in screen
- Verify state prerequisites are met

---

## File Structure

```
app/src/main/java/com/verumomnis/forensic/ui/
├── DeepResearchScreen.kt         (Query input + evidence selector)
├── ResearchProgressScreen.kt     (Live progress tracking)
├── ResearchResultsScreen.kt      (Results + feedback)
├── EngineStatsScreen.kt          (Admin dashboard)
├── DeepResearchViewModel.kt      (State management)
├── DeepResearchNavigation.kt     (Screen routing)
└── theme/
    └── Theme.kt                  (Color palette)
```

---

## References

- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Material 3 Design System](https://m3.material.io/)
- [Verum Omnis Constitution v6.0](../Constitution.md)
- [Hybrid Forensic Engine Guide](../HYBRID_FORENSIC_ENGINE.md)

---

*"The truth does not require belief. It requires only that you look."*

**Verum Omnis — UI for Forensic Truth**
