# Hybrid Forensic Engine with Gemma 3 Admin Hub

**Version:** 1.0.0  
**Status:** IMPLEMENTED  
**Date:** July 22, 2026  
**Constitution:** v6.0

---

## Overview

The Hybrid Forensic Engine is a three-tier system where:

1. **Local (1verum Android)** — Gemma 3 Admin Hub runs deep research on device
2. **Verification Hub (webdocsol Website)** — Humans verify findings and evolve rules
3. **Backend (Firebase)** — Orchestrates feedback loop and rule distribution

This creates a continuously improving forensic engine that evolves with emerging threats.

---

## Architecture

```
Android Device (1verum)
├── Nine-Brain Engine (local analysis)
│   └── Contradiction extraction, timeline, financial analysis
├── Gemma 3 Admin Hub (deep research orchestrator)
│   ├── Research query processing
│   ├── Contradiction synthesis
│   └── Rule inference
└── Feedback Sync (bidirectional webdocsol integration)
    ├── Send: Contradiction findings → verification hub
    └── Receive: Evolved rules ← verification hub

webdocsol (Verification Hub)
├── Contradiction Feedback Receiver
│   └── Store findings for human review
├── Human Verification Workflow
│   └── Experts validate and correct findings
├── Rule Evolution Engine
│   └── Infer new rules from verified data
└── Rule Broadcasting
    └── Distribute updates back to devices

Firebase (Backend Orchestrator)
├── Feedback Processor
│   └── Receive and aggregate from all devices
├── Rule Evolution Pipeline
│   └── Verify rules and escalate threats
├── Rule Distribution
│   └── Broadcast updates by jurisdiction
└── Threat Tracking
    └── Monitor contradiction evolution
```

---

## Key Components

### 1. G3AdminHub (Android)

**File:** `app/src/main/java/com/verumomnis/forensic/engine/G3AdminHub.kt`

**Responsibility:** Orchestrates deep research queries and contradiction management.

**Key Methods:**
- `executeResearch()` — Run deep research on vault evidence
- `sendContradictionFeedback()` — Send findings to verification hub
- `evolveEngine()` — Apply new rules to forensic engine
- `trackContradictionEvolution()` — Monitor how threats evolve

**Flow:**
1. User initiates deep research query (e.g., "Find all contradictions about payment authorization")
2. Query is sent to Gemma 3 model with vault evidence context
3. Gemma 3 synthesizes narrative anchored to evidence (person, page, line)
4. Contradictions are extracted and ranked by confidence
5. New rules are inferred to prevent similar contradictions
6. Findings packaged and sent to webdocsol for human verification

### 2. WebFeedbackSync (Android)

**File:** `app/src/main/java/com/verumomnis/forensic/engine/WebFeedbackSync.kt`

**Responsibility:** Bidirectional sync with webdocsol verification hub.

**Key Methods:**
- `packageFeedback()` — Format contradictions for sending
- `sendFeedbackToWebsite()` — Submit findings to verification hub
- `checkForRuleUpdates()` — Download evolved rules from website
- `applyRuleUpdates()` — Integrate new rules into local engine
- `trackRuleEffectiveness()` — Monitor rule accuracy over time

**Data Structures:**
- `FeedbackPacket` — Contradiction findings to send
- `RuleUpdate` — New rule received from website
- `VerificationResult` — Human review result from website

### 3. HybridForensicService (Android)

**File:** `app/src/main/java/com/verumomnis/forensic/engine/HybridForensicService.kt`

**Responsibility:** Main API for UI integration.

**Key Methods:**
- `startDeepResearch()` — Begin research session
- `getResearchStatus()` — Check progress
- `getResearchResult()` — Retrieve findings
- `submitVerificationFeedback()` — Send user's verification results
- `syncFeedbackToWebsite()` — Sync queued feedback (call when online)
- `checkAndApplyRuleUpdates()` — Download and apply new rules
- `getEngineStats()` — Admin dashboard stats

