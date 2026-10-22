import enum


class UserRole(str, enum.Enum):
    STUDENT = "student"
    ORGANIZER = "organizer"
    ADMIN = "admin"


class OrganizerStatus(str, enum.Enum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"


class EventStatus(str, enum.Enum):
    DRAFT = "draft"
    PENDING_REVIEW = "pending_review"
    PUBLISHED = "published"
    CANCELLED = "cancelled"
    COMPLETED = "completed"


class EventMode(str, enum.Enum):
    OFFLINE = "offline"
    ONLINE = "online"
    HYBRID = "hybrid"


class ParticipationType(str, enum.Enum):
    SOLO = "solo"
    TEAM = "team"
    EITHER = "either"


class RegistrationStatus(str, enum.Enum):
    REGISTERED = "registered"
    CANCELLED = "cancelled"
    CHECKED_IN = "checked_in"
    NO_SHOW = "no_show"


class TeamStatus(str, enum.Enum):
    FORMING = "forming"
    CONFIRMED = "confirmed"
    CANCELLED = "cancelled"


class DefaultStart(str, enum.Enum):
    COLLEGE = "college"
    HOME = "home"
    CUSTOM = "custom"


class JourneyStatus(str, enum.Enum):
    NOT_STARTED = "not_started"
    EN_ROUTE = "en_route"
    DELAYED = "delayed"
    ARRIVED = "arrived"
