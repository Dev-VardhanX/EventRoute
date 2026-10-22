# Google Routes API Cost Estimate for Driving-Delay Sampling

**Date Checked:** October 22, 2026  
**Official Pricing URLs:**
- [https://mapsplatform.google.com/pricing/](https://mapsplatform.google.com/pricing/)
- [https://developers.google.com/maps/documentation/routes/usage-and-billing](https://developers.google.com/maps/documentation/routes/usage-and-billing)

---

## 1. Parameters & Formula

The sampling plan queries pairs across future time windows to capture diurnal traffic variation:

| Parameter | Value | Details |
| :--- | :---: | :--- |
| **Origin-Destination Pairs ($N_{\text{pairs}}$)** | 20 | Selected across Delhi-NCR from `colleges.json`, `events.json`, and `journey.json`. |
| **Hours per Day ($H$)** | 17 | 06:00 to 22:00 (6 AM to 10 PM local time). |
| **Day Types ($D$)** | 2 | 1 future weekday (Tuesday) + 1 future weekend day (Saturday). |
| **Traffic Models ($M$)** | 2 | `BEST_GUESS` and `PESSIMISTIC` for each departure slot. |

$$\text{Total API Calls} = N_{\text{pairs}} \times H \times D \times M = 20 \times 17 \times 2 \times 2 = 1,360 \text{ calls}$$

---

## 2. SKU and Pricing Tier

- **Billed SKU:** `Routes: Compute Routes Pro`
  *(Triggered because requests use `travelMode: DRIVE`, `routingPreference: TRAFFIC_AWARE_OPTIMAL`, and explicit `trafficModel`).*
- **Base Rate:** **$10.00 USD per 1,000 calls** ($0.010 USD per call) for 0 – 100,000 calls/month.

---

## 3. Estimated Cost

$$\text{Estimated Total Cost} = 1,360 \times \frac{\$10.00}{1,000} = \mathbf{\$13.60\text{ USD}}\quad (\approx \text{₹1,135 INR})$$

### Monthly Free Tier & Credit Considerations
- Google Maps Platform includes a monthly free usage tier of **5,000 free events/month** for Pro SKUs (or a $200 monthly platform credit depending on account type).
- If your Google Cloud project is within its monthly free tier allowance and has no competing usage, the effective billed charge will be **$0.00 USD**.
- If free tier allowance is exhausted, the maximum charge for the planned 20 pairs will not exceed **$13.60 USD**.

---

## 4. Safety Guardrails & Budget Recommendations

1. **Hard Limit in Script:** The script includes `--max-calls 1500`. Since 1,360 calls $\le$ 1,500, it will execute cleanly while preventing accidental runaway recursion.
2. **Resumable CSV:** If interrupted or if rate-limited, re-running the script automatically skips keys `(route_id, weekday, hour)` already written in the CSV, avoiding duplicate billable calls.
3. **Google Cloud Budget Alert:** Set a budget alert of **$20.00 USD** in Google Cloud Console > Billing > Budgets & alerts before executing any live run.
