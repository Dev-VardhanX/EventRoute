# Candidate Model buffer-v3 Rejection Report (Pipeline Self-Test)

**Evaluation Timestamp:** `2026-10-22T08:26:31.335829+00:00`  
**Candidate Version:** `buffer-v3`  
**Current Active Model:** `buffer-v1`  
**Evaluation Nature:** Pipeline self-test on 350 FAKE trips to verify promotion gate rejection.

## Promotion Criteria & Results

| Rule | Requirement | Candidate Value | Current Value | Status |
| :--- | :--- | :---: | :---: | :---: |
| **1. Coverage Window** | $0.85 \le \text{Coverage} \le 0.95$ | **0.5000** | 0.5000 | FAIL |
| **2. Pinball Loss Gate** | $\le 74.0752$ (+2% max) | **66.8143** | 72.6227 | PASS |

## Holdout Metrics Breakdown

- **Clean Training Rows:** 280
- **Newest-Trips Holdout Rows:** 70
- **Candidate Mean Buffer:** 13.14 min
- **Current Mean Buffer:** 7.29 min

## Decision

**REJECTED.** The candidate model failed to satisfy the required promotion gate.
The current active model remains active in `models/registry.json`. No existing models were overwritten.
This was a pipeline self-test on 350 FAKE trips that shows our promotion gate rejects a bad candidate; we have no real student logs yet.
