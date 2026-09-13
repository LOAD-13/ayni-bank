from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    KYC_MATCH_THRESHOLD: float = 90.0
    KYC_MANUAL_REVIEW_THRESHOLD: float = 75.0
    
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

settings = Settings()
