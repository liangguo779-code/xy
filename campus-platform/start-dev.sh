#!/bin/bash
# 校园生态平台 - 单体应用开发模式启动脚本
# 前置条件: Docker 容器 (mysql, redis, elasticsearch) 已启动
# 如需一键启动 Docker + 本服务，请使用 start-all.sh

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
echo "[1/7] 检查环境..."

if ! command -v java &> /dev/null; then
    echo "❌ 未找到 Java，请安装 JDK 17+"
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "❌ 未找到 Node.js，请安装 Node 18+"
    exit 1
fi

if ! command -v python &> /dev/null && ! command -v python3 &> /dev/null; then
    echo "❌ 未找到 Python，请安装 Python 3.10+"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "❌ 未找到 Maven，请安装 Maven 3.8+"
    exit 1
fi

echo "✅ Java: $(java -version 2>&1 | head -1)"
echo "✅ Node: $(node -v)"
echo "✅ Python: $(python --version 2>&1 || python3 --version 2>&1)"
echo "✅ Maven: $(mvn -v 2>&1 | head -1)"

# 2. 检查 Docker 基础设施（仅检查，不启动）
echo ""
echo "[2/7] 检查 Docker 基础设施..."

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
echo "[3/7] 清理旧进程..."

kill_by_pattern() {
    local pattern=$1
    local name=$2
    local pids
    pids=$(tasklist 2>/dev/null | grep -i "$pattern" | awk '{print $2}' | tr -d '\r')
    if [ -z "$pids" ]; then
        echo "  - ${name} 无残留进程"
        return
    fi
    for pid in $pids; do
        taskkill //F //PID "$pid" > /dev/null 2>&1 && echo "  ✅ ${name} (PID: ${pid}) 已清理"
    done
}

# 清理所有 campus-app 相关的 Java 进程
kill_by_pattern "campus-app-1.0.0-SNAPSHOT.jar" "campus-app"
# 清理残留的 maven 进程
kill_by_pattern "maven" "Maven"
# 清理 AI 中台 Python 进程
kill_by_pattern "main.py" "AI 中台"
# 清理前端 Vite 进程
kill_by_pattern "vite" "前端(Vite)"

sleep 1

# 4. 启动 AI 中台
echo ""
echo "[4/7] 启动 AI 中台 (端口 8000)..."
cd "$PROJECT_DIR/ai-service"

if [ ! -d "venv" ]; then
    echo "  创建 Python 虚拟环境..."
    python -m venv venv 2>/dev/null || python3 -m venv venv
fi

source venv/bin/activate 2>/dev/null || source venv/Scripts/activate 2>/dev/null
pip install -r requirements.txt -q -i https://pypi.tuna.tsinghua.edu.cn/simple 2>/dev/null

export HF_HUB_OFFLINE=1
nohup python main.py > ../logs/ai-service.log 2>&1 &
AI_PID=$!
if ! kill -0 "$AI_PID" 2>/dev/null; then
    echo "❌ AI 中台启动失败"
    STARTUP_OK=false
    exit 1
fi
echo "✅ AI 中台已启动 (PID: $AI_PID)"

# 5. 构建并启动后端单体应用
echo ""
echo "[5/7] 构建后端单体应用..."

cd "$PROJECT_DIR/backend"
if ! mvn clean package -DskipTests -q; then
    echo "❌ 后端构建失败"
    STARTUP_OK=false
    exit 1
fi

echo ""
echo "  启动 campus-app (端口 9000)..."
nohup java -jar campus-app/target/campus-app-1.0.0-SNAPSHOT.jar > ../logs/campus-app.log 2>&1 &
APP_PID=$!
echo "✅ campus-app 已启动 (PID: $APP_PID)"

# 6. 启动前端
echo ""
echo "[6/7] 启动前端 (端口 5173)..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null

nohup npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "✅ 前端已启动 (PID: $FRONTEND_PID)"

# 7. 等待服务就绪
echo ""
echo "[7/7] 等待服务就绪..."

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

wait_for_service "http://localhost:8000/health" "AI 中台" || STARTUP_OK=false
wait_for_service "http://localhost:9000/actuator/health" "campus-app" || STARTUP_OK=false

if [ "$STARTUP_OK" = false ]; then
    echo ""
    echo "❌ 部分服务启动超时"
    exit 1
fi

# 初始化 RAG 知识库（AI 中台就绪后触发）
echo ""
echo "  初始化 RAG 知识库..."
MD_COUNT=$(find "$PROJECT_DIR/ai-service/knowledge" -maxdepth 1 -name "*.md" -type f 2>/dev/null | wc -l)
if [ "$MD_COUNT" -gt 0 ]; then
    for attempt in 1 2 3; do
        if curl -s -X POST http://localhost:8000/knowledge/rebuild > /dev/null 2>&1; then
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
echo "$AI_PID $APP_PID $FRONTEND_PID" > "$PROJECT_DIR/.pids"

echo ""
echo "=========================================="
echo "  启动完成！"
echo "=========================================="
echo ""
echo "  访问地址:"
echo "    前端:      http://localhost:5173"
echo "    后端:      http://localhost:9000"
echo "    AI 中台:   http://localhost:8000"
echo ""
echo "  测试账号: testuser / test123"
echo "  管理后台: admin / admin123"
echo ""
echo "  日志: $PROJECT_DIR/logs/"
echo "  停止: bash stop-dev.sh"
echo ""
