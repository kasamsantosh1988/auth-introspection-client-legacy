#!/bin/sh
# Build and test WITHOUT Maven (used where Maven Central is not reachable).
# Usage: SERVLET_JAR=... JUNIT_JAR=... HAMCREST_JAR=... [SPRING_JARS=boot.jar:boot-autoconfigure.jar:spring-core.jar:spring-context.jar] ./build.sh
# Without SPRING_JARS the optional Spring Boot auto-configuration (com.example.authclient.spring) is skipped.
set -e
OUT=target/classes; TOUT=target/test-classes
rm -rf target; mkdir -p $OUT $TOUT
SRC=$(find src/main/java -name '*.java')
if [ -z "$SPRING_JARS" ]; then SRC=$(find src/main/java -name '*.java' -not -path '*/spring/*'); fi
javac --release 8 -Xlint:all -cp "$SERVLET_JAR:$SPRING_JARS" -d $OUT $SRC
javac --release 8 -cp "$OUT:$SERVLET_JAR:$JUNIT_JAR:$HAMCREST_JAR" -d $TOUT $(find src/test/java -name '*.java')
java -cp "$OUT:$TOUT:$SERVLET_JAR:$JUNIT_JAR:$HAMCREST_JAR" org.junit.runner.JUnitCore \
  com.example.authclient.MiniJsonTest com.example.authclient.AntPathsTest com.example.authclient.AuthClientConfigTest \
  com.example.authclient.CachingTokenValidatorTest com.example.authclient.HttpTokenIntrospectorTest com.example.authclient.BearerTokenFilterTest
