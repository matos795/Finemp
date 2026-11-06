FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 finemp && useradd --uid 10001 --gid finemp --no-create-home finemp
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/finemp-api-0.0.1-SNAPSHOT.jar /app/finemp-api.jar
COPY --from=build --chown=10001:10001 /build/target/finemp-api-0.0.1-SNAPSHOT-staging-provisioner.jar /app/staging-provisioner.jar
COPY --from=build --chown=10001:10001 /build/target/finemp-api-0.0.1-SNAPSHOT-production-admin-bootstrap.jar /app/production-admin-bootstrap.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/finemp-api.jar"]
