# Data card

| Source | Label in `source` column | What it is | Honest limits |
| --- | --- | --- | --- |
| `eventroute_ml/simulate.py` | `synthetic` | Generated trips. Delay = planned time x (mode base + peak fraction) x lognormal noise, plus missed-transfer waits. All parameters are **assumptions** written in the file. | Not measured. It only proves the pipeline works. It encodes the same structure the model then learns, so do **not** present its metrics as real-world accuracy. |
| `scripts/sample_routes_api.py` | `real_api` | Driving only. `target_min = pessimistic - best_guess` traffic duration from the routing API, per origin/destination pair and hour. | A **proxy** for "extra time to allow", not measured lateness. Costs money; has a hard `--max-calls` limit. |
| App live journeys (`travel_logs`) | `travel_log` | Planned vs actual arrival from real trips. | Needs real users. Best data once it exists. |

## Target
`target_min` = (actual - planned duration) in minutes (may be slightly negative). The model predicts its 0.9 quantile; output is clamped to 0..20 minutes.

## Columns (training CSV)
`route_id, mode, hour, weekday, distance_m, transfers, walking_share, target_min, source`
Hour and weekday are in the event's local time zone (Asia/Kolkata). Weekday: Monday = 0.

## Evaluation rules
- Split **by route_id** (never by random row).
- Report coverage (target about 0.90), pinball loss, mean buffer minutes, and coverage by trip length.
- Compare against the rule table **scaled to the same coverage**, not the raw rule table.
- Report metrics per `source`; never mix synthetic and real numbers in one headline figure.
