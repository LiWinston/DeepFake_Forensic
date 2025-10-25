#!/usr/bin/env bash
set -euo pipefail

# One-click runner to bring up:
# - Docker middleware (MySQL/Redis/Kafka/MinIO + init)
# - Backend (Java JAR) on 8082
# - Frontend (Vite preview) on 3000
# - Py worker (kafka_worker.py)
# Requirements: Debian/Ubuntu with sudo; internet access
# Optional env:
#   SKIP_TORCH=1       Skip installing torch/torchvision (uses requirements.notorch.txt)
#   REBUILD=1          Force rebuild frontend bundle
#   CLEAN=1            Before start, stop existing app procs and bring docker down

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_DIR="$ROOT_DIR/logs"
PID_DIR="$ROOT_DIR/scripts/.pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

backend_env() {
  # Export backend env pointing to localhost services (docker mapped ports)
  set +u
  [ -f "$ROOT_DIR/.env" ] && source "$ROOT_DIR/.env"
  set -u
  export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/forensic_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true"
  export SPRING_DATASOURCE_USERNAME="root"
  export SPRING_DATASOURCE_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
  export SPRING_DATA_REDIS_HOST="localhost"
  export SPRING_DATA_REDIS_PORT="6379"
  export SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
  export MINIO_ENDPOINT="http://localhost:9000"
  export MINIO_ACCESS_KEY="minioadmin"
  export MINIO_SECRET_KEY="minioadmin"
  export MINIO_BUCKET_NAME="forensic-media"
  export SERVER_PORT="8082"
}

stop_existing() {
  echo "[run-all] Stopping existing app processes if any..."
  for name in backend frontend pyworker; do
    PID_FILE="$PID_DIR/$name.pid"
    if [ -f "$PID_FILE" ]; then
      PID=$(cat "$PID_FILE" || true)
      if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
        echo "[run-all] Killing $name (pid=$PID)"
        kill "$PID" || true; sleep 1
      fi
      rm -f "$PID_FILE"
    fi
  done
}

if [ "${CLEAN:-0}" = "1" ]; then
  stop_existing || true
  echo "[run-all] Bringing docker down..."
  sudo docker compose -f "$ROOT_DIR/docker-compose.yml" down || true
fi

echo "[run-all] 1/4 Docker middleware setup..."
START_APP=0 bash "$ROOT_DIR/scripts/setup-docker-linux.sh"

echo "[run-all] 2/4 Installing project deps..."
bash "$ROOT_DIR/scripts/setup-linux.sh"

echo "[run-all] 3/4 Starting backend..."
backend_env
(
  cd "$ROOT_DIR/backend"
  nohup bash -c 'backend_env_func() { :; }; backend_env_func; java -jar target/*.jar' \
    > "$LOG_DIR/backend.log" 2>&1 & echo $! > "$PID_DIR/backend.pid"
) &
sleep 2

echo "[run-all] 4/4 Starting frontend preview on :3000..."
(
  cd "$ROOT_DIR/frontend"
  if [ "${REBUILD:-0}" = "1" ] || [ ! -d "dist" ]; then
    npm run build >> "$LOG_DIR/frontend.log" 2>&1 || npm run build >> "$LOG_DIR/frontend.log" 2>&1
  fi
  nohup npm run serve -- --port 3000 >> "$LOG_DIR/frontend.log" 2>&1 & echo $! > "$PID_DIR/frontend.pid"
) &
sleep 2

echo "[run-all] Starting py-worker..."
(
  cd "$ROOT_DIR/py/server"
  set +u; source .venv/bin/activate; set -u
  nohup python kafka_worker.py >> "$LOG_DIR/py-worker.log" 2>&1 & echo $! > "$PID_DIR/pyworker.pid"
) &

echo "[run-all] All components started. Summary:"
echo "  - Backend:   http://localhost:8082 (logs: $LOG_DIR/backend.log)"
echo "  - Frontend:  http://localhost:3000 (logs: $LOG_DIR/frontend.log)"
echo "  - Py worker: logs: $LOG_DIR/py-worker.log"
echo "  - Middleware: MySQL:3306, Redis:6379, Kafka:9092, MinIO:9000"

echo "[run-all] To stop app processes:"
echo "  bash -c 'for f in $PID_DIR/*.pid; do [ -f \"$f\" ] && kill \"$(cat \"$f\")\" 2>/dev/null || true; done'"
