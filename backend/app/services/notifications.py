import logging
import uuid
from typing import Any

logger = logging.getLogger("eventroute.notifications")


def send_email(to: str, subject: str, body: str) -> None:
    """Send an email. For now, logs the message to support local development."""
    logger.info("EMAIL DISPATCH -> To: %s | Subject: %s | Body: %s", to, subject, body)


def queue_notification(
    user: Any,
    notification_type: str,
    event: Any = None,
    data: dict[str, Any] | None = None,
) -> None:
    """Queue an immediate or timed notification for a user."""
    user_id = getattr(user, "id", user)
    event_id = getattr(event, "id", event) if event else None
    logger.info(
        "NOTIFICATION QUEUED -> User: %s | Type: %s | Event: %s | Data: %s",
        user_id,
        notification_type,
        event_id,
        data,
    )


def queue_team_notification(
    team: Any,
    notification_type: str,
    event: Any = None,
    exclude_user_id: uuid.UUID | None = None,
) -> None:
    """Queue a notification for all members of a team, optionally excluding a sender."""
    team_id = getattr(team, "id", team)
    event_id = getattr(event, "id", event) if event else None
    logger.info(
        "TEAM NOTIFICATION QUEUED -> Team: %s | Type: %s | Event: %s | Exclude: %s",
        team_id,
        notification_type,
        event_id,
        exclude_user_id,
    )
