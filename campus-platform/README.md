# 校园生态平台

一站式校园服务平台，整合二手物品交易、社区互动论坛与 AI 校园事务咨询。

## 技术栈

- **后端**: Spring Boot 3 + MyBatis-Plus + Spring Security + JWT
- **前端**: Vue 3 + Element Plus + Pinia + Vue Router
- **AI 中台**: FastAPI + LangChain + Chroma + 云端 LLM API
- **基础设施**: MySQL 8 + Redis 7 + Elasticsearch 8 + Nginx

## 项目结构

```
campus-platform/
├── backend/          # Java 后端 (模块化单体)
│   ├── campus-common/    # 公共模块
│   ├── campus-user/      # 用户模块
│   ├── campus-trade/     # 交易模块
│   ├── campus-forum/     # 论坛模块
│   ├── campus-ai/        # AI 对接模块
│   └── campus-admin/     # 管理后台 (启动入口)
├── frontend/         # Vue 3 前端
├── ai-service/       # Python AI 中台
├── sql/              # 数据库脚本
├── docker-compose.yml
└── nginx.conf
```

## 快速启动

### 1. 启动基础设施
```bash
docker-compose up -d mysql redis elasticsearch
```

### 2. 启动 AI 中台
```bash
cd ai-service
cp .env.example .env  # 填入你的 API Key
pip install -r requirements.txt
python main.py
```

### 3. 启动后端
```bash
cd backend
mvn clean package -DskipTests
java -jar campus-admin/target/*.jar
```

### 4. 启动前端
```bash
cd frontend
npm install
npm run dev
```

### 5. Docker Compose 一键部署
```bash
# 前端先构建
cd frontend && npm run build && cd ..
# 设置环境变量
export OPENAI_API_KEY=sk-your-key
# 启动全部服务
docker-compose up -d
```

## 访问地址

- 前端: http://localhost (Docker) / http://localhost:5173 (开发)
- 后端 API: http://localhost:8080
- Swagger 文档: http://localhost:8080/doc.html
- AI 中台: http://localhost:8000
