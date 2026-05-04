# Stage 1: Build with Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 配置阿里云 Maven 镜像（解决 Maven Central 在国内网络下载截断问题）
RUN mkdir -p /root/.m2 && printf '<settings>\n  <mirrors>\n    <mirror>\n      <id>aliyun</id>\n      <url>https://maven.aliyun.com/repository/public</url>\n      <mirrorOf>central</mirrorOf>\n    </mirror>\n  </mirrors>\n</settings>' > /root/.m2/settings.xml

# 先单独 COPY pom.xml，下载依赖层独立缓存（只有 pom.xml 变化才重新下载）
COPY pom.xml .
RUN mvn -B dependency:go-offline -q

# 再 COPY 源码并编译（源码变化只重新编译，不重新下载依赖）
COPY src ./src
RUN mvn -B -q package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN addgroup -S moodfm && adduser -S moodfm -G moodfm
USER moodfm
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "app.jar"]
