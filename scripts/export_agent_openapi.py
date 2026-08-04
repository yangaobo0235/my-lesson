import json
from pathlib import Path

from mylesson_agent.main import app


def main() -> None:
    schema = app.openapi()
    schema["paths"] = {
        path: operations
        for path, operations in schema["paths"].items()
        if path.startswith("/api/v1/ai")
    }
    schema["info"]["title"] = "MyLesson Public Agent API"
    destination = Path(__file__).resolve().parents[1] / "contracts" / "public-agent.openapi.yaml"
    destination.write_text(
        json.dumps(schema, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
