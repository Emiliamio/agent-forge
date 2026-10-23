# 阶段 1：Node.js 构建静态产物
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 阶段 2：Nginx 生产托管（非 root 镜像：以 uid 101 运行，监听 8080）
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY --from=builder /app/public/agentforge-widget.js /usr/share/nginx/html/agentforge-widget.js
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 显式声明非 root（基础镜像本身已是 uid 101，此处让静态检查也可见）
USER 101:101

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=6 \
    CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]