from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import auth, colleges, health, me
from app.core.config import get_settings
from app.core.errors import register_error_handlers

settings = get_settings()
app = FastAPI(title="EventRoute API", version="0.1.0", docs_url="/docs")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
register_error_handlers(app)

# Health endpoints
app.include_router(health.router)  # /health
app.include_router(health.router, prefix="/api/v1")

# API routers under /api/v1 and root alias
app.include_router(auth.router, prefix="/api/v1")
app.include_router(auth.router)

app.include_router(me.router, prefix="/api/v1")
app.include_router(me.router)

app.include_router(colleges.router, prefix="/api/v1")
app.include_router(colleges.router)
