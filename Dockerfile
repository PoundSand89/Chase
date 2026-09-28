FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY *.java *.class ChaseKeno*.class /app/

RUN javac ChaseKeno.java 2>/dev/null || true

EXPOSE 8080

CMD ["java", "ChaseKeno"]