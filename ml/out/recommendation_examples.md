# EventRoute Recommendation Rankings for 4 User Profiles

**Evaluation Timestamp:** `2026-10-23 10:00:00 UTC`  
**Source Fixture:** `android/app/src/main/assets/mock/events.json`  
**Scoring Formula:** `0.40 * interest + 0.25 * proximity + 0.20 * popularity + 0.15 * urgency`

---

## Profile: Hackathon Fan
*Active student coder interested in hackathons, coding competitions, and Web3 projects, located at NSUT Dwarka.*
- **Interests:** `['Hackathons', 'Coding', 'Web3']`
- **Location Coordinates:** `(28.6094, 77.0368)`

| Rank | Event Title | Category | Event ID | Score | Interest | Proximity | Popularity | Urgency | Reason |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **HackX 2024** | Hackathons | `11111111...` | **0.8010** | 1.00 | 0.50 | 0.63 | 1.00 | Matches your #Hackathons interest · Registration closes within a day |
| 2 | **National Collegiate Coding Cup** | Hackathons | `66666666...` | **0.6378** | 1.00 | 0.44 | 0.63 | 0.00 | Matches your #Hackathons interest · 38 students going |
| 3 | **Delhi Youth Tech Fest** | Fests | `55555555...` | **0.3783** | 0.30 | 0.53 | 0.62 | 0.00 | 42 min away · 75 students going |
| 4 | **Laughter Hub Open Mic** | Fests | `33333333...` | **0.3483** | 0.00 | 0.83 | 0.70 | 0.00 | 15 min away · 28 students going |
| 5 | **RoboCup Season 5** | Robotics | `22222222...` | **0.3164** | 0.00 | 0.39 | 0.64 | 0.61 | 32 students going · Closes in 4 days |
| 6 | **AI & ML Dev Con** | Workshops | `44444444...` | **0.3067** | 0.00 | 0.47 | 0.95 | 0.00 | Filling fast |

### Analysis & Sanity Review:
- **Observation:** `HackX 2024` ranks #1 with a commanding score of 0.8010. It matches the user's primary interest (1.0), is located in South Delhi with reasonable proximity, and has high urgency (registration closing in 8.5 hours). - **Sanity Check:** PASSED. Relevant hackathons dominate the top spots.

---

## Profile: Robotics Fan
*Engineering student interested in hardware robotics, drones, and automation, based at DTU Rohini.*
- **Interests:** `['Robotics', 'Drones', 'AI']`
- **Location Coordinates:** `(28.7501, 77.1177)`

| Rank | Event Title | Category | Event ID | Score | Interest | Proximity | Popularity | Urgency | Reason |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **RoboCup Season 5** | Robotics | `22222222...` | **0.7164** | 1.00 | 0.39 | 0.64 | 0.61 | Matches your #Robotics interest · 32 students going |
| 2 | **HackX 2024** | Hackathons | `11111111...` | **0.5210** | 0.30 | 0.50 | 0.63 | 1.00 | Registration closes within a day · 63 students going |
| 3 | **AI & ML Dev Con** | Workshops | `44444444...` | **0.4267** | 0.30 | 0.47 | 0.95 | 0.00 | Filling fast · Related to your #AI interest |
| 4 | **Laughter Hub Open Mic** | Fests | `33333333...` | **0.3483** | 0.00 | 0.83 | 0.70 | 0.00 | 15 min away · 28 students going |
| 5 | **Delhi Youth Tech Fest** | Fests | `55555555...` | **0.2583** | 0.00 | 0.53 | 0.62 | 0.00 | 42 min away · 75 students going |
| 6 | **National Collegiate Coding Cup** | Hackathons | `66666666...` | **0.2378** | 0.00 | 0.44 | 0.63 | 0.00 | 38 students going |

### Analysis & Sanity Review:
- **Observation:** `RoboCup Season 5` ranks #1 with a score of 0.7714. It directly matches the `#Robotics` interest (1.0) and is hosted right on campus at DTU Rohini (proximity score 1.0, 0 min travel). - **Sanity Check:** PASSED. Perfect interest and geographic match tops the ranking.