**Usage Example:**
```kotlin
// Start research
val research = HybridForensicService.startDeepResearch(
    query = "Find contradictions about wire transfers to offshore accounts",
    vaultEvidenceIds = listOf("doc1", "doc2", "doc3"),
    jurisdiction = "SA",
    userContext = "Investigating AllFuels case"
)

// Poll for result
val status = HybridForensicService.getResearchStatus(research.sessionId)

// Get findings when complete
val result = HybridForensicService.getResearchResult(research.sessionId)

// Submit user's verification
HybridForensicService.submitVerificationFeedback(
    sessionId = research.sessionId,
    userApproved = true,
    corrections = emptyList(),
    comments = "Findings match our manual analysis"
)

// Sync to website when online
HybridForensicService.syncFeedbackToWebsite()

// Check for new rules
HybridForensicService.checkAndApplyRuleUpdates("SA")
```

### 4. VerificationHub (webdocsol)

**File:** `webdocsol/verification-hub.js`

**Responsibility:** Central hub for human verification and collective learning.

**Key Methods:**
- `receiveFeedbackPacket()` — Accept findings from devices
- `submitVerification()` — Record human review decision
- `triggerRuleEvolution()` — Create new rules from verified data
- `getRuleUpdatesForJurisdiction()` — Send evolved rules to devices
- `getVerificationStats()` — Track verification progress
- `trackContradictionEvolution()` — Monitor threat patterns

**Workflow:**
1. Receive contradiction packet from Android device
2. Store for human review queue
3. Expert reviews and verifies findings
4. Submits verification decision (approved/corrected)
5. If verified HIGH/VERY_HIGH, trigger rule evolution
6. Infer new rules from the verified contradiction
7. Rules are broadcast back to all devices in that jurisdiction

### 5. Feedback Processor (Firebase)

**File:** `firebase/fraud-firewall/functions/feedback-processor.ts`

**Responsibility:** Backend orchestration of the feedback loop.

**Key Functions:**
- `processFeedbackPacket()` — Receive findings from devices
- `getRuleUpdates()` — Serve evolved rules by jurisdiction
- `evolveEngineRules()` — Auto-create rules from verified data
- `trackContradictionEvolution()` — Detect threat escalation

**Triggers:**
- HTTP endpoint for feedback packets from devices
- Firestore trigger when rules are verified
- Firestore trigger when contradictions accumulate (threat escalation)

---

## Data Flow

### Forward Path (Device → Website)

```
Android Device
    ↓
[Research Query]
    ↓
[Gemma 3 Analysis]
    ↓
[Contradiction Extraction]
    ↓
[Rule Inference]
    ↓
[FeedbackPacket]
    ↓
webdocsol (HTTP POST)
    ↓
[Verification Hub]
```

### Feedback Path (Website → Device)

```
webdocsol (Human Verification)
    ↓
[Verified Decision]
    ↓
[Rule Evolution]
    ↓
Firebase Cloud Functions
    ↓
[Rule Distribution]
    ↓
Android Device (Check for Updates)
    ↓
[Apply New Rules]
    ↓
[Forensic Engine Improvement]
```

---

## Contradiction Evolution Tracking

The system monitors how contradictions evolve as criminals adapt:

```
Base Contradiction (Day 1)
└── Verified HIGH confidence
    └── Creates Base Rule
        └── R-wire-transfer-offshore-v1

Variant 1 (Day 8)
└── Similar pattern with subtle difference
    └── Verified HIGH confidence
        └── Updates Base Rule (confidence upgraded)
            └── R-wire-transfer-offshore-v1 (VERY_HIGH)

Variant 2 (Day 15)
└── Different technique, same outcome
    └── Creates Variant Rule
        └── R-wire-transfer-shell-company-v1

Threat Assessment
└── 3+ similar contradictions → ESCALATING
└── Auto-escalate to threat alert
└── Notify fraud teams across institutions
```

---

## Rule Effectiveness Tracking

Each rule tracks:
- **Total Fires** — How often rule matched evidence
- **True Positives** — Genuine fraud detected
- **False Positives** — Rule incorrectly matched innocent activity
- **Precision** — TP / (TP + FP)
- **Recommendation** — HIGHLY_EFFECTIVE / EFFECTIVE / MONITOR / DEPRECATE_CANDIDATE

