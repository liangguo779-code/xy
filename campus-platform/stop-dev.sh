#!/bin/bash
# 校园生态平台 - 停止开发服务

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "停止服务..."

# 按端口查找并杀掉进程的函数
kill_by_port() {
    local port=$1
    local name=$2
    local pid=$(netstat -ano 2>/dev/null | grep ":${port}.*LISTEN" | awk '{print $5}' | head -1)
    if [ -n "$pid" ] && [ "$pid" != "0" ]; then
        taskkill //F //PID "$pid" 2>/dev/null && echo "✅ $name 已停止 (PID: $pid)" || echo "  $name 停止失败"
    else
        echo "  $name 未运行 (端口 $port)"
    fi
}

# 尝试从 .pids 文件读取并停止
if [ -f "$PROJECT_DIR/.pids" ]; then
    read AI_PID BACKEND_PID FRONTEND_PID < "$PROJECT_DIR/.pids"
    # Windows 环境用 taskkill
    if command -v taskkill &> /dev/null; then
        taskkill //F //PID "$AI_PID" 2>/dev/null && echo "✅ AI 中台已停止" || echo "  AI 中台未运行"
        taskkill //F //PID "$BACKEND_PID" 2>/dev/null && echo "✅ 后端已停止" || echo "  后端未运行"
        taskkill //F //PID "$FRONTEND_PID" 2>/dev/null && echo "✅ 前端已停止" || echo "  前端未运行"
    else
        kill $AI_PID 2>/dev/null && echo "✅ AI 中台已停止" || echo "  AI 中台未运行"
        kill $BACKEND_PID 2>/dev/null && echo "✅ 后端已停止" || echo "  后端未运行"
        kill $FRONTEND_PID 2>/dev/null && echo "✅ 前端已停止" || echo "  前端未运行"
    fi
    rm -f "$PROJECT_DIR/.pids"
fi

# 无论 .pids 是否存在，都按端口兜底清理
echo ""
echo "按端口检查并清理..."

kill_by_port 8080 "后端"
kill_by_port 8000 "AI 中台"
kill_by_port 5173 "前端(Vite)"
kill_by_port 5174 "前端(Vite备用)"
kill_by_port 5175 "前端(Vite备用)"
kill_by_port 5176 "前端(Vite备用)"

# 清理 pids 文件
rm -f "$PROJECT_DIR/.pids"

echo ""
echo "完成"
