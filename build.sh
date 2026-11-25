#!/bin/sh
# Build and test WITHOUT Maven (used where Maven Central is not reachable).
# Usage: SERVLET_JAR=/path/javax.servlet-api.jar JUNIT_JAR=... HAMCREST_JAR=... ./build.sh
set -e
OUT=target/classes; TOUT=target/test-classes
rm -rf target; mkdir -p $OUT $TOUT
javac --release 8 -Xlint:all -cp "$SERVLET_JAR" -d $OUT $(find src/main/java -name '*.java')
javac --release 8 -cp "$OUT:$SERVLET_JAR:$JUNIT_JAR:$HAMCREST_JAR" -d $TOUT $(find src/test/java -name '*.java')
java -cp "$OUT:$TOUT:$SERVLET_JAR:$JUNIT_JAR:$HAMCREST_JAR" org.junit.runner.JUnitCore \
  com.example.authclient.MiniJsonTest com.example.authclient.AntPathsTest com.example.authclient.AuthClientConfigTest \
  com.example.authclient.CachingTokenValidatorTest com.example.authclient.HttpTokenIntrospectorTest com.example.authclient.BearerTokenFilterTest
