"""One error shape everywhere: {"code", "message", "details"?}"""
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

STATUS = {
    "VALIDATION_ERROR": 422, "UNAUTHENTICATED": 401, "FORBIDDEN": 403, "NOT_FOUND": 404,
    "EVENT_CLOSED": 409, "EVENT_FULL": 409, "ALREADY_REGISTERED": 409, "TEAM_FULL": 409,
    "TEAM_TOO_SMALL": 409, "NOT_ELIGIBLE": 403, "TIME_CLASH": 409, "INVITE_INVALID": 404,
    "ROUTE_UNAVAILABLE": 503, "RATE_LIMITED": 429,
}


class AppError(Exception):
    def __init__(self, code: str, message: str, details: dict | None = None, status: int | None = None):
        self.code, self.message, self.details = code, message, details
        self.status = status or STATUS.get(code, 400)


def _body(code: str, message: str, details=None) -> dict:
    body = {"code": code, "message": message}
    if details is not None:
        body["details"] = details
    return body


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def app_error(_: Request, exc: AppError):
        return JSONResponse(_body(exc.code, exc.message, exc.details), status_code=exc.status)

    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, exc: RequestValidationError):
        errs = [{"field": ".".join(str(p) for p in e["loc"][1:]), "message": e["msg"]} for e in exc.errors()]
        return JSONResponse(_body("VALIDATION_ERROR", "Invalid request", {"errors": errs}), status_code=422)

    @app.exception_handler(StarletteHTTPException)
    async def http_error(_: Request, exc: StarletteHTTPException):
        code = {401: "UNAUTHENTICATED", 403: "FORBIDDEN", 404: "NOT_FOUND", 429: "RATE_LIMITED"}.get(
            exc.status_code, "VALIDATION_ERROR")
        return JSONResponse(_body(code, str(exc.detail)), status_code=exc.status_code)
