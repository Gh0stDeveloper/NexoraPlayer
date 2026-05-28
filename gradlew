#!/bin/sh
# Gradle wrapper startup script for UNIX
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="Gradle"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
die () { echo; echo "ERROR: $*"; echo; exit 1; }
JAVACMD="${JAVA_HOME}/bin/java"
[ -f "$JAVACMD" ] || JAVACMD="java"
eval set -- $DEFAULT_JVM_OPTS
exec "$JAVACMD" "$@" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
