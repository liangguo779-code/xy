#!/bin/bash
# 校园生态平台 - 单体应用开发模式启动脚本
# 前置条件: Docker 容器 (mysql, redis, elasticsearch) 已启动
# 如需一键启动 Docker + 本服务，请使用 start-all.sh
#
# 注意: AI 服务已迁移到 backend/campus-ai (LangChain4j in-process),
#      不再需要单独的 Python AI 中台。

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$PROJECT_DIR/logs"

STARTUP_OK=true

cleanup() {
    if [ "$STARTUP_OK" = false ]; then
        echo ""
        echo "=========================================="
        echo "  启动失败，自动清理已启动的服务..."
        echo "=========================================="
        bash "$PROJECT_DIR/stop-dev.sh"
    fi
}
trap cleanup EXIT
trap 'STARTUP_OK=false; exit 130' INT TERM

echo "=========================================="
echo "  校园生态平台 - 单体模式启动"
echo "=========================================="

# 1. 检查前置条件
echo ""
echo "[1/6] 检查环境..."

if ! command -v java &> /dev/null; then
    echo "❌ 未找到 Java，请安装 JDK 17+"
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "❌ 未找到 Node.js，请安装 Node 18+"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "❌ 未找到 Maven，请安装 Maven 3.8+"
    exit 1
fi

echo "✅ Java: $(java -version 2>&1 | head -1)"
echo "✅ Node: $(node -v)"
echo "✅ Maven: $(mvn -v 2>&1 | head -1)"

# 2. 检查 Docker 基础设施（仅检查，不启动）
echo ""
echo "[2/6] 检查 Docker 基础设施..."

check_port() {
    local port=$1
    local name=$2
    if netstat -an 2>/dev/null | grep -q ":${port}.*LISTEN"; then
        echo "✅ ${name} 端口 ${port} 可用"
        return 0
    elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "campus-"; then
        echo "✅ ${name} Docker 容器运行中"
        return 0
    else
        echo "❌ ${name} 未运行 (端口: ${port})"
        return 1
    fi
}

INFRA_OK=true
check_port 3306 "MySQL" || INFRA_OK=false
check_port 6379 "Redis" || INFRA_OK=false
check_port 9200 "Elasticsearch" || INFRA_OK=false

if [ "$INFRA_OK" = false ]; then
    echo ""
    echo "请先启动基础设施容器:"
    echo "  bash start-all.sh          # 一键启动全部"
    echo "  docker compose up -d mysql redis elasticsearch  # 仅启动容器"
    exit 1
fi

# 3. 清理旧进程
echo ""
echo "[3/6] 清理旧进程..."

echo "  调用 stop-dev.sh 彻底清理..."
bash "$PROJECT_DIR/stop-dev.sh"

sleep 2

# 4. 构建后端单体应用
echo ""
echo "[4/6] 构建后端单体应用..."

cd "$PROJECT_DIR/backend"

# 检查并等待旧的.jar文件被释放
JAR_FILE="$PROJECT_DIR/backend/campus-app/target/campus-app-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "  检查旧的.jar文件是否可删除..."
    for i in {1..10}; do
        if rm -f "$JAR_FILE" 2>/dev/null; then
            echo "  ✅ 旧.jar文件已清理"
            break
        fi
        if [ $i -eq 10 ]; then
            echo "  ⚠️  旧.jar文件被锁定，尝试强制清理..."
            cmd //c "del /f /q \"$JAR_FILE\"" 2>/dev/null || true
            sleep 1
        fi
        echo "  ⏳ 等待.jar文件释放... ($i/10)"
        sleep 1
    done
fi

if ! mvn clean package -DskipTests -q; then
    echo "❌ 后端构建失败"
    STARTUP_OK=false
    exit 1
fi

