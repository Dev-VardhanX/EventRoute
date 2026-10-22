# Google Routes API Verification Notes

**Date Verified:** October 22, 2026  
**Target Endpoint:** `computeRoutes` (REST API v2)  
**Official Documentation URLs:**
- Overview & REST Guide: [https://developers.google.com/maps/documentation/routes/compute_routes_overview](https://developers.google.com/maps/documentation/routes/compute_routes_overview)
- REST Reference (`computeRoutes`): [https://developers.google.com/maps/documentation/routes/reference/rest/v2/TopLevel/computeRoutes](https://developers.google.com/maps/documentation/routes/reference/rest/v2/TopLevel/computeRoutes)
- Usage & Billing: [https://developers.google.com/maps/documentation/routes/usage-and-billing](https://developers.google.com/maps/documentation/routes/usage-and-billing)
- Google Maps Platform Pricing: [https://mapsplatform.google.com/pricing/](https://mapsplatform.google.com/pricing/)

---

## 1. Verified API Specifications

### 1.1 Endpoint & Method
- **HTTP Method:** `POST`
- **URL:** `https://routes.googleapis.com/directions/v2:computeRoutes`

### 1.2 Required Headers
1. `X-Goog-Api-Key`: The Google Cloud API key with Routes API enabled.
2. `X-Goog-FieldMask`: **Mandatory**. The Routes API requires a field mask on every request; requests without this header fail with an `INVALID_ARGUMENT` (400) error.
   - For our data collection: `routes.duration,routes.staticDuration,routes.distanceMeters`
3. `Content-Type`: `application/json`

### 1.3 Request Body Parameters
- **`origin` & `destination`:**
  ```json
  {
    "location": {
      "latLng": {
        "latitude": 28.5459,
        "longitude": 77.1926
      }
    }
  }
  ```
- **`travelMode`:** `DRIVE` (Enum: `DRIVE`, `BICYCLE`, `WALK`, `TWO_WHEELER`, `TRANSIT`).
- **`routingPreference`:** `TRAFFIC_AWARE_OPTIMAL`
  - Allowed values: `TRAFFIC_UNAWARE`, `TRAFFIC_AWARE`, `TRAFFIC_AWARE_OPTIMAL`.
  - **Prerequisite:** In Google Routes API, the `trafficModel` parameter can **only** be specified when `routingPreference` is set to `TRAFFIC_AWARE_OPTIMAL` and `travelMode` is `DRIVE` (or `TWO_WHEELER`).
- **`trafficModel`:**
  - Allowed values: `BEST_GUESS` (default), `PESSIMISTIC`, `OPTIMISTIC`.
  - In `sample_routes_api.py`, each origin-destination departure is queried twice: once with `BEST_GUESS` and once with `PESSIMISTIC` to capture the extra margin a cautious traveler should allow.
- **`departureTime`:**
  - Must be formatted as an RFC 3339 UTC string with trailing `Z` (e.g. `2026-10-27T03:30:00Z`).
  - For `DRIVE` mode with traffic predictions, `departureTime` **must be in the future**. The script generates departure hours on the upcoming Tuesday and Saturday, strictly guaranteed to be in the future.

### 1.4 Response Payload & Field Formats
- Response structure:
  ```json
  {
    "routes": [
      {
        "distanceMeters": 23500,
        "duration": "2700s",
        "staticDuration": "2100s"
      }
    ]
  }
  ```
- **`duration`:** String representation of seconds ending in `"s"` (e.g. `"2700s"`). Parsed using `float(s.rstrip("s"))`.
- **`distanceMeters`:** Integer meters (e.g. `23500`).

---

## 2. SKU and Billing Tier Trigger
- Setting `routingPreference: TRAFFIC_AWARE_OPTIMAL` triggers the **Routes: Compute Routes Pro** (formerly Compute Routes Advanced) SKU tier.
- Billed at **$10.00 USD per 1,000 calls** after the initial monthly free tier cap (5,000 Pro requests/month).
