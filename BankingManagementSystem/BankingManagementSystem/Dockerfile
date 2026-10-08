FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY backend/src /app/src
RUN mkdir out
RUN javac -d out $(find src -name "*.java")
EXPOSE 8080
CMD ["java", "-cp", "out", "com.bank.app.Main", "8080"]
