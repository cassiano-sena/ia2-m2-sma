#!/usr/bin/env bash
# Run SMA Generator Rental GUI
# Requires: Java 21 JDK (from IntelliJ IDEA JBR)

MVN="C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd"
JAVA_HOME="C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.2/jbr"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"

echo "Starting SMA Generator Rental GUI..."
echo "JAVA_HOME=$JAVA_HOME"
cmd //c "\"$MVN\" -f pom.xml compile exec:java -Dexec.mainClass=br.univali.cc.ia2.m2.sma.gui.TrafficControlApp -Dexec.cleanupDaemonThreads=false"