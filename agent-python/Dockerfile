ARG PYTHON_IMAGE=python:3.12-slim-bookworm
FROM ${PYTHON_IMAGE} AS builder
ARG PIP_INDEX_URL=https://pypi.org/simple

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_INDEX_URL=${PIP_INDEX_URL}

WORKDIR /build
COPY pyproject.toml README.md ./
COPY src ./src
RUN python -m pip install --prefix=/install .

FROM ${PYTHON_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH=/home/agent/.local/bin:$PATH

RUN groupadd --system agent && useradd --system --gid agent --create-home agent
WORKDIR /app
COPY --from=builder /install /usr/local
COPY alembic.ini ./
COPY migrations ./migrations
COPY src ./src
ENV PYTHONPATH=/app/src
USER agent
EXPOSE 24109
CMD ["python", "-m", "uvicorn", "mylesson_agent.main:app", "--host", "0.0.0.0", "--port", "24109"]
