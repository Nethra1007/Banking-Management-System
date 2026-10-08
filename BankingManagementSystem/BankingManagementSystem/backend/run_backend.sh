#!/bin/sh
# Compiles and starts the Java API on http://localhost:8080
cd "$(dirname "$0")" || exit 1
rm -rf out && mkdir out
javac -d out $(find src -name "*.java") || exit 1
java -cp out com.bank.app.Main
