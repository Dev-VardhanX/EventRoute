from abc import ABC, abstractmethod
from datetime import datetime

from app.services.routing.models import Route


class RoutingException(Exception):
    """Base exception for routing operations."""
    pass


class ProviderTimeout(RoutingException):
    """Raised when an external route provider times out."""
    pass


class ProviderError(RoutingException):
    """Raised when an external route provider returns a non-transient error."""
    pass


class NoRouteFound(RoutingException):
    """Raised when a route provider finds no viable paths between the points."""
    pass


class RouteProvider(ABC):
    name: str

    @abstractmethod
    def get_routes(
        self,
        origin: tuple[float, float],
        destination: tuple[float, float],
        mode: str,
        arrive_by: datetime | None = None,
        depart_at: datetime | None = None,
    ) -> list[Route]:
        """Fetch and convert routes from provider to common format."""
        pass
