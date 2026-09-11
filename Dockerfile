# ============================================================
#  多阶段构建：前端打包 → 塞进后端 → 打成一个可以跑的镜像
#
#  构建镜像：  docker build -t bili-web .
#  运行容器：  docker run -d -p 8080:8080 -v bili-uploads:/app/uploads bili-web
#  然后浏览器打开 http://localhost:8080
# ============================================================

# ---------- 阶段 1：构建 Vue 前端 ----------
FROM node:20-alpine AS frontend
WORKDIR /app/frontend

# 先只拷贝依赖清单，利用 Docker 缓存层，改代码时不用重新装依赖
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --include=dev

COPY frontend/ ./
RUN npm run build

# ---------- 阶段 2：构建 Spring Boot 后端 ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app/backend

# 同理：先把 pom 拉下来，依赖能缓存住
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline

COPY backend/src ./src
# 把前端产物放进后端静态目录，这样一个 jar 就能提供完整网站
COPY --from=frontend /app/frontend/dist/ ./src/main/resources/static/
RUN mvn -B package -DskipTests

# ---------- 阶段 3：运行（只要一个 JRE，镜像很小） ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8"

COPY --from=backend /app/backend/target/bili-web.jar ./app.jar

# 用户头像、昵称等资料保存在这里，挂个数据卷就不会随容器删除而丢失
VOLUME ["/app/uploads"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/api/home > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
