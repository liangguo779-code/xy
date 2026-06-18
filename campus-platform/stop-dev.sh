#!/bin/bash
# 校园生态平台 - 停止微服务 (不杀 Docker 容器)

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  校园生态平台 - 停止微服务"
echo "=========================================="
echo ""

# 按端口杀进程
kill_by_port() {
    local port=$1
    local name=$2
    # 尝试用 netstat + taskkill (Windows Git Bash)
    local pid
    pid=$(netstat -ano 2>/dev/null | grep ":${port} " | grep LISTEN | awk '{print $5}' | head -1)
    if [ -n "$pid" ] && [ "$pid" != "0" ]; then
        taskkill //F //PID "$pid" > /dev/null 2>&1 && echo "  ✅ ${name} (PID: ${pid}) 已停止" || echo "  ⚠️  ${name} 停止失败"
        return 0
    fi
    # 尝试用 lsof (macOS/Linux)
    pid=$(lsof -ti :${port} 2>/dev/null | head -1)
    if [ -n "$pid" ]; then
        kill -9 "$pid" 2>/dev/null && echo "  ✅ ${name} (PID: ${pid}) 已停止" || echo "  ⚠️  ${name} 停止失败"
        return 0
    fi
    echo "  - ${name} 未运行 (端口 ${port})"
}

# 从 .pids 文件读取 PID
if [ -f "$PROJECT_DIR/.pids" ]; then
    read -r AI_PID GATEWAY_PID USER_PID TRADE_PID FORUM_PID AI_CONSULT_PID ADMIN_PID FRONTEND_PID < "$PROJECT_DIR/.pids"

    kill_pid() {
        local pid=$1
        local name=$2
        if [ -z "$pid" ] || [ "$pid" = "" ]; then return; fi
        taskkill //F //PID "$pid" > /dev/null 2>&1 && echo "  ✅ ${name} (PID: ${pid}) 已停止" || \
        kill -9 "$pid" 2>/dev/null && echo "  ✅ ${name} (PID: ${pid}) 已停止" || \
        echo "  - ${name} 未运行"
    }

    echo "通过 PID 停止服务:"
    kill_pid "$AI_PID" "AI 中台"
    kill_pid "$GATEWAY_PID" "Gateway"
    kill_pid "$USER_PID" "User Service"
    kill_pid "$TRADE_PID" "Trade Service"
    kill_pid "$FORUM_PID" "Forum Service"
    kill_pid "$AI_CONSULT_PID" "AI Consult"
    kill_pid "$ADMIN_PID" "Admin Service"
    kill_pid "$FRONTEND_PID" "前端"
    rm -f "$PROJECT_DIR/.pids"
    echo ""
fi

# 按端口兜底清理
echo "按端口检查并清理:"
kill_by_port 9000 "Gateway"
kill_by_port 8081 "User Service"
kill_by_port 8082 "Trade Service"
kill_by_port 8083 "Forum Service"
kill_by_port 8084 "AI Consult"
kill_by_port 8085 "Admin Service"
kill_by_port 8000 "AI 中台"
kill_by_port 5173 "前端(Vite)"

rm -f "$PROJECT_DIR/.pids"

echo ""
echo "Docker 容器未停止 (mysql, redis, elasticsearch, nacos)"
echo "如需停止: docker-compose down"
echo ""
echo "完成"
