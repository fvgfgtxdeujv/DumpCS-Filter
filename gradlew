#!/bin/sh
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
exec "$JAVA_HOME/bin/java" -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
