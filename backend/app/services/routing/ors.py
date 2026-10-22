import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx

from app.core.config import get_settings
from app.services.routing.base import (
    NoRouteFound,
    ProviderError,
    ProviderTimeout,
    RouteProvider,
)
from app.services.routing.models import Leg, Route, Step

logger = logging.getLogger("eventroute.routing.ors")
settings = get_settings()

ORS_BASE_URL = "https://api.openrouteservice.org/v2/directions"
PROFILE_MAP = {
    "walk": "foot-walking",
    "bike": "cycling-regular",
    "drive": "driving-car",
}


def parse_ors_response(
    data: dict[str, Any],
    mode: str = "drive",
    default_origin: str = "Origin",
    default_dest: str = "Destination",
    reference_arrive_by: datetime | None = None,
) -> list[Route]:
    """Pure parser converting OpenRouteService directions JSON into common Route objects."""
    routes_json = data.get("routes", [])
    if not routes_json:
        return []

    parsed_routes: list[Route] = []
    now = datetime.now(timezone.utc)

    for idx, r_item in enumerate(routes_json):
        summary = r_item.get("summary", {})
        duration_s = int(round(summary.get("duration", 0)))
        distance_m = int(round(summary.get("distance", 0)))
        geometry = r_item.get("geometry", "")

        step_list: list[Step] = []
        segments = r_item.get("segments", [])
        for seg in segments:
            for st in seg.get("steps", []):
                st_dist = int(round(st.get("distance", 0)))
                st_dur = int(round(st.get("duration", 0)))
                instruction = st.get("instruction") or st.get("name") or "Proceed"
                step_list.append(
                    Step(
                        instruction=instruction,
                        distance_m=st_dist,
                        duration_s=st_dur,
                        mode=mode,
                    )
                )

        leg = Leg(
            mode=mode,
            from_loc=default_origin,
            to_loc=default_dest,
            duration_s=duration_s,
            distance_m=distance_m,
            polyline=geometry,
            steps=step_list,
        )

        if reference_arrive_by:
            arrival_at = reference_arrive_by
            departure_at = arrival_at - timedelta(seconds=duration_s)
        else:
            departure_at = now
            arrival_at = departure_at + timedelta(seconds=duration_s)

        leg.start_at = departure_at
        leg.end_at = arrival_at

        walking_s = duration_s if mode == "walk" else 0

        parsed_routes.append(
            Route(
                id=str(uuid.uuid4()),
                summary=f"Via {mode.capitalize()} (ORS)",
                duration_s=duration_s,
                distance_m=distance_m,
                departure_at=departure_at,
                arrival_at=arrival_at,
                fare=None,
                transfers=0,
                walking_s=walking_s,
                legs=[leg],
            )
        )

    return parsed_routes


class ORSRouteProvider(RouteProvider):
    name = "ors"

    def __init__(self, api_key: str | None = None, timeout: float = 8.0):
        self.api_key = api_key or settings.ors_api_key
        self.timeout = timeout

    def get_routes(
        self,
        origin: tuple[float, float],
        destination: tuple[float, float],
        mode: str,
        arrive_by: datetime | None = None,
        depart_at: datetime | None = None,
    ) -> list[Route]:
        if not self.api_key:
            raise ProviderError("OpenRouteService API key is not configured")

        profile = PROFILE_MAP.get(mode.lower(), "driving-car")
        url = f"{ORS_BASE_URL}/{profile}"

        # ORS expects coordinates in [lng, lat]
        payload = {
            "coordinates": [
                [origin[1], origin[0]],
                [destination[1], destination[0]],
            ],
            "instructions": True,
            "elevation": False,
        }

        headers = {
            "Authorization": self.api_key,
            "Content-Type": "application/json",
        }

        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(url, json=payload, headers=headers)
        except httpx.TimeoutException as exc:
            raise ProviderTimeout("OpenRouteService API request timed out") from exc
        except Exception as exc:
            raise ProviderError(f"OpenRouteService network error: {exc}") from exc

        if res.status_code >= 400:
            raise ProviderError(f"OpenRouteService returned {res.status_code}: {res.text}")

        data = res.json()
        routes = parse_ors_response(data, mode=mode, reference_arrive_by=arrive_by)
        if not routes:
            raise NoRouteFound("OpenRouteService found no routes")
        return routes
