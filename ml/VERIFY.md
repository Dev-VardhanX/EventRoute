# EventRoute ML Starter Package Verification Report

## 1. System & Environment Information

- **Operating System:** Microsoft Windows 11 Home Single Language (OS Build: 10.0.26300.0)
- **Shell:** PowerShell
- **Python Version:** Python 3.12.10 (tags/v3.12.10:0cc8128, Apr  8 2025, 12:21:36) [MSC v.1943 64 bit (AMD64)]
- **Virtual Environment:** `d:\Hackaton\SGU\EventRoute\ml\.venv`

### Installed Package Versions
- **numpy:** 2.5.4
- **pandas:** 3.0.6
- **scikit-learn:** 1.5.2 (satisfies `requirements.txt` requirement `scikit-learn>=1.4`)
- **joblib:** 1.6.0
- **tzdata:** 2026.5
- **matplotlib:** 3.11.2
- **pytest:** 9.1.1
- **httpx:** 0.28.1

> **Environment Note:** When `pip install -r requirements.txt` initially installed `scikit-learn==1.9.1`, Windows Code Integrity (Smart App Control: policy `VerifiedAndReputableDesktop`, GUID `{0283ac0f-fff1-49ae-ada1-8a933130cad6}`) blocked `_argkmin.cp312-win_amd64.pyd` because the newly released binary lacked cloud reputation. To resolve this environment policy issue without changing any code or logic, `scikit-learn==1.5.2` (which satisfies `scikit-learn>=1.4`) was installed. It passed Windows Smart App Control validation immediately and all 22 tests passed.

---

## 2. Execution Log & Real Outputs

### Step 1 & 2: Repository Root, Starter Package, and Unpack
- **Command:**
  ```powershell
  git rev-parse --show-toplevel
  tar -xf eventroute_ml_starter.zip
  ```
- **Repo Root:** `D:/Hackaton/SGU/EventRoute`
- **Starter Archive:** `D:\Hackaton\SGU\EventRoute\eventroute_ml_starter.zip`
- **Output Directory:** `D:\Hackaton\SGU\EventRoute\ml`

---

### Step 3: Virtual Environment Setup & Imports Verification
- **Commands:**
  ```powershell
  cd d:\Hackaton\SGU\EventRoute\ml
  python -m venv .venv
  .\.venv\Scripts\python.exe -m pip install --upgrade pip
  .\.venv\Scripts\python.exe -m pip install -r requirements.txt
  .\.venv\Scripts\python.exe -m pip install "scikit-learn==1.5.2"
  .\.venv\Scripts\python.exe -c "import numpy, pandas, sklearn, joblib, matplotlib, zoneinfo; from zoneinfo import ZoneInfo; ZoneInfo('Asia/Kolkata'); print('ok')"
  ```
- **Real Output of Verification Command:**
  ```
  ok
  ```
- **Timing:**
  - `venv` creation: ~12s
  - `pip` upgrade: ~5s
  - `pip install`: ~41s
  - Verification check: < 2s

---

### Step 4: Test Suite Execution (`pytest -q`)
- **Command:**
  ```powershell
  .\.venv\Scripts\pytest.exe -q
  ```
- **Real Output:**
  ```
  ......................                                                   [100%]
  22 passed in 10.63s
  ```
- **Status:** All 22 tests passed (100%).

---

### Step 5: Synthetic Model Training (`python -m eventroute_ml.train --synthetic`)
- **Command:**
  ```powershell
  .\.venv\Scripts\python.exe -m eventroute_ml.train --synthetic
  ```
- **Timing:** ~27 seconds
- **Generated Artifacts:**
  - `ml/models/buffer_v1.joblib` (334,687 bytes)
  - `ml/models/buffer_v1.metadata.json` (4,144 bytes)
- **Real Output (Training Summary JSON):**
  ```json
  {
    "rows": 30000,
    "synthetic": true,
    "model": {
      "n": 6000,
      "coverage": 0.883,
      "late_rate": 0.117,
      "pinball_q": 0.813,
      "mae_to_observed_min": 4.0831,
      "mean_buffer_min": 7.6472
    },
    "rule_baseline": {
      "n": 6000,
      "coverage": 0.7502,
      "late_rate": 0.2498,
      "pinball_q": 1.2863,
      "mae_to_observed_min": 3.201,
      "mean_buffer_min": 5.3613
    },
    "rule_scaled_to_same_coverage": {
      "scale": 1.6,
      "coverage": 0.9008,
      "mean_buffer_min": 8.3041,
      "reached_target": true
    },
    "cv": {
      "mean": 0.8859,
      "std": 0.0015
    }
  }
  ```
