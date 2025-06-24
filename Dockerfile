FROM eclipse-temurin:21

ENV APP_HOME /app
WORKDIR ${APP_HOME}

RUN apt-get update
RUN apt-get -y install wget
RUN apt-get -y install jq

COPY ./datagen/target/datagen-1.0-SNAPSHOT.jar ${APP_HOME}/
WORKDIR ${APP_HOME}
RUN ["java", "-jar", "datagen-1.0-SNAPSHOT.jar"]
