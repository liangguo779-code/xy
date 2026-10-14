#!/bin/bash
# 校园生态平台 - 一键启动 Docker 基础设施 + 单体应用开发模式
# 等价于: docker-compose up -d mysql redis elasticsearch && bash start-dev.sh

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  校园生态平台 - 一键启动 (Docker + 开发模式)"
echo "=========================================="

# 0. 检查 Docker / docker-compose
if ! command -v docker &> /dev/null; then
    echo "❌ 未找到 Docker，请先安装 Docker Desktop"
    exit 1
fi

# docker compose (v2 插件) 与 docker-compose (独立二进制) 均可
if docker compose version &> /dev/null; then
    COMPOSE="docker compose"
elif command -v docker-compose &> /dev/null; then
    COMPOSE="docker-compose"
else
    echo "❌ 未找到 docker compose / docker-compose"
    exit 1
fi

# 1. 启动基础设施容器
echo ""
echo "[1/2] 启动 Docker 基础设施 (mysql, redis, elasticsearch)..."
$COMPOSE -f "$PROJECT_DIR/docker-compose.yml" up -d mysql redis elasticsearch || {
    echo "❌ Docker 基础设施启动失败"
    exit 1
}

# 2. 等待健康检查通过（MySQL 首次初始化含 init.sql，可能需要 ~30s）
echo ""
echo "[2/2] 等待容器就绪..."
wait_healthy() {
    local name=$1
    local max_wait=${2:-60}
    printf "  等待 %-20s" "$name..."
    for i in $(seq 1 $max_wait); do
        status=$(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null)
        if [ "$status" = "healthy" ]; then
            echo " ✅"
            return 0
        fi
        sleep 2
    done
    echo " ⚠️ 超时 (status: ${status:-unknown})"
    return 1
}

ALL_OK=true
wait_healthy campus-mysql 60 || ALL_OK=false
wait_healthy campus-redis 30 || ALL_OK=false
wait_healthy campus-es 60 || ALL_OK=false

if [ "$ALL_OK" = false ]; then
    echo ""
    echo "❌ 基础设施未就绪，请查看容器日志:"
    echo "   $COMPOSE -f \"$PROJECT_DIR/docker-compose.yml\" logs"
    exit 1
fi

# 3. 启动应用（后端 + 前端）
echo ""
bash "$PROJECT_DIR/start-dev.sh"