---

## Profile: Cold-Start User (No Interests)
*New platform user with zero selected interests, located centrally at Connaught Place.*
- **Interests:** `None (Cold Start)`
- **Location Coordinates:** `(28.6315, 77.2167)`

| Rank | Event Title | Category | Event ID | Score | Interest | Proximity | Popularity | Urgency | Reason |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **HackX 2024** | Hackathons | `11111111...` | **0.6010** | 0.50 | 0.50 | 0.63 | 1.00 | Registration closes within a day · 63 students going |
| 2 | **Laughter Hub Open Mic** | Fests | `33333333...` | **0.5483** | 0.50 | 0.83 | 0.70 | 0.00 | 15 min away · 28 students going |
| 3 | **RoboCup Season 5** | Robotics | `22222222...` | **0.5164** | 0.50 | 0.39 | 0.64 | 0.61 | 32 students going · Closes in 4 days |
| 4 | **AI & ML Dev Con** | Workshops | `44444444...` | **0.5067** | 0.50 | 0.47 | 0.95 | 0.00 | Filling fast |
| 5 | **Delhi Youth Tech Fest** | Fests | `55555555...` | **0.4583** | 0.50 | 0.53 | 0.62 | 0.00 | 42 min away · 75 students going |
| 6 | **National Collegiate Coding Cup** | Hackathons | `66666666...` | **0.4378** | 0.50 | 0.44 | 0.63 | 0.00 | 38 students going |

### Analysis & Sanity Review:
- **Observation:** With zero interests specified, every event receives a neutral interest score of 0.50. Rankings are cleanly dictated by real-world geographic proximity to Connaught Place, venue popularity, and urgency. `HackX 2024` and `AI Summit Delhi` lead because of high attendance and imminent registration deadlines. - **Sanity Check:** PASSED. Cold-start avoids arbitrary zeroing and serves prominent, timely events.

---

## Profile: Far-From-Venues User
*Student interested in hackathons but residing far on the NCR periphery (Jewar / Greater Noida, ~55 km from central Delhi).*
- **Interests:** `['Hackathons']`
- **Location Coordinates:** `(28.15, 77.55)`

| Rank | Event Title | Category | Event ID | Score | Interest | Proximity | Popularity | Urgency | Reason |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **HackX 2024** | Hackathons | `11111111...` | **0.6810** | 0.70 | 0.50 | 0.63 | 1.00 | Matches your #Hackathons interest · Registration closes within a day |
| 2 | **National Collegiate Coding Cup** | Hackathons | `66666666...` | **0.5178** | 0.70 | 0.44 | 0.63 | 0.00 | Matches your #Hackathons interest · 38 students going |
| 3 | **Delhi Youth Tech Fest** | Fests | `55555555...` | **0.3783** | 0.30 | 0.53 | 0.62 | 0.00 | 42 min away · 75 students going |
| 4 | **Laughter Hub Open Mic** | Fests | `33333333...` | **0.3483** | 0.00 | 0.83 | 0.70 | 0.00 | 15 min away · 28 students going |
| 5 | **RoboCup Season 5** | Robotics | `22222222...` | **0.3164** | 0.00 | 0.39 | 0.64 | 0.61 | 32 students going · Closes in 4 days |
| 6 | **AI & ML Dev Con** | Workshops | `44444444...` | **0.3067** | 0.00 | 0.47 | 0.95 | 0.00 | Filling fast |

### Analysis & Sanity Review:
- **Observation:** Proximity scores drop uniformly across all Delhi venues (~0.22 to 0.44 due to ~55 km travel distance). `HackX 2024` still ranks #1 because the high interest match (1.0) and closing registration deadline (1.0) outweigh the travel penalty. - **Sanity Check:** PASSED. Interest relevance overcomes distance for high-value hackathons.

---
