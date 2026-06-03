#!/bin/bash
# 校园生态平台 - 开发模式启动脚本
# 用法: bash start-dev.sh

set -e
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  校园生态平台 - 开发模式启动"
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

if ! command -v python &> /dev/null && ! command -v python3 &> /dev/null; then
    echo "❌ 未找到 Python，请安装 Python 3.10+"
    exit 1
fi

echo "✅ Java: $(java -version 2>&1 | head -1)"
echo "✅ Node: $(node -v)"
echo "✅ Python: $(python --version 2>&1 || python3 --version 2>&1)"

# 2. 检查基础设施
echo ""
echo "[2/6] 检查 MySQL 和 Redis..."

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
echo "[3/6] 启动 AI 中台 (端口 8000)..."
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
echo "[4/6] 构建并启动后端 (端口 8080)..."
cd "$PROJECT_DIR/backend"
mvn clean package -DskipTests -q

# 后台启动
mkdir -p "$PROJECT_DIR/logs"
nohup java -jar campus-admin/target/campus-admin-1.0.0-SNAPSHOT.jar > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
echo "✅ 后端已启动 (PID: $BACKEND_PID)"

# 5. 启动前端
echo ""
echo "[5/6] 启动前端 (端口 5173)..."
cd "$PROJECT_DIR/frontend"
npm install -q 2>/dev/null

# 后台启动
nohup npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "✅ 前端已启动 (PID: $FRONTEND_PID)"

# 6. 等待服务就绪
echo ""
echo "[6/6] 等待服务就绪..."
sleep 10

# 检查服务状态
if curl -s http://localhost:8080/api/auth/login > /dev/null 2>&1; then
    echo "✅ 后端就绪"
else
    echo "⚠️  后端启动中，请稍候..."
fi

if curl -s http://localhost:8000/health > /dev/null 2>&1; then
    echo "✅ AI 中台就绪"
else
    echo "⚠️  AI 中台启动中，请稍候..."
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
