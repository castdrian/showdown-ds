#!/bin/sh
set -eu
set -f
APP_HOME=$(CDPATH= cd "$(dirname "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
set -- "-Dorg.gradle.appname=gradlew" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
eval "set -- $(printf '%s\n' "$DEFAULT_JVM_OPTS ${JAVA_OPTS:-} ${GRADLE_OPTS:-}" | xargs -n1 | sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' | tr '\n' ' ')" '"$@"'
exec "$JAVACMD" "$@"
