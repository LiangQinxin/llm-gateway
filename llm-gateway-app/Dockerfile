FROM eclipse-temurin:17-jre
# 作者
LABEL maintainer="liangqinxin"

ENV TZ=Asia/Shanghai
ENV PARAMS=""

# 时区配置
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /
COPY target/llm-gateway-app.jar /llm-gateway-app.jar

# exec 模式，java 成为 pid=1，支持 docker stop 优雅停机
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /llm-gateway-app.jar $PARAMS"]
