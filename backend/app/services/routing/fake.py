import math
import uuid
from datetime import datetime, timedelta, timezone

from app.services.routing.base import RouteProvider
from app.services.routing.models import Leg, Route, Step, TransitInfo


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlng / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


class FakeProvider(RouteProvider):
    name = "fake"

    def get_routes(
        self,
        origin: tuple[float, float],
        destination: tuple[float, float],
        mode: str,
        arrive_by: datetime | None = None,
        depart_at: datetime | None = None,
    ) -> list[Route]:
        mode = mode.lower()
        dist_km = haversine_km(origin[0], origin[1], destination[0], destination[1])
        dist_m = max(100, int(round(dist_km * 1000)))

        # Speeds in km/h: walk 5, bike 15, drive 30, transit 20
        speed_map = {"walk": 5.0, "bike": 15.0, "drive": 30.0, "transit": 20.0}
        speed_kmh = speed_map.get(mode, 25.0)

        now = datetime.now(timezone.utc)
        legs: list[Leg] = []
        dummy_poly = "gfo}D~nbvM?_ibE~nbvM"

        if mode == "transit":
            # 2 legs: walk 400m (300s) + bus for the rest
            walk_dist = min(400, dist_m // 2)
            walk_dur = int(round((walk_dist / 1000.0) / 5.0 * 3600))
            bus_dist = dist_m - walk_dist
            bus_dur = int(round((bus_dist / 1000.0) / 20.0 * 3600))
            total_dur = walk_dur + bus_dur

            if arrive_by:
                arrival_at = arrive_by
                departure_at = arrival_at - timedelta(seconds=total_dur)
            else:
                departure_at = now
                arrival_at = departure_at + timedelta(seconds=total_dur)

            walk_end = departure_at + timedelta(seconds=walk_dur)

            leg1 = Leg(
                mode="walk",
                from_loc="Start Point",
                to_loc="Bus Stop",
                start_at=departure_at,
                end_at=walk_end,
                duration_s=walk_dur,
                distance_m=walk_dist,
                polyline=dummy_poly,
                steps=[
                    Step(
                        instruction="Walk to nearest transit stop",
                        distance_m=walk_dist,
                        duration_s=walk_dur,
                        mode="walk",
                    )
                ],
            )
            leg2 = Leg(
                mode="bus",
                from_loc="Bus Stop",
                to_loc="Venue",
                start_at=walk_end,
                end_at=arrival_at,
                duration_s=bus_dur,
                distance_m=bus_dist,
                polyline=dummy_poly,
                steps=[
                    Step(
                        instruction="Board Route 502 towards Venue",
                        distance_m=bus_dist,
                        duration_s=bus_dur,
                        mode="bus",
                    )
                ],
                transit=TransitInfo(
                    line_name="Route 502",
                    short_name="502",
                    vehicle_type="BUS",
                    headsign="City Center",
                    stops_count=8,
                    agency="DTC",
                    color="#00AA00",
                ),
            )
            legs = [leg1, leg2]
            transfers = 0
            walking_s = walk_dur
            duration_s = total_dur
            fare = {"currency": "INR", "amount": 25.0}
        else:
            duration_s = max(60, int(round((dist_km / speed_kmh) * 3600)))
            if arrive_by:
                arrival_at = arrive_by
                departure_at = arrival_at - timedelta(seconds=duration_s)
            else:
                departure_at = now
                arrival_at = departure_at + timedelta(seconds=duration_s)

            leg = Leg(
                mode=mode,
                from_loc="Origin",
                to_loc="Destination",
                start_at=departure_at,
                end_at=arrival_at,
                duration_s=duration_s,
                distance_m=dist_m,
                polyline=dummy_poly,
                steps=[
                    Step(
                        instruction=f"Proceed via {mode}",
                        distance_m=dist_m,
                        duration_s=duration_s,
                        mode=mode,
                    )
                ],
            )
            legs = [leg]
            transfers = 0
            walking_s = duration_s if mode == "walk" else 0
            fare = None

        return [
            Route(
                id=str(uuid.uuid4()),
                summary=f"Via {mode.capitalize()} (Simulated)",
                duration_s=duration_s,
                distance_m=dist_m,
                departure_at=departure_at,
                arrival_at=arrival_at,
                fare=fare,
                transfers=transfers,
                walking_s=walking_s,
                legs=legs,
            )
        ]
