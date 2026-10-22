# EventRoute ML Audit & Spec Alignment Report

**Date:** October 22, 2026  
**Scope:** `eventroute_ml/recommend.py` and `docs/BACKEND.md` (Section 8.8)

---

## 1. Specification Alignment (docs/BACKEND.md vs Implementation)

In `docs/BACKEND.md` (Section 8.8):
> **Recommendations:** `0.40 interest_match + 0.25 proximity + 0.20 popularity + 0.15 urgency`, with a human-readable reason

| Dimension | BACKEND.md Specification | Implementation (`eventroute_ml/recommend.py`) | Status | Notes / Differences |
| :--- | :--- | :--- | :---: | :--- |
| **Weights** | `0.40 interest + 0.25 prox + 0.20 pop + 0.15 urg` | Configured as default `REC_WEIGHTS` (configurable via `weights=` parameter) | **Aligned** | Weights sum to 1.0. |
| **Interest Match** | Matches user interests with human-readable reason | Canonicalizes tags/categories, expands synonyms (AI/ML, robotics, hackathons), scores category (0.7) and tags (0.3). Cold start returns neutral 0.5. | **Aligned** | Case/punctuation insensitive. |
| **Proximity** | Travel time / distance | Prefers routing `travel_minutes`; falls back to Haversine distance / 22 km/h. Scores 1.0 at 0 min down to 0.0 at $\ge$ 90 min. Neutral 0.5 when coordinates missing. | **Aligned** | Robust against `NaN` coordinates. |
| **Popularity** | Capacity fill / registrations count | Fill ratio $\text{reg} / \text{cap}$ if capacity known; log-scaled $\log(1+\text{reg})/\log(1+200)$ otherwise. | **Aligned** | Handles 0 registrations cleanly. |
| **Urgency** | Time to registration deadline | 1.0 if $\le 24$h, 0.0 if $\ge 7$ days (168h), linear interpolation in-between. | **Aligned** | Evaluated relative to `now`. |
| **Registration Filters** | Exclude already registered, full, or closed events (Section 8.1) | `cancelled`, `registered`, `full`, `registration_deadline < now`, and `start_at < now` are filtered out. | **Aligned** | Configurable via `exclude_registered` and `exclude_full`. |
| **Reason String** | Human-readable reason | Explains top 2 contributing components (e.g. "Matches your #Hackathons interest · Registration closes within a day"). | **Hardened** | Enforced $\le 70$ characters and non-empty guarantee. |
| **Tie-Breaking** | Deterministic ordering | Sorted by `(-score, start_at, event_id)` | **Hardened** | Guarantees strictly deterministic ordering and duplicate ID deduplication. |

---

## 2. Hardened Edge Cases Documented

1. **Missing or Corrupted Coordinates:** If user or event coordinates are `None`, `NaN`, or infinite, proximity cleanly returns `(0.5, None)` without throwing exceptions.
2. **Missing Deadlines:** If `registration_deadline` is `None`, urgency evaluates to `(0.0, None)` and does not prematurely filter out the event unless `start_at` has passed.
3. **Punctuation & Casing in Interests:** Inputs like `"#AI"`, `" ai "`, `"#Hackathons!"`, and `"Open Mic"` are stripped and canonicalized properly.
4. **Reason String Truncation:** Reason strings are strictly bounded to at most 70 characters and never mention missing information (e.g. no travel time if minutes is None).
5. **Deduplication:** Duplicate event entries in the candidate list are filtered so only the first occurrence is scored and ranked.
