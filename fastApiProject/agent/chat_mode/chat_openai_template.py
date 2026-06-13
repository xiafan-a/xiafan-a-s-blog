from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
import os

load_dotenv()


class ChatOpenAITemplate:
    """ChatOpenAI template class with env configuration."""

    @classmethod
    def from_env(
        cls,
        model: str = None,
        api_url: str = None,
        api_key: str = None,
        temperature: float = 0,
        streaming: bool = True,
    ) -> ChatOpenAI:
        """Create a ChatOpenAI instance from environment variables.

        Args:
            model: Model name, defaults to GENERATE_MODEL_FAST env var.
            api_url: API URL, defaults to GENERATE_API_URL env var.
            api_key: API key, defaults to GENERATE_API_KEY env var.
            temperature: Sampling temperature, defaults to 0.
            streaming: Enable streaming, defaults to True.

        Returns:
            ChatOpenAI instance configured from env vars.
        """
        return ChatOpenAI(
            model=model or os.getenv("GENERATE_MODEL_FAST", ""),
            base_url=api_url or os.getenv("GENERATE_API_URL", ""),
            api_key=api_key or os.getenv("GENERATE_API_KEY", ""),
            temperature=temperature,
            streaming=streaming
        )
