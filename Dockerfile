# Etapa de compilación: descarga dependencias y empaqueta el JAR.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q package -DskipTests

# Etapa de ejecución: solo JRE y el JAR, sin usuario root.
FROM eclipse-temurin:25-jre AS run
WORKDIR /app
RUN useradd --system --create-home pos
COPY --from=build /app/target/*.jar /app/app.jar
USER pos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
