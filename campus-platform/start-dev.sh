#!/bin/bash
# 校园生态平台 - 微服务开发模式启动脚本
# 前置条件: Docker 容器 (mysql, redis, elasticsearch, nacos) 已启动

set -e
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$PROJECT_DIR/logs"

echo "=========================================="
echo "  校园生态平台 - 微服务模式启动"
echo "=========================================="

# 1. 检查前置条件
echo ""
echo "[1/8] 检查环境..."

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

# 2. 检查 Docker 基础设施
echo ""
echo "[2/8] 检查 Docker 基础设施..."

check_container() {
    local name=$1
    local port=$2
    local display=$3
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${name}$"; then
        echo "✅ ${display} 容器运行中"
        return 0
    elif netstat -an 2>/dev/null | grep -q ":${port}.*LISTEN"; then
        echo "✅ ${display} 端口 ${port} 可用"
        return 0
    else
        echo "❌ ${display} 未运行 (容器: ${name}, 端口: ${port})"
        return 1
    fi
}

INFRA_OK=true
check_container "mysql" 3306 "MySQL" || INFRA_OK=false
check_container "redis" 6379 "Redis" || INFRA_OK=false
check_container "elasticsearch" 9200 "Elasticsearch" || INFRA_OK=false
check_container "nacos" 8848 "Nacos" || INFRA_OK=false

if [ "$INFRA_OK" = false ]; then
    echo ""
    echo "请先启动基础设施容器:"
    echo "  docker-compose up -d mysql redis elasticsearch nacos"
    exit 1
fi

# 3. 启动 AI 中台
echo ""
echo "[3/8] 启动 AI 中台 (端口 8000)..."
cd "$PROJECT_DIR/ai-service"

if [ ! -d "venv" ]; then
    echo "  创建 Python 虚拟环境..."
    python -m venv venv 2>/dev/null || python3 -m venv venv
fi

source venv/bin/activate 2>/dev/null || source venv/Scripts/activate 2>/dev/null
pip install -r requirements.txt -q -i https://pypi.tuna.tsinghua.edu.cn/simple 2>/dev/null

nohup python main.py > ../logs/ai-service.log 2>&1 &
AI_PID=$!
echo "✅ AI 中台已启动 (PID: $AI_PID)"

# 4. 构建后端微服务
echo ""
echo "[4/8] 构建后端微服务..."
cd "$PROJECT_DIR/backend"
mvn clean package -DskipTests -q

# 5. 启动微服务
echo ""
echo "[5/8] 启动微服务..."

start_service() {
    local jar=$1
    local name=$2
    local port=$3
    local log=$4

    echo "  启动 ${name} (端口 ${port})..."
    nohup java -jar "$jar" > "../logs/${log}" 2>&1 &
    echo $!
}

GATEWAY_PID=$(start_service "campus-gateway/target/campus-gateway-1.0.0-SNAPSHOT.jar" "Gateway" 9000 "gateway.log")
echo "  ✅ Gateway PID: $GATEWAY_PID"

USER_PID=$(start_service "campus-user/target/campus-user-1.0.0-SNAPSHOT.jar" "User Service" 8081 "user.log")
echo "  ✅ User Service PID: $USER_PID"

TRADE_PID=$(start_service "campus-trade/target/campus-trade-1.0.0-SNAPSHOT.jar" "Trade Service" 8082 "trade.log")
echo "  ✅ Trade Service PID: $TRADE_PID"

FORUM_PID=$(start_service "campus-forum/target/campus-forum-1.0.0-SNAPSHOT.jar" "Forum Service" 8083 "forum.log")
echo "  ✅ Forum Service PID: $FORUM_PID"

AI_CONSULT_PID=$(start_service "campus-ai/target/campus-ai-1.0.0-SNAPSHOT.jar" "AI Consult" 8084 "ai-consult.log")
echo "  ✅ AI Consult PID: $AI_CONSULT_PID"

ADMIN_PID=$(start_service "campus-admin/target/campus-admin-1.0.0-SNAPSHOT.jar" "Admin Service" 8085 "admin.log")
echo "  ✅ Admin Service PID: $ADMIN_PID"

# 6. 启动前端
echo ""
echo "[6/8] 启动前端 (端口 5173)..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null

nohup npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "✅ 前端已启动 (PID: $FRONTEND_PID)"

# 7. 等待服务就绪
echo ""
echo "[7/8] 等待服务就绪..."

wait_for_service() {
    local url=$1
    local name=$2
    local max_wait=${3:-40}
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

wait_for_service "http://localhost:8000/health" "AI 中台"
wait_for_service "http://localhost:9000/actuator/health" "Gateway"
wait_for_service "http://localhost:8081/actuator/health" "User Service"
wait_for_service "http://localhost:8082/actuator/health" "Trade Service"
wait_for_service "http://localhost:8083/actuator/health" "Forum Service"
wait_for_service "http://localhost:8084/actuator/health" "AI Consult"
wait_for_service "http://localhost:8085/actuator/health" "Admin Service"

# 8. 初始化 RAG 知识库
echo ""
echo "[8/8] 初始化 RAG 知识库..."
MD_COUNT=$(find "$PROJECT_DIR/ai-service/knowledge" -maxdepth 1 -name "*.md" -type f 2>/dev/null | wc -l)
if [ "$MD_COUNT" -eq 0 ]; then
    echo "  知识库为空，跳过索引"
else
    RAG_RESPONSE=$(curl -s -X POST http://localhost:8000/knowledge/rebuild 2>&1)
    if echo "$RAG_RESPONSE" | grep -q "重建"; then
        echo "  ✅ RAG 知识库已触发（${MD_COUNT} 个文件）"
    else
        echo "  ⚠️  RAG 初始化失败: $RAG_RESPONSE"
    fi
fi

# 保存 PID
echo "$AI_PID $GATEWAY_PID $USER_PID $TRADE_PID $FORUM_PID $AI_CONSULT_PID $ADMIN_PID $FRONTEND_PID" > "$PROJECT_DIR/.pids"

echo ""
echo "=========================================="
echo "  启动完成！"
echo "=========================================="
echo ""
echo "  访问地址:"
echo "    前端:      http://localhost:5173"
echo "    Gateway:   http://localhost:9000"
echo "    Nacos:     http://localhost:8848/nacos  (nacos/nacos)"
echo "    AI 中台:   http://localhost:8000"
echo ""
echo "  微服务:"
echo "    User:    8081  |  Trade:  8082  |  Forum:  8083"
echo "    AI:      8084  |  Admin:  8085"
echo ""
echo "  测试账号: testuser / test123"
echo "  管理后台: admin / admin123"
echo ""
echo "  日志: $PROJECT_DIR/logs/"
echo "  停止: bash stop-dev.sh"
echo ""
