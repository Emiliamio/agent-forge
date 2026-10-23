# 阶段 1：Maven 构建编译
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段 2：轻量级 JRE 运行时（非 root 运行）
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx2048m"

RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 非 root 运行：镜像内以 uid 10001 启动，避免容器逃逸时的宿主机 root 权限
RUN addgroup -g 10001 -S app && adduser -u 10001 -S app -G app

COPY --from=builder /app/target/*.jar app.jar
RUN chown -R app:app /app

USER 10001:10001

EXPOSE 8080

# 容器级健康检查（compose 里另有更严格的一份；此处保证单独 docker run 也能自检）
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/api/system/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]