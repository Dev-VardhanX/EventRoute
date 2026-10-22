from fastapi import APIRouter, Depends, File, UploadFile
from pydantic import BaseModel

from app.core.security import require_role
from app.models.enums import UserRole
from app.models.user import User
from app.services.storage import save_image

router = APIRouter(tags=["uploads"])


class ImageUploadResponse(BaseModel):
    url: str


@router.post(
    "/uploads/image",
    response_model=ImageUploadResponse,
    dependencies=[Depends(require_role(UserRole.ORGANIZER, UserRole.ADMIN))],
)
def upload_image(file: UploadFile = File(...)):
    """Upload event banner or image (jpg, png, webp, max 3 MB). Restricted to organizers and admins."""
    url = save_image(file)
    return ImageUploadResponse(url=url)
