# 阶段 1：Maven 构建编译
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段 2：轻量级 JRE 运行时
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx2048m"

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
