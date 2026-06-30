#!/bin/bash
# 校园生态平台 - 停止单体应用 (不杀 Docker 容器)

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  校园生态平台 - 停止服务"
echo "=========================================="
echo ""

# 从 .pids 文件读取 PID
if [ -f "$PROJECT_DIR/.pids" ]; then
    read -r AI_PID APP_PID FRONTEND_PID < "$PROJECT_DIR/.pids"

    kill_pid() {
        local pid=$1
        local name=$2
        if [ -z "$pid" ] || [ "$pid" = "" ]; then return; fi
        # Windows: 使用 taskkill 强制终止进程树
        if taskkill //F //T //PID "$pid" > /dev/null 2>&1; then
            echo "  ✅ ${name} (PID: ${pid}) 已停止"
            return 0
        fi
        # Unix: 使用 kill -9
        if kill -9 "$pid" 2>/dev/null; then
            echo "  ✅ ${name} (PID: ${pid}) 已停止"
            return 0
        fi
        echo "  - ${name} 未运行"
        return 1
    }

    echo "通过 PID 停止服务:"
    kill_pid "$AI_PID" "AI 中台"
    kill_pid "$APP_PID" "campus-app"
    kill_pid "$FRONTEND_PID" "前端"
    rm -f "$PROJECT_DIR/.pids"
    echo ""
fi

# 按进程名兜底清理
echo "按进程名检查并清理:"

kill_by_pattern() {
    local pattern=$1
    local name=$2
    local pids
    # Windows: 使用 tasklist 查找进程
    pids=$(tasklist 2>/dev/null | grep -i "$pattern" | awk '{print $2}' | tr -d '\r')
    if [ -z "$pids" ]; then
        echo "  - ${name} 无残留进程"
        return 0
    fi
    for pid in $pids; do
        if taskkill //F //T //PID "$pid" > /dev/null 2>&1; then
            echo "  ✅ ${name} (PID: ${pid}) 已清理"
        fi
    done
}

# 清理所有相关进程
kill_by_pattern "campus-app-1.0.0-SNAPSHOT.jar" "campus-app"
kill_by_pattern "maven" "Maven"
kill_by_pattern "main.py" "AI 中台"
kill_by_pattern "vite" "前端(Vite)"
kill_by_pattern "node" "Node.js"

echo ""

# 按端口兜底清理
echo "按端口检查并清理:"

kill_by_port() {
    local port=$1
    local name=$2
    local pid
    # Windows Git Bash
    pid=$(netstat -ano 2>/dev/null | grep ":${port} " | grep LISTEN | awk '{print $5}' | head -1)
    if [ -n "$pid" ] && [ "$pid" != "0" ]; then
        if taskkill //F //T //PID "$pid" > /dev/null 2>&1; then
            echo "  ✅ ${name} (PID: ${pid}) 已停止"
        else
            echo "  ⚠️  ${name} 停止失败"
        fi
        return 0
    fi
    # macOS/Linux
    pid=$(lsof -ti :${port} 2>/dev/null | head -1)
    if [ -n "$pid" ]; then
        if kill -9 "$pid" 2>/dev/null; then
            echo "  ✅ ${name} (PID: ${pid}) 已停止"
        else
            echo "  ⚠️  ${name} 停止失败"
        fi
        return 0
    fi
    echo "  - ${name} 未运行 (端口 ${port})"
}

kill_by_port 9000 "campus-app"
kill_by_port 8000 "AI 中台"
kill_by_port 5173 "前端(Vite)"

# 等待进程完全停止
echo ""
echo "等待进程完全停止..."
sleep 2

# 验证端口已释放
verify_port_free() {
    local port=$1
    local name=$2
    if netstat -ano 2>/dev/null | grep -q ":${port}.*LISTEN"; then
        echo "  ⚠️  ${name} 端口 ${port} 仍被占用"
        return 1
    fi
    return 0
}

verify_port_free 9000 "campus-app"
verify_port_free 8000 "AI 中台"
verify_port_free 5173 "前端(Vite)"

rm -f "$PROJECT_DIR/.pids"

echo ""
echo "Docker 容器未停止 (mysql, redis, elasticsearch)"
echo "如需停止: docker-compose down"
echo ""
echo "✅ 停止完成"
