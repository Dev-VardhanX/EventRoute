from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "dev"
    database_url: str = "postgresql+psycopg://eventroute:eventroute@localhost:5432/eventroute"
    jwt_secret: str = "dev-secret-change-me"
    access_token_minutes: int = 15
    refresh_token_days: int = 14
    allowed_origins: str = "http://localhost:3000"
    app_base_url: str = "http://localhost:8000"
    default_timezone: str = "Asia/Kolkata"

    google_maps_server_key: str = ""
    ors_api_key: str = ""
    fcm_service_account_json: str = ""
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    cloudinary_url: str = ""
    daily_paid_route_limit: int = 500

    @property
    def cors_origins(self) -> list[str]:
        return [o.strip() for o in self.allowed_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
