FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /build

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src src

RUN ./mvnw -B -DskipTests clean package


FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /build/target/assortment-0.0.1-SNAPSHOT.jar /app/eltro.jar

ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -Duser.timezone=UTC"

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/eltro.jar"]