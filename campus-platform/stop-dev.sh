#!/bin/bash
# 校园生态平台 - 停止开发服务

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "停止服务..."

if [ -f "$PROJECT_DIR/.pids" ]; then
    read AI_PID BACKEND_PID FRONTEND_PID < "$PROJECT_DIR/.pids"
    kill $AI_PID 2>/dev/null && echo "✅ AI 中台已停止" || echo "  AI 中台未运行"
    kill $BACKEND_PID 2>/dev/null && echo "✅ 后端已停止" || echo "  后端未运行"
    kill $FRONTEND_PID 2>/dev/null && echo "✅ 前端已停止" || echo "  前端未运行"
    rm -f "$PROJECT_DIR/.pids"
else
    echo "未找到 PID 文件，尝试按端口停止..."
    # Windows
    if command -v taskkill &> /dev/null; then
        taskkill //F //IM java.exe 2>/dev/null
        taskkill //F //IM node.exe 2>/dev/null
    else
        # Linux/Mac
        kill $(lsof -t -i:8080) 2>/dev/null
        kill $(lsof -t -i:8000) 2>/dev/null
        kill $(lsof -t -i:5173) 2>/dev/null
    fi
fi

echo "完成"
