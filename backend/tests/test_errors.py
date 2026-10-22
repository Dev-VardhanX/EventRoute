from fastapi import APIRouter
from app.core.errors import AppError
from app.main import app

r = APIRouter()


@r.get("/_boom")
def boom():
    raise AppError("EVENT_FULL", "No seats left", {"seat_limit": 10})


app.include_router(r)


def test_app_error_shape(client):
    res = client.get("/_boom")
    assert res.status_code == 409
    assert res.json() == {"code": "EVENT_FULL", "message": "No seats left", "details": {"seat_limit": 10}}


def test_unknown_route_uses_error_shape(client):
    res = client.get("/nope")
    assert res.status_code == 404 and res.json()["code"] == "NOT_FOUND"
