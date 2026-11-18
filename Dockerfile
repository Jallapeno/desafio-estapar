FROM gradle:8.12.1-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/*-all.jar /app/application.jar
USER 10001:10001
EXPOSE 3003
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
