# =====================================================================
# elasticsearch-ik:8.12.0 —— 官方 Elasticsearch 8.12.0 + IK 中文分词插件
#
# docker-compose.yml 的 elasticsearch 服务引用镜像 elasticsearch-ik:8.12.0，
# 索引的 ik_max_word / ik_smart 分析器依赖本插件。构建命令见同目录 README.md。
#
# 在线构建（默认，构建期需能访问插件下载源）：
#   docker build -t elasticsearch-ik:8.12.0 dev/elasticsearch-ik
#
# 离线构建（把插件 zip 放进本目录后构建）：
#   docker build -t elasticsearch-ik:8.12.0 dev/elasticsearch-ik
# =====================================================================

# 基础镜像：官方 Elasticsearch。如拉取 Docker Hub 困难，可改用 Elastic 官方仓库：
#   --build-arg ES_IMAGE=docker.elastic.co/elasticsearch/elasticsearch:8.12.0
ARG ES_IMAGE=elasticsearch:8.12.0
FROM ${ES_IMAGE}

# 离线安装用的插件 zip 文件名（相对构建上下文，即本目录）。
# 与 ES 版本必须严格一致（8.12.0 对 8.12.0），否则启动时插件校验失败。
ARG IK_ZIP=elasticsearch-analysis-ik-8.12.0.zip
# 在线安装地址（INFINI 镜像源，返回的即 elasticsearch-analysis-ik-8.12.0.zip）。
# 备选：https://github.com/infinilabs/analysis-ik/releases 对应版本的 zip。
ARG IK_URL=https://get.infini.cloud/elasticsearch/analysis-ik/8.12.0

USER elasticsearch

# 整目录拷入是为了兼容「zip 存在/不存在」两种构建路径，安装后随即清理。
COPY --chown=elasticsearch:0 . /tmp/ik-build/

RUN set -eux; \
    if [ -s "/tmp/ik-build/${IK_ZIP}" ]; then \
        echo "==> 本地存在 ${IK_ZIP}，离线安装 IK 插件"; \
        bin/elasticsearch-plugin install --batch "/tmp/ik-build/${IK_ZIP}"; \
    else \
        echo "==> 本地无插件 zip，在线安装 IK 插件: ${IK_URL}"; \
        bin/elasticsearch-plugin install --batch "${IK_URL}"; \
    fi; \
    rm -rf /tmp/ik-build; \
    bin/elasticsearch-plugin list | grep -i analysis-ik

USER elasticsearch
