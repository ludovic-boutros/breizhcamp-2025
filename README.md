# BreizhCamp 2025 Talk Lab

![Smart City](./images/smart-city.png)

This lab aims to implement a virtual Smart City that detects cars following each other using Apache Flink SQL and Apache
Kafka.

## Prerequisities

The data generator is a Java application that generates data and sends it to a Kafka topic.
It is built using `Apache Maven`. The project leverages `Apache Flink` and `Apache Kafka` to process the data.
The infrastructure is set up with local containers, though `Confluent Cloud` can be used for simplicity.

## How to run the project

### Start the local infrastructure

```shell
podman compose up -d --remove-orphans
```

### Stop the local infrastructure

```shell
podman compose down
```

### Delete the local data

```shell
rm -Rf ./data/kafka
```

### Check the local infrastructure

```shell
podman ps
open http://localhost:9021
open http://localhost:8081
open http://localhost:7070/swagger
podman compose logs -f datagen
```

Jump to the [City Lab](#Apache-Flink-processing).

### Start Apache Flink SQL shell

```shell
podman compose run sql-client
```

## Application

### Compile the project

```shell
mvn clean package
```

### Start the service

If not already started in a container, you can run the service using:

```shell
java -jar ./datagen/target/datagen-1.0-SNAPSHOT.jar
```

## General

### Create a demo topic

```shell
kafka-topics --create --topic flink-input --bootstrap-server localhost:9092 --partitions 6 --replication-factor 1
```

### Add a schema

```shell
SCHEMA='
{
  "fields": [
    {
      "name": "data",
      "type": "string"
    },
    {
      "name": "id",
      "type": "string"
    }
  ],
  "name": "sampleRecord",
  "namespace": "com.mycorp.mynamespace",
  "type": "record"
}
'

echo "$SCHEMA"

# The schema registry does not accept new lines or spaces in the schema
ONE_LINE_ESCAPED_SCHEMA=$(jq -n --arg schema "$SCHEMA" --arg type "AVRO" '{schemaType: $type, schema: $schema}')

echo "$ONE_LINE_ESCAPED_SCHEMA"

curl -s -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
     --data "$ONE_LINE_ESCAPED_SCHEMA" \
     http://localhost:6081/subjects/flink-input-value/versions
```

### Check schema

```shell
curl -s -X GET http://localhost:6081/subjects/flink-input-value/versions
```

### Produce some data

```shell
kafka-avro-console-producer \
  --broker-list localhost:9092 \
  --topic flink-input \
  --property value.schema='{
    "type":"record",
    "name":"sampleRecord",
    "namespace":"com.mycorp.mynamespace",
    "fields":[
      {"name":"data","type":"string"},
      {"name":"id","type":"string"}
    ]
  }' \
  --property schema.registry.url=http://localhost:6081 \
  --property parse.key=true \
  --property key.separator='|' \
  --property key.serializer=org.apache.kafka.common.serialization.StringSerializer <<EOF
1|{"data": "v1", "id": "1"}
2|{"data": "v2", "id": "2"}
3|{"data": "v3", "id": "3"}
4|{"data": "v4", "id": "4"}
5|{"data": "v5", "id": "5"}
6|{"data": "v6", "id": "6"}
7|{"data": "v7", "id": "7"}
8|{"data": "v8", "id": "8"}
9|{"data": "v9", "id": "9"}
10|{"data": "v10", "id": "10"}
EOF
```

### Map a topic to a Flink table

```sql
CREATE TABLE flinkInput (
   `data` STRING,
   `id` STRING,
   `ts` TIMESTAMP(3) METADATA FROM 'timestamp'
 ) WITH (
   'connector' = 'kafka',
   'topic' = 'flink-input',
   'properties.bootstrap.servers' = 'broker:29092',
   'properties.group.id' = 'test-group',
   'scan.startup.mode' = 'earliest-offset',
   -- UTF-8 string as Kafka keys
   'key.format' = 'raw',
   'key.fields' = 'id',
   'value.format' = 'avro-confluent',
   'value.avro-confluent.url' = 'http://schema-registry:6081',
   'value.fields-include' = 'EXCEPT_KEY'
 );
```

## City Lab

### Create a city

```shell
curl -s -X POST http://localhost:7070/cities?size=10 | jq .
```

### List cities

```shell
curl -s -X GET http://localhost:7070/cities | jq .
```

### Get one

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X GET "http://localhost:7070/cities/${encoded_name}" | jq .
```

### Start a new car in a city

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

### Get all cars in a city

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

### Start a new car in a city following another car

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)
export vin=$(curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq -r '.[0].vin')

curl -s -X POST "http://localhost:7070/cities/${name}/cars?vin=${vin}" | jq .
```

### Complete script

```shell
curl -s -X POST http://localhost:7070/cities?size=10 | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars" | jq .

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .

export vin=$(curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq -r '.[0].vin')

curl -s -X POST "http://localhost:7070/cities/${name}/cars?vin=${vin}" | jq .

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

## Apache Flink processing

### Create a very small city with one car

First delete the current city.

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X DELETE "http://localhost:7070/cities/${encoded_name}" | jq .
curl -s -X GET http://localhost:7070/cities | jq .
```

```shell
curl -s -X POST "http://localhost:7070/cities?size=1" | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars" | jq .

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

### Start the cli

```shell
podman compose run sql-client
```

### Create a table

```sql
CREATE TABLE car_detected (
   `sensorId` STRING,
   `vin` STRING,
   `licensePlate` STRING,
   `city` STRING,
   `x` INT,
   `y` INT,
   `timestamp` TIMESTAMP(3),
   WATERMARK FOR `timestamp` AS `timestamp`
 ) WITH (
   'connector' = 'kafka',
   'topic' = 'car-detected',
   'properties.bootstrap.servers' = 'broker:29092',
   'properties.group.id' = 'flink-car-detection',
   'properties.auto.offset.reset' = 'earliest',
   -- UTF-8 string as Kafka keys
   'key.format' = 'raw',
   'key.fields' = 'sensorId',
   'value.format' = 'avro-confluent',
   'value.avro-confluent.url' = 'http://schema-registry:6081',
   'value.fields-include' = 'EXCEPT_KEY'
 );
```

### View data

```sql
SELECT * FROM car_detected;
```

```sql
SELECT `sensorId`, 
    count(*) AS `count`, 
    MAX(`timestamp`) AS `lastSeen`
FROM `car_detected` 
GROUP BY `sensorId`;
```

## Search cars that are going several times in the same place using pattern recognition

### A first approach

```sql
SELECT *
FROM `car_detected`
    MATCH_RECOGNIZE(
        PARTITION BY `vin`
        ORDER BY `timestamp`
        MEASURES
            A.`city` AS city,
            A.`timestamp` AS A_ts,
            A.`x` AS A_x,
            A.`y` AS A_y,
            C.`timestamp` AS C_ts,
            C.`x` AS C_x,
            C.`y` AS C_y
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (A B* C)
        DEFINE
            B AS B.`x` <> A.`x` OR B.`y` <> A.`y`,
            C AS C.`x` = A.`x` AND C.`y` = A.`y`
    );
```

No result.

Why?

### Watermarks!

```sql
ALTER TABLE `car_detected` ADD `topic_partition` INT METADATA FROM 'partition';
```

```sql
SELECT `topic_partition`, `sensorId`, CURRENT_WATERMARK(`timestamp`) AS `CURRENT_WATERMARK` FROM `car_detected`;
```

This small city contains only **4** different sensors generating **4** different message keys.
This is an example of an **idle** partitions.
Some partitions will never get any message and their watermark will stall.
This leads to a situation where the stream will never make any progress.

- https://rmoff.net/2025/04/25/its-time-we-talked-about-time-exploring-watermarks-and-more-in-flink-sql/
- https://www.youtube.com/watch?v=sdhwpUAjqaI
- https://www.youtube.com/watch?v=PWLjEyJxhg0
- https://current.confluent.io/2024-sessions/timing-is-everything-understanding-event-time-processing-in-flink-sql
- https://github.com/knaufk/advent-of-flink-2024/blob/main/08_current_watermark.md

```sql
SELECT sensorId, CURRENT_WATERMARK(`timestamp`) AS CURRENT_WATERMARK FROM `car_detected`;
```

### Solutions

- Recreate the topic with fewer partitions. (Hoping that the keys will be evenly distributed, or at least that all
  partitions), but would reduce the maximum parallelism. 🫣
- Use another message key.
- Create a bigger city. 😛
- Configure an idle timeout:

```sql
ALTER TABLE `car_detected` SET ('scan.watermark.idle-timeout'='5sec');
```

Delete the current city, again :p.

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X DELETE "http://localhost:7070/cities/${encoded_name}" | jq .
curl -s -X GET http://localhost:7070/cities | jq .
```

Create a bigger city of size 5.

```shell
curl -s -X POST "http://localhost:7070/cities?size=5" | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars" | jq .

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

### State stores

Data corresponding to the beginning of the pattern needs to be kept in memory until the pattern is entirely matched.
If the city is huge, the clause `B*` may stay _opened_ for a very long time, keeping a lot of data in memory especially
with a very high number of cars.

### Create a very huge city with 20000 cars

First delete the current city.

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X DELETE "http://localhost:7070/cities/${encoded_name}" | jq .
curl -s -X GET http://localhost:7070/cities | jq .
```

Then create a new city of size 10000 with 20000 cars.

```shell
curl -s -X POST "http://localhost:7070/cities?size=10000" | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars?count=20000" | jq .
```

### Try to run the first approach

Let's increase the parallelism to 8 in order to be able to run the query on a bigger city.

```sql
SET 'parallelism.default' = '8';
```

```sql
SELECT *
FROM `car_detected`
    MATCH_RECOGNIZE(
        PARTITION BY `vin`
        ORDER BY `timestamp`
        MEASURES
            A.`timestamp` AS A_ts,
            A.`x` AS A_x,
            A.`y` AS A_y,
            C.`timestamp` AS C_ts,
            C.`x` AS C_x,
            C.`y` AS C_y
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (A B* C)
        DEFINE
            B AS B.`x` <> A.`x` OR B.`y` <> A.`y`,
            C AS C.`x` = A.`x` AND C.`y` = A.`y`
    );
```

The task manager in charge of running the query will crash.

Restart it:

```shell
podman compose start taskmanager
```

### A better approach

```sql
SELECT *
FROM `car_detected`
    MATCH_RECOGNIZE(
        PARTITION BY `vin`
        ORDER BY `timestamp`
        MEASURES
            A.`timestamp` AS A_ts,
            A.`x` AS A_x,
            A.`y` AS A_y,
            C.`timestamp` AS C_ts,
            C.`x` AS C_x,
            C.`y` AS C_y
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (A B{3} C)
        DEFINE
            B AS B.`x` <> A.`x` OR B.`y` <> A.`y`,
            C AS C.`x` = A.`x` AND C.`y` = A.`y`
    );
```

Fixing the pattern to a fixed size will help the task manager to keep the state small.
Does not respect the original requirements.

### Another different approach

```sql
SELECT *
FROM `car_detected`
    MATCH_RECOGNIZE(
        PARTITION BY `sensorId`, `vin`
        ORDER BY `timestamp`
        MEASURES
            A.`timestamp` AS A_ts,
            A.`x` AS A_x,
            A.`y` AS A_y,
            B.`timestamp` AS B_ts,
            B.`x` AS B_x,
            B.`y` AS B_y
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (A B) 
        WITHIN INTERVAL '10' SECOND
        DEFINE 
            A AS true,
            B AS true
    );
```

A maximum of 10_000 * 10_000 * 20_000 = 2_000_000_000_000 potential different opened patterns in memory!

But this is not really true because we only have 20_000 cars and each car has a limited number of sensors.
This is a lot of opened patterns, but it is not infinite.

We still need to limit the number of opened patterns to a reasonable number using a `WITHIN INTERVAL` clause.

We also probably need to increase the memory size of the job managers.

Let's produce the result in another output topic:

```shell
kafka-topics --create --topic same-car-detected --bootstrap-server localhost:9092 --partitions 6 --replication-factor 1
```

```sql
CREATE TABLE `same_car_detected` (
   `sensorId` STRING NOT NULL,
   `vin` STRING NOT NULL,
   `licensePlate` STRING NOT NULL,
   `city` STRING NOT NULL,
   `x` INT NOT NULL,
   `y` INT NOT NULL,
   `timestamp_1` TIMESTAMP(3) NOT NULL,
   `timestamp_2` TIMESTAMP(3) NOT NULL,
   WATERMARK FOR `timestamp_2` AS `timestamp_2`
 ) 
 WITH (
   'connector' = 'kafka',
   'topic' = 'same-car-detected',
   'properties.bootstrap.servers' = 'broker:29092',
   'properties.group.id' = 'flink-same-car-detection',
   'properties.auto.offset.reset' = 'earliest',
   -- UTF-8 string as Kafka keys
   'key.format' = 'raw',
   'key.fields' = 'sensorId',
   'value.format' = 'avro-confluent',
   'value.avro-confluent.url' = 'http://schema-registry:6081'
 );
 ```

```sql
INSERT INTO `same_car_detected`
SELECT  *
FROM `car_detected`
    MATCH_RECOGNIZE(
        PARTITION BY `sensorId`, `vin`
        ORDER BY `timestamp`
        MEASURES
            A1.`licensePlate` AS `licensePlate`,
            A1.`city` AS `city`,
            A1.`x` AS `x`,
            A1.`y` AS `y`,
            A1.`timestamp` AS `timestamp_1`,
            A2.`timestamp` AS `timestamp_2`
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (A1 A2) 
        WITHIN INTERVAL '10' SECOND
        DEFINE 
            A1 AS true,
            A2 AS true
    );
```

## Detect cars following another car

First delete the current city.

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X DELETE "http://localhost:7070/cities/${encoded_name}" | jq .
curl -s -X GET http://localhost:7070/cities | jq .
```

Then create a new city of size 100 with 10 cars.

```shell
curl -s -X POST "http://localhost:7070/cities?size=100" | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars?count=10" | jq .
```

Add a car following another car.

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)
export vin=$(curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq -r '.[0].vin')

curl -s -X POST "http://localhost:7070/cities/${name}/cars?vin=${vin}" | jq .
```

### Transform the data in order to get car's paths as a key.

Create an intermediate topic to store the data.

```shell
kafka-topics --create --topic car-paths --bootstrap-server localhost:9092 --partitions 6 --replication-factor 1
```

```sql
CREATE TABLE `car_paths` (
   `vin` STRING NOT NULL,
   `city` STRING NOT NULL,
   `licensePlate` STRING NOT NULL,
   `lastX` INT NOT NULL,
   `lastY` INT NOT NULL,
   `sensorIds` STRING NOT NULL,
   `pathId` STRING NOT NULL,
   `startTime` TIMESTAMP(3) NOT NULL,
   `endTime` TIMESTAMP(3) NOT NULL,
   WATERMARK FOR `endTime` AS `endTime`
 ) 
 WITH (
   'connector' = 'kafka',
   'topic' = 'car-paths',
   'properties.bootstrap.servers' = 'broker:29092',
   'properties.group.id' = 'flink-car-paths',
   'properties.auto.offset.reset' = 'earliest',
   -- UTF-8 string as Kafka keys
   'key.format' = 'raw',
   'key.fields' = 'pathId',
   'value.format' = 'avro-confluent',
   'value.avro-confluent.url' = 'http://schema-registry:6081'
 );
 ```

```sql
INSERT INTO `car_paths`
SELECT `vin`,
       `city`,
       `licensePlate`,
       `lastX`,
       `lastY`,
       CONCAT_WS('|', `sensorIdsArray`[1], 
                      `sensorIdsArray`[2],
                      `sensorIdsArray`[3],
                      `sensorIdsArray`[4],
                      `sensorIdsArray`[5],
                      `sensorIdsArray`[6],
                      `sensorIdsArray`[7],
                      `sensorIdsArray`[8],
                      `sensorIdsArray`[9],
                      `sensorIdsArray`[10]) AS `sensorIds`, 
       MD5(CONCAT_WS('|', `sensorIdsArray`[1], 
                          `sensorIdsArray`[2],
                          `sensorIdsArray`[3],
                          `sensorIdsArray`[4],
                          `sensorIdsArray`[5],
                          `sensorIdsArray`[6],
                          `sensorIdsArray`[7],
                          `sensorIdsArray`[8],
                          `sensorIdsArray`[9],
                          `sensorIdsArray`[10])) AS `pathId`,
       `startTime`,
       `endTime`
FROM car_detected
MATCH_RECOGNIZE(
  PARTITION BY `vin`
  ORDER BY `timestamp`
  MEASURES
    FIRST(A.`licensePlate`) AS `licensePlate`,
    FIRST(A.`city`) AS `city`,
    FIRST(A.`timestamp`) AS `startTime`,
    LAST(A.`timestamp`) AS `endTime`,
    LAST(A.`x`) AS `lastX`,
    LAST(A.`y`) AS `lastY`,
    ARRAY_AGG(A.`sensorId`) AS `sensorIdsArray`
  ONE ROW PER MATCH
  AFTER MATCH SKIP TO NEXT ROW
  PATTERN (A{10})
  DEFINE
    A AS TRUE
);
```

Now we can check if cars have the same path.

```sql
SELECT *
FROM car_paths
MATCH_RECOGNIZE(
  PARTITION BY `pathId`
  ORDER BY `endTime`
  MEASURES
    GOOD_GUY.`city` AS `city`,
    GOOD_GUY.`vin` AS `goodGuyVin`,
    BAD_GUY.`vin` AS `badGuyVin`,
    GOOD_GUY.`endTime` AS `goodGuyEndTime`,
    GOOD_GUY.`lastX` AS `goodGuyLastX`,
    GOOD_GUY.`lastY` AS `goodGuyLastY`,
    BAD_GUY.`endTime` AS `badGuyEndTime`,
    BAD_GUY.`lastX` AS `badGuyLastX`,
    BAD_GUY.`lastY` AS `badGuyLastY`
  ONE ROW PER MATCH
  AFTER MATCH SKIP PAST LAST ROW
  PATTERN (GOOD_GUY BAD_GUY)
  DEFINE
    GOOD_GUY AS TRUE,
    BAD_GUY AS BAD_GUY.`vin` <> GOOD_GUY.`vin`
);
```

## Run the lab on Confluent Cloud

TODO
