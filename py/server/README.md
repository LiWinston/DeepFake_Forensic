# Python Analysis Backend

This service exposes HTTP endpoints for image CNN and wraps video traditional analyzers, and provides a Kafka consumer for long-running tasks. It also reports progress to Redis for polling by the Java backend/front-end.

## Components
- Flask HTTP API: `app.py` (port 7000)
  - /health
  - /ai/models, /ai/predict/image, /ai/predict/video (delegates to existing 2dCNN API)
  - /traditional/video/noise (wraps VidTraditional/noise)
- Kafka Worker: `kafka_worker.py`
  - Consumes tasks from topics:
    - image-ai-analysis-tasks
    - video-traditional-analysis-tasks
    - video-ai-analysis-tasks
  - Publishes results to `analysis-results`
  - Reports progress to Redis at key `analysis:progress:{taskId}`

## Setup

```pwsh
# Windows PowerShell
cd py/server
python -m venv .venv
. .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
# Start Flask API
python app.py
# In another terminal, start Kafka worker
python kafka_worker.py
```

Configure via env vars if needed:
- KAFKA_BOOTSTRAP (default localhost:9092)
- REDIS_HOST (default localhost)
- GROUP_ID (default py-analyzer-group)

## Notes
- CNN models should be placed under `py/2dCNN/models/` (e.g., `tiny_*.pth`, `nano_*.pth`).
- For video analysis, provide local file path (when via Kafka) or upload video to the Flask endpoint.
