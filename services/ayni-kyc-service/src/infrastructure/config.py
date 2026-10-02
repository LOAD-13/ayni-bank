from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    KYC_MATCH_THRESHOLD: float = 90.0
    KYC_MANUAL_REVIEW_THRESHOLD: float = 75.0

    # Acceso a los documentos KYC (HU-02). identity-service solo envia la clave
    # del objeto; este servicio lo lee directamente de MinIO (diseno-base.md §3.5).
    MINIO_ENDPOINT: str = "http://localhost:9000"
    MINIO_ACCESS_KEY: str = ""
    MINIO_SECRET_KEY: str = ""
    MINIO_BUCKET_KYC: str = "ayni-kyc-documentos"

    # Cargar PaddleOCR al arrancar y no en la primera peticion: el modelo tarda
    # varios segundos en cargarse, y la primera extraccion superaria el timeout
    # de 10 s de identity-service. Apagado por defecto para que las pruebas no
    # carguen el modelo real.
    KYC_PRECARGAR_OCR: bool = False

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

settings = Settings()
