# 本机是amd64 → 打出amd64镜像；本机arm → 打出arm64镜像
docker build -t liangqinxin/llm-gateway-app:1.0-jdk17 -f ./Dockerfile .