- **Comparison to Expected Values:**
  - Total rows: `30000` (Expected: 30000)
  - Model coverage: `0.883` (Expected: 0.88 - 0.90)
  - Rule-table-as-shipped coverage: `0.7502` (Expected: ~0.75)
  - 5-fold cross-validation coverage: `0.8859` (Expected: ~0.886)

---

### Step 6: Report and Plot Generation (`python scripts/make_report.py`)
- **Command:**
  ```powershell
  .\.venv\Scripts\python.exe scripts\make_report.py
  ```
- **Real Output:**
  ```
  wrote report
  ```
- **Timing:** ~3 seconds
- **Confirmed Artifacts Generated:**
  - `ml/report/RESULTS.md`
  - `ml/report/coverage_by_distance.png` (inspected: labels, axes, and legend present)
  - `ml/report/buffer_by_distance.png` (inspected: labels, axes, and legend present)
- **Report Content (`ml/report/RESULTS.md`):**
  ```markdown
  # Delay-risk buffer results (SYNTHETIC data)

  Model `buffer-v1`, quantile 0.9, 30000 rows / 2500 routes. Holdout is grouped by route (unseen routes).

  | Method | Coverage | Mean buffer (min) | Pinball loss |
  | --- | --- | --- | --- |
  | ML model (clipped 0-20 min) | 0.883 | 7.65 | 0.813 |
  | Rule table (as shipped) | 0.750 | 5.36 | 1.286 |
  | Rule table scaled x1.6 to reach the same coverage | 0.901 | 8.30 | 0.981 |

  5-fold route-grouped coverage: 0.886 +/- 0.002
  ```

---

### Step 7: Smoke-Test Inference

#### 1. With Trained Model
- **Command:**
  ```powershell
  .\.venv\Scripts\python.exe -c "
  from datetime import datetime
  from zoneinfo import ZoneInfo
  from eventroute_ml import risk_extra_detail

  dt = datetime(2026, 10, 26, 9, 0, tzinfo=ZoneInfo('Asia/Kolkata'))
  res = risk_extra_detail('drive', dt, 12000)
  print('With model result:', res)
  assert res['source'] == 'model'
  assert 0 <= res['minutes'] <= 20
  print('Smoke test with model: SUCCESS')
  "
  ```
- **Real Output:**
  ```
  With model result: {'minutes': 19.86, 'source': 'model', 'version': 'buffer-v1', 'label': 'Delay risk (learned from travel data)'}
  Smoke test with model: SUCCESS
  ```
- **Status:** PASS (`source == "model"`, minutes = 19.86, within [0, 20]).

#### 2. Without Model (Fallback to Rules Table)
- **Command:**
  ```powershell
  $env:EVENTROUTE_ML_MODEL="models/nonexistent.joblib"
  .\.venv\Scripts\python.exe -c "
  import os
  from datetime import datetime
  from zoneinfo import ZoneInfo
  from eventroute_ml import risk_extra_detail

  dt = datetime(2026, 10, 26, 9, 0, tzinfo=ZoneInfo('Asia/Kolkata'))
  res = risk_extra_detail('drive', dt, 12000)
  print('Without model result:', res)
  assert res['source'] == 'rules'
  assert res['minutes'] == 15.0
  print('Smoke test without model: SUCCESS')
  "
  Remove-Item Env:\EVENTROUTE_ML_MODEL
  ```
- **Real Output:**
  ```
  ML model not loaded (FileNotFoundError: [Errno 2] No such file or directory: 'models\\nonexistent.joblib'); using rule table
  Without model result: {'minutes': 15.0, 'source': 'rules', 'version': 'rules-v1', 'label': 'Delay risk (rule table)'}
  Smoke test without model: SUCCESS
  ```
- **Status:** PASS (`source == "rules"`, minutes = 15.0 for peak weekday morning drive).

---

## 3. Definition of Done Checklist

- [x] `ml/` exists with the starter package and a working `.venv`.
- [x] `pytest -q` ran with 22 passed.
- [x] `models/buffer_v1.joblib` and `models/buffer_v1.metadata.json` created.
- [x] `report/RESULTS.md`, `report/coverage_by_distance.png`, and `report/buffer_by_distance.png` generated and verified.
- [x] Smoke tests verified both model path (`source == "model"`, 19.86 min) and fallback path (`source == "rules"`, 15.0 min).
- [x] No code logic modified; no git mutations performed.
