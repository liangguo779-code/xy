# elasticsearch-ik 镜像构建

`docker-compose.yml` 的 `elasticsearch` 服务引用镜像 `elasticsearch-ik:8.12.0`（官方 Elasticsearch 8.12.0 + IK 中文分词插件）。
帖子/商品索引的 `ik_max_word` / `ik_smart` 分析器依赖该插件，本目录提供可复现的镜像构建脚本。

## 构建

镜像名与标签必须与 compose 保持一致（`elasticsearch-ik:8.12.0`），在**项目根目录**执行：

```bash
# 在线安装（构建期需联网，从 IK_URL 下载插件 zip）
docker build -t elasticsearch-ik:8.12.0 dev/elasticsearch-ik
```

离线 / 弱网环境：先下载插件 zip 放到本目录（文件名保持 `elasticsearch-analysis-ik-8.12.0.zip`），再执行同一条构建命令，
构建脚本检测到本地 zip 会自动改走离线安装：

```bash
# 插件下载源（任选其一，zip 版本必须与 ES 版本一致）
curl -LO https://get.infini.cloud/elasticsearch/analysis-ik/8.12.0
# 重命名为标准文件名（下载源 Content-Disposition 为 elasticsearch-analysis-ik-8.12.0.zip）
mv 8.12.0 elasticsearch-analysis-ik-8.12.0.zip

docker build -t elasticsearch-ik:8.12.0 dev/elasticsearch-ik
```

可选参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `ES_IMAGE` | `elasticsearch:8.12.0` | 基础镜像；Docker Hub 拉不动时改 `docker.elastic.co/elasticsearch/elasticsearch:8.12.0` |
| `IK_ZIP` | `elasticsearch-analysis-ik-8.12.0.zip` | 本地离线插件 zip 文件名（相对本目录） |
| `IK_URL` | `https://get.infini.cloud/elasticsearch/analysis-ik/8.12.0` | 在线安装地址 |

## 验证

```bash
# 插件已安装
docker run --rm elasticsearch-ik:8.12.0 bin/elasticsearch-plugin list

# 启动后确认 ik 分词可用（compose 起的 ES 映射了 9200 端口）
docker compose up -d elasticsearch
curl "http://localhost:9200/_analyze" -H 'Content-Type: application/json' \
  -d '{"analyzer":"ik_max_word","text":"学分认定与重学流程"}'
```
