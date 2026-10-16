FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

COPY --chmod=0755 mvnw ./mvnw
COPY .mvn/ .mvn/
COPY pom.xml checkstyle.xml ./
COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

RUN groupadd --gid 10001 backend \
    && useradd --uid 10001 --gid backend --no-create-home --shell /usr/sbin/nologin backend

COPY --from=build /build/target/taan-backend-*.jar /app/app.jar

USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/app.jar", "taan.online.Main"]