Rules with precision < 0.5 are candidates for deprecation.

---

## Feedback Loop Cycle

**Complete cycle time:** 7-30 days per rule improvement

1. **Day 0** — Device detects contradiction via Gemma 3
2. **Day 0** — User approves finding; feedback sent to website
3. **Day 0-2** — Firebase receives packet
4. **Day 1-5** — Human expert verifies on webdocsol
5. **Day 5** — Verification submitted; rule evolution triggered
6. **Day 5-7** — Firebase generates and tests new rule
7. **Day 7** — Rule broadcast to all devices in jurisdiction
8. **Day 7-30** — New rule fires on other cases, proving effectiveness

**Result:** Forensic engine continuously improves with verified field data.

---

## Constitutional Compliance

All components enforce Constitution v6.0 requirements:

- **Truth over Probability** — Confidence is ordinal only (VERY_HIGH/HIGH/MODERATE/LOW)
- **Evidence before Narrative** — Every claim cites person, page, line
- **Mandatory Contradiction Disclosure** — All contradictions are flagged
- **Determinism** — No randomness, no hidden calls
- **Chain of Custody** — SHA-512 hashes, timestamps, sealed outputs

---

## Security & Privacy

- **Local Processing** — Gemma 3 runs on device; no evidence leaves device
- **Sealed Feedback** — Packets are cryptographically sealed before sending
- **Constitution Embedded** — Rules carry embedded Constitutional constraints
- **Read-Only Vault** — AI reads evidence; cannot modify or delete
- **Audit Trail** — Every action logged with timestamp and signature

---

## Integration with Nine-Brain Engine

The Gemma 3 Admin Hub works alongside (not replacing) the Nine-Brain Engine:

**Nine-Brain (B1-B9)** — Deterministic contradiction detection
- B1 — Contradiction extraction
- B2 — Document/metadata verification
- B3 — Communications analysis
- B4 — Behavioral analysis
- B5 — Timeline reconstruction
- B6 — Financial analysis
- B7 — Legal statute mapping
- B8 — Audio forensics
- B9 — Guardian validation (no voting)

**Gemma 3 Admin Hub** — Contextual research and rule evolution
- Deep research queries beyond Nine-Brain scope
- Human-guided exploration of vault evidence
- Rule inference from verified findings
- Threat escalation detection
- Engine evolution coordination

**Together:** Local deterministic engine + adaptive learning = hybrid system.

---

## Testing

Verify the hybrid engine with:

```bash
cd 1verum
./gradlew testDebugUnitTest  # Unit tests including G3AdminHub tests
```

Key test files:
- `app/src/test/java/com/verumomnis/forensic/HybridForensicServiceTest.kt`
- `app/src/test/java/com/verumomnis/forensic/G3AdminHubTest.kt`
- `app/src/test/java/com/verumomnis/forensic/WebFeedbackSyncTest.kt`

---

## Deployment

### Android App
```bash
cd 1verum
./gradlew assembleDebug
# Or release:
./gradlew bundleRelease
```

### webdocsol
```bash
cd webdocsol
# Serve verify.html and verification-hub.js
# No build step required (vanilla JS)
```

### Firebase
```bash
cd firebase/fraud-firewall/functions
npm install
firebase deploy --only functions
```

---

## Roadmap

- [ ] Quantized Gemma 3 model on-device (currently simulated)
- [ ] WebSocket real-time sync for large feedback packets
- [ ] Machine learning feedback on rule effectiveness
- [ ] Multi-jurisdiction threat correlation
- [ ] Adversarial rule testing before broadcast
- [ ] Automated threat escalation via email/SMS

---

## References

- Constitution v6.0
- Nine-Brain Engine Specification
- Verum Omnis Seal Specification
- Firebase Cloud Functions Documentation
- Gemma 3 Model Card

---

*"The truth does not require belief. It requires only that you look."*

**Verum Omnis — AI Forensics for Truth**