# 检查端口是否可用
echo ""
echo "  检查端口 9000 是否可用..."
if netstat -ano 2>/dev/null | grep -q ":9000.*LISTEN"; then
    echo "  ⚠️  端口 9000 仍被占用，强制清理..."
    local_pid=$(netstat -ano 2>/dev/null | grep ":9000 " | grep LISTEN | awk '{print $5}' | head -1)
    if [ -n "$local_pid" ] && [ "$local_pid" != "0" ]; then
        taskkill //F //PID "$local_pid" > /dev/null 2>&1 || true
        sleep 2
    fi
fi

echo ""
echo "  启动 campus-app (端口 9000)..."
nohup java -jar campus-app/target/campus-app-1.0.0-SNAPSHOT.jar --server.address=0.0.0.0 > ../logs/campus-app.log 2>&1 &
APP_PID=$!
echo "✅ campus-app 已启动 (PID: $APP_PID)"

# 5. 启动前端
echo ""
echo "[5/6] 启动前端 (端口 5173)..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null

nohup npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "✅ 前端已启动 (PID: $FRONTEND_PID)"

# 6. 等待服务就绪
echo ""
echo "[6/6] 等待服务就绪..."

wait_for_service() {
    local url=$1
    local name=$2
    local max_wait=${3:-60}
    printf "  等待 %-20s" "$name..."
    for i in $(seq 1 $max_wait); do
        if curl -s "$url" > /dev/null 2>&1; then
            echo " ✅"
            return 0
        fi
        sleep 2
    done
    echo " ⚠️ 超时"
    return 1
}

wait_for_service "http://localhost:9000/api/ai/health" "campus-app AI" 180 || STARTUP_OK=false

if [ "$STARTUP_OK" = false ]; then
    echo ""
    echo "❌ 部分服务启动超时"
    exit 1
fi

# 初始化 ES 商品索引
echo ""
echo "  初始化 ES 商品索引..."
ADMIN_TOKEN=$(curl -s -X POST http://localhost:9000/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin123"}' 2>/dev/null \
    | python -c 'import sys,json; print(json.load(sys.stdin).get("data",{}).get("token",""))' 2>/dev/null)
if [ -n "$ADMIN_TOKEN" ]; then
    curl -s -X PUT http://localhost:9000/api/admin/goods/reindex \
        -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null 2>&1 && \
        echo "  ✅ ES 商品索引已重建" || \
        echo "  ⚠️  ES 索引重建失败"
else
    echo "  ⚠️  获取 admin token 失败，跳过 ES 重建"
fi

# 初始化 RAG 知识库 (LangChain4j in-process,触发后台索引任务)
echo ""
echo "  初始化 RAG 知识库..."
MD_COUNT=$(find "$PROJECT_DIR/backend/campus-ai/src/main/resources/knowledge" -maxdepth 1 -name "*.md" -type f 2>/dev/null | wc -l)
if [ "$MD_COUNT" -gt 0 ]; then
    for attempt in 1 2 3; do
        if curl -s -X POST http://localhost:9000/api/admin/knowledge/rebuild > /dev/null 2>&1; then
            echo "  ✅ RAG 知识库已触发（${MD_COUNT} 个文件）"
            break
        fi
        echo "  ⏳ 重试中... ($attempt/3)"
        sleep 3
    done
else
    echo "  ℹ️  无知识库文件，跳过"
fi

# 保存 PID
echo "$APP_PID $FRONTEND_PID" > "$PROJECT_DIR/.pids"

echo ""
echo "=========================================="
echo "  启动完成！"
echo "=========================================="
echo ""
echo "  访问地址:"
echo "    前端:      http://localhost:5173"
echo "    后端:      http://localhost:9000"
echo "    AI (内嵌): http://localhost:9000/api/ai/health"
echo ""
echo "  测试账号: testuser / test123"
echo "  管理后台: admin / admin123"
echo ""
echo "  日志: $PROJECT_DIR/logs/"
echo "  停止: bash stop-dev.sh"
echo ""
