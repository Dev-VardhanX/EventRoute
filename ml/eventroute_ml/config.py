"""Single place for every constant the ML code depends on."""
from pathlib import Path

MODEL_VERSION = "buffer-v1"
RISK_MIN_MIN = 0.0          # risk_extra is clamped to 0..20 minutes (BACKEND.md section 8.8)
RISK_MAX_MIN = 20.0
QUANTILE = 0.9              # we predict the 90th percentile of delay
DEFAULT_TZ = "Asia/Kolkata"

# Leg modes in the common route format
MODES = ("walk", "bike", "bus", "metro", "train", "tram", "drive")

PACKAGE_DIR = Path(__file__).resolve().parent
ML_ROOT = PACKAGE_DIR.parent
DEFAULT_MODEL_PATH = ML_ROOT / "models" / "buffer_v1.joblib"

# Recommendation weights (BACKEND.md section 8.8). Must sum to 1.0.
REC_WEIGHTS = {"interest": 0.40, "proximity": 0.25, "popularity": 0.20, "urgency": 0.15}
