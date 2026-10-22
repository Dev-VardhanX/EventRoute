import os
import uuid
from abc import ABC, abstractmethod
from pathlib import Path

from fastapi import UploadFile

from app.core.config import get_settings
from app.core.errors import AppError

settings = get_settings()

ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGE_SIZE_BYTES = 3 * 1024 * 1024  # 3 MB


class StorageProvider(ABC):
    @abstractmethod
    def save_image(self, file: UploadFile) -> str:
        """Save an uploaded image and return its URL."""
        pass


class LocalStorageProvider(StorageProvider):
    def __init__(self, upload_dir: Path | None = None):
        if upload_dir is None:
            code_dir = Path("/code")
            if code_dir.exists():
                upload_dir = code_dir / "uploads"
            else:
                upload_dir = Path(__file__).resolve().parent.parent.parent / "uploads"
        self.upload_dir = upload_dir
        self.upload_dir.mkdir(parents=True, exist_ok=True)

    def save_image(self, file: UploadFile) -> str:
        # Validate filename and extension
        filename = file.filename or "upload"
        ext = os.path.splitext(filename)[1].lower()
        if ext not in ALLOWED_EXTENSIONS:
            raise AppError(
                "VALIDATION_ERROR",
                f"Invalid file extension '{ext}'. Allowed extensions: {', '.join(sorted(ALLOWED_EXTENSIONS))}",
            )

        if file.content_type and file.content_type.lower() not in ALLOWED_CONTENT_TYPES:
            raise AppError(
                "VALIDATION_ERROR",
                f"Invalid file content type '{file.content_type}'. Must be image (jpg, png, webp).",
            )

        # Read content and validate size
        contents = file.file.read()
        if len(contents) > MAX_IMAGE_SIZE_BYTES:
            raise AppError(
                "VALIDATION_ERROR",
                f"File size exceeds maximum allowed limit of 3 MB (received {len(contents)} bytes).",
            )

        unique_name = f"{uuid.uuid4().hex}{ext}"
        target_path = self.upload_dir / unique_name
        with open(target_path, "wb") as f:
            f.write(contents)

        return f"/uploads/{unique_name}"


# Default provider instance
_default_storage = LocalStorageProvider()


def get_storage_provider() -> StorageProvider:
    return _default_storage


def save_image(file: UploadFile) -> str:
    return get_storage_provider().save_image(file)
