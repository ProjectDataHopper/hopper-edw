# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# syntax=docker/dockerfile:1

ARG HOP_IMAGE_VERSION=2.19.0-SNAPSHOT
ARG MYSQL_JDBC_VERSION=8.4.0
ARG SINGLESTORE_JDBC_VERSION=1.2.8

FROM maven:3.9-eclipse-temurin-21 AS build

ARG MYSQL_JDBC_VERSION
ARG SINGLESTORE_JDBC_VERSION

RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY pom.xml README.md ./
COPY src ./src
COPY hop-hsm-jdbc ./hop-hsm-jdbc
COPY docs ./docs
COPY scripts/rewrite-adoc-hrefs.py ./scripts/rewrite-adoc-hrefs.py

RUN mvn -q package -DskipTests

RUN mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy \
      -Dartifact=com.mysql:mysql-connector-j:${MYSQL_JDBC_VERSION} \
      -DoutputDirectory=/jdbc \
  && mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy \
      -Dartifact=com.singlestore:singlestore-jdbc-client:${SINGLESTORE_JDBC_VERSION} \
      -DoutputDirectory=/jdbc

FROM apache/hop:${HOP_IMAGE_VERSION}

USER root

# Customer-360 and other test workflows run small Python helpers via Shell actions.
RUN apk add --no-cache python3

COPY --from=build /jdbc/*.jar /opt/hop/lib/jdbc/
COPY --from=build /src/target/hop-datavault-*.zip /tmp/hop-datavault.zip

RUN unzip -q /tmp/hop-datavault.zip -d /opt/hop \
  && rm /tmp/hop-datavault.zip \
  && chown -R hop:hop /opt/hop/plugins/misc/datavault /opt/hop/lib/jdbc

COPY scripts/docker/entrypoint-init.sh /opt/hop-datavault/entrypoint-init.sh
COPY scripts/docker/entrypoint-prepare.sh /opt/hop-datavault/entrypoint-prepare.sh
RUN chmod +x /opt/hop-datavault/entrypoint-init.sh /opt/hop-datavault/entrypoint-prepare.sh \
  && chown hop:hop /opt/hop-datavault/entrypoint-init.sh /opt/hop-datavault/entrypoint-prepare.sh

USER hop
