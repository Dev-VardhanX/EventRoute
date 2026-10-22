import logging
import uuid
from datetime import datetime, timezone
from typing import Any

import httpx

from app.core.config import get_settings
from app.services.routing.base import (
    NoRouteFound,
    ProviderError,
    ProviderTimeout,
    RouteProvider,
)
from app.services.routing.models import Leg, Route, Step, TransitInfo

logger = logging.getLogger("eventroute.routing.google")
settings = get_settings()

GOOGLE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
GOOGLE_FIELD_MASK = (
    "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,"
    "routes.legs,routes.description,routes.travelAdvisory.transitFare"
)


def parse_google_response(
    data: dict[str, Any],
    mode: str = "transit",
    default_origin: str = "Origin",
    default_dest: str = "Destination",
    reference_arrive_by: datetime | None = None,
) -> list[Route]:
    """Pure parser converting Google Routes API JSON into common Route objects."""
    routes_json = data.get("routes", [])
    if not routes_json:
        return []

    parsed_routes: list[Route] = []
    now = datetime.now(timezone.utc)

    for idx, r_item in enumerate(routes_json):
        duration_str = r_item.get("duration", "0s")
        duration_s = int(duration_str.rstrip("s")) if duration_str else 0
        distance_m = int(r_item.get("distanceMeters", 0))
        summary = r_item.get("description") or f"Route {idx + 1}"

        # Fare advisory
        fare = None
        travel_advisory = r_item.get("travelAdvisory") or {}
        transit_fare = travel_advisory.get("transitFare")
        if transit_fare:
            fare = {
                "currency": transit_fare.get("currencyCode", "INR"),
                "amount": float(transit_fare.get("units", 0))
                + (transit_fare.get("nanos", 0) / 1e9),
            }

        google_legs = r_item.get("legs", [])
        legs: list[Leg] = []

        is_transit_mode = mode == "transit" or any(
            any(s.get("travelMode") == "TRANSIT" for s in g_leg.get("steps", []))
            for g_leg in google_legs
        )

        first_leg_dep: datetime | None = None
        last_leg_arr: datetime | None = None

        if is_transit_mode and google_legs:
            # Multi-modal transit steps become individual legs in common model
            for g_leg in google_legs:
                for step in g_leg.get("steps", []):
                    step_mode = step.get("travelMode", "WALK")
                    step_dur_str = step.get("staticDuration", "0s")
                    step_dur = int(step_dur_str.rstrip("s")) if step_dur_str else 0
                    step_dist = int(step.get("distanceMeters", 0))
                    step_poly = step.get("polyline", {}).get("encodedPolyline", "")
                    instruction = step.get("navigationInstruction", {}).get(
                        "instructions", ""
                    )

                    if step_mode == "TRANSIT":
                        td = step.get("transitDetails", {})
                        sd = td.get("stopDetails", {})
                        tl = td.get("transitLine", {})
                        v = tl.get("vehicle", {})
                        v_type = v.get("type", "BUS").upper()

                        if v_type in ("SUBWAY", "METRO"):
                            common_mode = "metro"
                        elif v_type == "TRAIN":
                            common_mode = "train"
                        elif v_type == "TRAM":
                            common_mode = "tram"
                        else:
                            common_mode = "bus"

                        agencies = tl.get("agencies", [{}])
                        agency_name = agencies[0].get("name") if agencies else None

                        transit_info = TransitInfo(
                            line_name=tl.get("name"),
                            short_name=tl.get("nameShort"),
                            vehicle_type=v_type,
                            headsign=td.get("headsign"),
                            stops_count=int(td.get("stopCount", 0)),
                            agency=agency_name,
                            color=tl.get("color"),
                        )

                        dep_raw = sd.get("departureTime")
                        arr_raw = sd.get("arrivalTime")
                        dep_dt = (
                            datetime.fromisoformat(dep_raw.replace("Z", "+00:00"))
                            if dep_raw
                            else None
                        )
                        arr_dt = (
                            datetime.fromisoformat(arr_raw.replace("Z", "+00:00"))
                            if arr_raw
                            else None
                        )

                        if dep_dt and not first_leg_dep:
                            first_leg_dep = dep_dt
                        if arr_dt:
                            last_leg_arr = arr_dt

                        from_name = sd.get("departureStop", {}).get(
                            "name", default_origin
                        )
                        to_name = sd.get("arrivalStop", {}).get("name", default_dest)

                        legs.append(
                            Leg(
                                mode=common_mode,
                                from_loc=from_name,
                                to_loc=to_name,
                                start_at=dep_dt,
                                end_at=arr_dt,
                                duration_s=step_dur,
                                distance_m=step_dist,
                                polyline=step_poly,
                                steps=[
                                    Step(
                                        instruction=instruction
                                        or f"Take {tl.get('name', 'transit')}",
                                        distance_m=step_dist,
                                        duration_s=step_dur,
                                        mode=common_mode,
                                    )
                                ],
                                transit=transit_info,
                            )
                        )
                    else:
                        legs.append(
                            Leg(
                                mode="walk",
                                from_loc=default_origin,
                                to_loc=default_dest,
                                duration_s=step_dur,
                                distance_m=step_dist,
                                polyline=step_poly,
                                steps=[
                                    Step(
                                        instruction=instruction or "Walk",
                                        distance_m=step_dist,
                                        duration_s=step_dur,
                                        mode="walk",
                                    )
                                ],
                            )
                        )
        else:
            # Single or driving/walking/cycling mode
            for g_leg in google_legs:
                step_list: list[Step] = []
                for s in g_leg.get("steps", []):
                    s_dur_str = s.get("staticDuration", "0s")
                    s_dur = int(s_dur_str.rstrip("s")) if s_dur_str else 0
                    s_dist = int(s.get("distanceMeters", 0))
                    step_list.append(
                        Step(
                            instruction=s.get("navigationInstruction", {}).get(
                                "instructions", ""
                            ),
                            distance_m=s_dist,
                            duration_s=s_dur,
                            mode=mode,
                        )
                    )

                poly = g_leg.get("polyline", {}).get("encodedPolyline") or r_item.get(
                    "polyline", {}
                ).get("encodedPolyline", "")
                legs.append(
                    Leg(
                        mode=mode,
                        from_loc=default_origin,
                        to_loc=default_dest,
                        duration_s=duration_s,
                        distance_m=distance_m,
                        polyline=poly,
                        steps=step_list,
                    )
                )

        # Compute departure and arrival times
        if reference_arrive_by:
            arrival_at = last_leg_arr or reference_arrive_by
            departure_at = first_leg_dep or (
                arrival_at - httpx._types.timedelta(seconds=duration_s)
                if hasattr(httpx, "_types")
                else arrival_at - (datetime.fromtimestamp(duration_s, timezone.utc) - datetime.fromtimestamp(0, timezone.utc))
            )
        else:
            departure_at = first_leg_dep or now
            arrival_at = last_leg_arr or (
                departure_at
                + (
                    datetime.fromtimestamp(duration_s, timezone.utc)
                    - datetime.fromtimestamp(0, timezone.utc)
                )
            )

        transit_legs = [l for l in legs if l.transit is not None]
        transfers = max(0, len(transit_legs) - 1)
        walking_s = sum(l.duration_s for l in legs if l.mode == "walk")

        parsed_routes.append(
            Route(
                id=str(uuid.uuid4()),
                summary=summary,
                duration_s=duration_s,
                distance_m=distance_m,
                departure_at=departure_at,
                arrival_at=arrival_at,
                fare=fare,
                transfers=transfers,
                walking_s=walking_s,
                legs=legs,
            )
        )

    return parsed_routes


