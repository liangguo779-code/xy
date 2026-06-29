#!/bin/bash
# 校园生态平台 - 一键启动（Docker 容器 + 本地服务）

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "=========================================="
echo "  校园生态平台 - 一键启动"
echo "=========================================="

# 1. 启动 Docker 基础设施
echo ""
echo "[1/2] 启动 Docker 容器 (mysql, redis, elasticsearch)..."
if ! docker compose up -d mysql redis elasticsearch; then
    echo "❌ Docker 容器启动失败"
    exit 1
fi

# 等待容器健康
echo ""
echo "  等待容器就绪..."

wait_healthy() {
    local name=$1
    local max_wait=${2:-60}
    printf "  %-20s" "$name..."
    for i in $(seq 1 $max_wait); do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "missing")
        if [ "$status" = "healthy" ]; then
            echo " ✅"
            return 0
        fi
        if [ "$status" = "missing" ]; then
            echo " ❌ 容器不存在"
            return 1
        fi
        sleep 2
    done
    echo " ⚠️ 超时"
    return 1
}

DOCKER_OK=true
wait_healthy campus-mysql 30 || DOCKER_OK=false
wait_healthy campus-redis 15 || DOCKER_OK=false
wait_healthy campus-es 60 || DOCKER_OK=false

if [ "$DOCKER_OK" = false ]; then
    echo ""
    echo "❌ 部分容器启动失败，请检查 docker compose logs"
    exit 1
fi

# 2. 启动本地服务
echo ""
echo "[2/2] 启动本地服务..."
echo ""
bash "$PROJECT_DIR/start-dev.sh"
exit $?
