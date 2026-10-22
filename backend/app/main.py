from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api import health
from app.core.config import get_settings
from app.core.errors import register_error_handlers

settings = get_settings()
app = FastAPI(title="EventRoute API", version="0.1.0", docs_url="/docs")

app.add_middleware(
    CORSMiddleware, allow_origins=settings.cors_origins,
    allow_credentials=True, allow_methods=["*"], allow_headers=["*"],
)
register_error_handlers(app)

app.include_router(health.router)               # /health (uptime pinger)
app.include_router(health.router, prefix="/api/v1")
# later steps: app.include_router(auth.router, prefix="/api/v1") ...