class GoogleRouteProvider(RouteProvider):
    name = "google"

    def __init__(self, api_key: str | None = None, timeout: float = 8.0):
        self.api_key = api_key or settings.google_maps_server_key
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
            raise ProviderError("Google Maps API key is not configured")

        mode_map = {
            "transit": "TRANSIT",
            "walk": "WALK",
            "bike": "BICYCLE",
            "drive": "DRIVE",
        }
        travel_mode = mode_map.get(mode.lower(), "TRANSIT")

        payload: dict[str, Any] = {
            "origin": {
                "location": {
                    "latLng": {"latitude": origin[0], "longitude": origin[1]}
                }
            },
            "destination": {
                "location": {
                    "latLng": {
                        "latitude": destination[0],
                        "longitude": destination[1],
                    }
                }
            },
            "travelMode": travel_mode,
            "computeAlternativeRoutes": True,
        }

        if travel_mode == "TRANSIT":
            if arrive_by:
                payload["arrivalTime"] = (
                    arrive_by.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                )
            elif depart_at:
                payload["departureTime"] = (
                    depart_at.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                )

        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": self.api_key,
            "X-Goog-FieldMask": GOOGLE_FIELD_MASK,
        }

        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(GOOGLE_ROUTES_URL, json=payload, headers=headers)
        except httpx.TimeoutException as exc:
            raise ProviderTimeout("Google Routes API request timed out") from exc
        except Exception as exc:
            raise ProviderError(f"Google Routes network error: {exc}") from exc

        if res.status_code >= 400:
            raise ProviderError(f"Google Routes API returned {res.status_code}: {res.text}")

        data = res.json()
        routes = parse_google_response(data, mode=mode, reference_arrive_by=arrive_by)
        if not routes:
            raise NoRouteFound("Google Routes API found no routes")
        return routes
