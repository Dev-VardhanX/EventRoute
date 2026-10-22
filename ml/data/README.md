# EventRoute Origin-Destination (OD) Pairs Dataset

This directory contains the origin-destination pairs used to collect driving-delay proxy data from Google's Routes API.

---

## 1. How the Pairs Were Chosen

The pairs were derived directly from the real mock coordinates specified in the application:
- **Participating Colleges (`colleges.json`):**
  - IIT Delhi (Hauz Khas): `28.5459, 77.1926`
  - DTU (Rohini): `28.7501, 77.1177`
  - NSUT (Dwarka): `28.6094, 77.0368`
  - IIIT Delhi (Okhla): `28.5457, 77.2732`
  - IGDTUW (Kashmere Gate): `28.6652, 77.2324`
- **Key Transit Hubs & Junctions (`journey.json`):**
  - Hauz Khas Metro: `28.5520, 77.1910`
  - Dwarka Mor: `28.6192, 77.0331`
  - Rajouri Garden: `28.6492, 77.1226`
  - Dhaula Kuan: `28.5912, 77.1610`
- **Major Event Venues & Central Hubs (`events.json`):**
  - Pragati Maidan (Convention Hall): `28.6186, 77.2435`
  - Connaught Place (City Center): `28.6315, 77.2167`

---

## 2. Distance Distribution

The 20 pairs provide a realistic cross-section of student travel journeys across Delhi-NCR:

| Distance Tier | Count | Examples | Purpose |
| :--- | :---: | :--- | :--- |
| **Short (< 5 km)** | 6 | `IITD <-> Hauz Khas` (0.7 km), `NSUT <-> Dwarka Mor` (1.1 km), `Pragati Maidan -> Connaught Place` (3.0 km), `IGDTUW -> Connaught Place` (4.1 km) | Captures last-mile trips, short transfers, and localized traffic delays. |
| **Medium (5 – 15 km)** | 7 | `IGDTUW -> Pragati Maidan` (5.3 km), `IITD -> Dhaula Kuan` (5.9 km), `IITD -> IIITD` (7.9 km), `IIITD -> Pragati Maidan` (8.6 km), `NSUT -> Rajouri Garden` (9.5 km), `DTU -> Rajouri Garden` (11.2 km) | Represents intra-city transit and typical student commutes between campuses and venues. |
| **Long (> 15 km)** | 7 | `DTU -> Connaught Place` (16.4 km), `IITD -> NSUT` (16.8 km), `DTU -> NSUT` (17.5 km), `IIITD -> Rajouri Garden` (18.7 km), `DTU -> Pragati Maidan` (19.1 km), `IITD -> DTU` (23.9 km), `NSUT -> IIITD` (24.2 km) | Models major cross-city arterial routes (Ring Road, Outer Ring Road, NH-48) with high variance in traffic congestion. |

---

## 3. File Schema (`ml/data/od_pairs.csv`)

```csv
route_id,origin_lat,origin_lng,dest_lat,dest_lng
route_01_iitd_hauzkhas,28.5459,77.1926,28.5520,77.1910
...
```

- `route_id`: Unique string identifier for the OD pair.
- `origin_lat`, `origin_lng`: Latitude and longitude of origin in decimal degrees.
- `dest_lat`, `dest_lng`: Latitude and longitude of destination in decimal degrees.
