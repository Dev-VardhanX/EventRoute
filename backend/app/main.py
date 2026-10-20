from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="EventRoute API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # replace with ALLOWED_ORIGINS from settings
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}

# TODO (backend lead): include routers from app/api under prefix /api/v1
