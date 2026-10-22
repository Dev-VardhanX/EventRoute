# EventRoute Starter Website

A responsive, editable frontend starter based on the supplied 13-page EventRoute UI reference.
Current build: landing page (PDF page 1) with claymorphism/neumorphic cards, Quicksand font, responsive layout, event filters/search, and fictional demo events.

## Run it
1. Extract the ZIP.
2. Open the `EventRoute_Starter` folder in VS Code.
3. Install the VS Code extension **Live Server**.
4. Right-click `index.html` → **Open with Live Server**.

Alternative: from the folder containing `index.html`, run `python -m http.server 5500`, then open `http://localhost:5500`.

## Edit the demo content
Open `js/app.js` and edit the `demoEvents` array. All sample event names, dates, fees, and travel times are fictional demo content.

## Design
- Font: Quicksand (Google Fonts; internet needed to fetch the font).
- Claymorphism-inspired soft raised cards, rounded corners, pastel purple/pink/mint palette.
- The hero illustration is made with CSS shapes, not the original source artwork from the PDF. No separate source image assets were included in the supplied PDF export, so the layout uses a matching editable illustration.
- This starter implements the landing page first. Login, event detail, team, routing, organizer and admin screens remain to be added.

## Important
This is a frontend demo only. Authentication, registration, live maps, route estimates, QR scanning, database and backend API are not connected.


## Enhanced edition
- Added soft claymorphism shadows and animated hover states.
- Added floating hero cards, bobbing decorative artwork, subtle button shine, and scroll reveal.
- Added `assets/reference/reference-page-1.png` as a visual reference rendered from the supplied PDF.
- Extracted 42 embedded image asset(s) from the supplied PDF into `assets/reference/` when present.
- The PDF page reference is included so you can compare the UI while editing. The current landing page uses its own editable CSS illustration; extracted PDF assets are available as source assets where the PDF embedded them.


## Login page (PDF page 2)
Open `login.html` to view the login UI. The landing page Login and Sign Up buttons link to it. The student mascot image extracted from the supplied PDF is reused from `assets/reference/pdf-image-13.jpeg`. Login/signup, password reset, Google, and Apple controls are visual demos only; real authentication needs a backend/provider.
\n\n## Page 3: Discover Map\nOpen `discover-map.html` or click Discover from the landing page. The page uses the original Page 3 PDF screenshot as the map backdrop, with responsive event cards, demo filters/search, map pins, and soft floating animations. The map/location controls are demo UI, not live maps or GPS.\n

Page 4: open `event-details.html`, or click the first robotics event on Discover. Registration, route, share, and save are demo interactions and do not connect to a backend.
