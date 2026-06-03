#!/bin/bash
# 校园生态平台 - 开发模式启动脚本
# 用法: bash start-dev.sh

set -e
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$PROJECT_DIR/logs"

echo "=========================================="
echo "  校园生态平台 - 开发模式启动"
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

# 2. 检查基础设施
echo ""
echo "[2/7] 检查 MySQL 和 Redis..."

if ! netstat -an 2>/dev/null | grep -q ":3306.*LISTEN"; then
    echo "⚠️  MySQL 未运行，请先启动 MySQL (端口 3306)"
    echo "   或运行: docker-compose up -d mysql redis"
    exit 1
fi
echo "✅ MySQL 已运行"

if ! netstat -an 2>/dev/null | grep -q ":6379.*LISTEN"; then
    echo "⚠️  Redis 未运行，请先启动 Redis (端口 6379)"
    exit 1
fi
echo "✅ Redis 已运行"

# 3. 启动 AI 中台
echo ""
echo "[3/7] 启动 AI 中台 (端口 8000)..."
cd "$PROJECT_DIR/ai-service"

if [ ! -d "venv" ]; then
    echo "  创建 Python 虚拟环境..."
    python -m venv venv 2>/dev/null || python3 -m venv venv
fi

source venv/bin/activate 2>/dev/null || source venv/Scripts/activate 2>/dev/null
pip install -r requirements.txt -q -i https://pypi.tuna.tsinghua.edu.cn/simple 2>/dev/null

# 后台启动
nohup python main.py > ../logs/ai-service.log 2>&1 &
AI_PID=$!
echo "✅ AI 中台已启动 (PID: $AI_PID)"

# 4. 构建并启动后端
echo ""
echo "[4/7] 构建并启动后端 (端口 8080)..."
cd "$PROJECT_DIR/backend"
mvn clean package -DskipTests -q

# 后台启动
nohup java -jar campus-admin/target/campus-admin-1.0.0-SNAPSHOT.jar > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
echo "✅ 后端已启动 (PID: $BACKEND_PID)"

# 5. 启动前端
echo ""
echo "[5/7] 启动前端 (端口 5173)..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null

# 后台启动
nohup npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "✅ 前端已启动 (PID: $FRONTEND_PID)"

# 6. 等待服务就绪
echo ""
echo "[6/7] 等待服务就绪..."

# 等待 AI 中台就绪
echo "  等待 AI 中台..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8000/health > /dev/null 2>&1; then
        echo "✅ AI 中台就绪"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "⚠️  AI 中台启动超时，请检查日志: $PROJECT_DIR/logs/ai-service.log"
    fi
    sleep 2
done

# 等待后端就绪
echo "  等待后端..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 || curl -s http://localhost:8080/doc.html > /dev/null 2>&1; then
        echo "✅ 后端就绪"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "⚠️  后端启动超时，请检查日志: $PROJECT_DIR/logs/backend.log"
    fi
    sleep 2
done

# 7. 初始化 RAG 知识库
echo ""
echo "[7/7] 初始化 RAG 知识库..."
RAG_RESPONSE=$(curl -s -X POST http://localhost:8000/knowledge/rebuild 2>&1)
if echo "$RAG_RESPONSE" | grep -q "重建完成"; then
    echo "✅ RAG 知识库初始化完成"
    echo "  $RAG_RESPONSE"
else
    echo "⚠️  RAG 初始化失败或知识库为空"
    echo "  $RAG_RESPONSE"
    echo "  可稍后手动触发: curl -X POST http://localhost:8000/knowledge/rebuild"
fi

echo ""
echo "=========================================="
echo "  🎉 启动完成！"
echo "=========================================="
echo ""
echo "  前端:     http://localhost:5173"
echo "  后端 API: http://localhost:8080"
echo "  AI 中台:  http://localhost:8000"
echo "  Swagger:  http://localhost:8080/doc.html"
echo ""
echo "  测试账号: testuser / test123"
echo "  管理后台: admin / admin123 -> 右上角头像 -> 管理后台"
echo ""
echo "  日志目录: $PROJECT_DIR/logs/"
echo "  停止服务: bash stop-dev.sh"
echo ""

# 保存 PID
echo "$AI_PID $BACKEND_PID $FRONTEND_PID" > "$PROJECT_DIR/.pids"
