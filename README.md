# BreizhCamp 2025 Talk Lab

## How to run the project

### Start the local infrastructure

```shell
podman compose up -d --remove-orphans
```

### Check the local infrastructure

```shell
podman ps
open http://localhost:9021
open http://localhost:8081
```

### Start Apache Flink SQL shell

```shell
podman compose run sql-client
```

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

### Create a city

```shell
curl -s  -d '{ "size" : 10 }' -H "Content-Type: application/json" -X POST http://localhost:7070/cities | jq .
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

curl -s -X POST "http://localhost:7070/cities/${name}/cars/following/${vin}" | jq .
```

### Complete script

```shell
curl -s  -d '{ "size" : 10 }' -H "Content-Type: application/json" -X POST http://localhost:7070/cities | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars" | jq .

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .

export vin=$(curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq -r '.[0].vin')

curl -s -X POST "http://localhost:7070/cities/${name}/cars/following/${vin}" | jq .

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

## Apache Flink processing

### Create a very small city with one car

```shell
curl -s  -d '{ "size" : 2 }' -H "Content-Type: application/json" -X POST http://localhost:7070/cities | jq .
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

### Search cars that are going several times in the same place using pattern recognition

## A first approach

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

If the city is huge, the pattern `B*` may stay opened for a very long time, keeping data in memory especially with a
very high number of cars.

### Create a very huge city with 100000 cars

First delete the current city.

```shell
name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

curl -s -X DELETE "http://localhost:7070/cities/${encoded_name}" | jq .
curl -s -X GET http://localhost:7070/cities | jq .
```

Then create a new city with 100000 cars.

```shell
curl -s  -d '{ "size" : 10000 }' -H "Content-Type: application/json" -X POST http://localhost:7070/cities | jq .
curl -s -X GET http://localhost:7070/cities | jq .

name=$(curl -s -X GET http://localhost:7070/cities | jq -r '.[0].name')
export encoded_name=$(echo -n "$name" | jq -s -R -r @uri)

for i in {0..20000}   
do
  curl -s -X POST "http://localhost:7070/cities/${encoded_name}/cars" | jq .
done

curl -s -X GET "http://localhost:7070/cities/${encoded_name}/cars" | jq .
```

### Try to run the first approach

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

## A better approach

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