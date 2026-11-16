# Build and verify the combined JAR with scripts/build-production.sh first.
FROM eclipse-temurin:21-jre@sha256:18bb4a91a3dea3f03ee31d3755af34dfb40f904fc77ab56cdc2f092e32fff9c2
WORKDIR /app
COPY --chown=10001:10001 backend/target/belsen-ai-0.0.1-SNAPSHOT.jar /app/belsen.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=production BELSEN_BIND_ADDRESS=0.0.0.0
EXPOSE 8081
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/belsen.jar"]
