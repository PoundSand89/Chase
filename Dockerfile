FROM eclipse-temurin:21-jdk-alpine

# Set working directory
WORKDIR /app

# Copy all Java source and compiled files
COPY *.java *.class ChaseKeno*.class /app/

# Compile Java source if needed
RUN javac ChaseKeno.java 2>/dev/null || true

# Make sure Java is available
RUN java -version

# Expose port (Vercel typically doesn't require this)
EXPOSE 8080

# Default command to run the Java application
CMD ["java", "ChaseKeno"]