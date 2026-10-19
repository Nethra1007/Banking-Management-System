# Use a lightweight Java runtime
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the backend source code from your local folder to the conta
COPY backend/src /app/src

# Compile the Java code
RUN mkdir out
RUN javac -d out $(find src -name "*.java")

# Expose the port (Render uses 10000 by default)
EXPOSE 10000

# Command to run the application using port 10000
CMD ["java", "-cp", "out", "com.bank.app.Main", "10000"]