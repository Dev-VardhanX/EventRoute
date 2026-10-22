import logging

logger = logging.getLogger("eventroute.notifications")


def send_email(to: str, subject: str, body: str) -> None:
    """Send an email. For now, logs the message to support local development."""
    logger.info("EMAIL DISPATCH -> To: %s | Subject: %s | Body: %s", to, subject, body)
