FROM eclipse-temurin:21.0.13_9-jre-jammy
RUN groupadd --gid 10001 bidroom && useradd --uid 10001 --gid bidroom --home /var/lib/bidroom --create-home bidroom
WORKDIR /opt/bidroom
COPY server/target/server-1.1.1.jar /opt/bidroom/server.jar
USER 10001:10001
ENV BIDROOM_DATA_DIR=/var/lib/bidroom
EXPOSE 5001
ENTRYPOINT ["java","-Xms64m","-Xmx256m","-Dfile.encoding=UTF-8","-jar","/opt/bidroom/server.jar"]
