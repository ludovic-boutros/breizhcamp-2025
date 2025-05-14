package bzh.breizhcamp.city;

import bzh.breizhcamp.avro.CarDetectedEvent;
import bzh.breizhcamp.faker.FakerInstance;
import bzh.breizhcamp.kafka.Configuration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static bzh.breizhcamp.kafka.Configuration.KAFKA_CAR_DETECTED_TOPIC_NAME_PROPERTY;
import static bzh.breizhcamp.kafka.Configuration.MOVING_RATE_CONFIG_PROPERTY;

@Getter
@Slf4j
@ToString(exclude = {"kafkaProducer"})
public class City implements Closeable {
    private static final Random R = new Random();

    private final int size;
    private final String name = FakerInstance.get().gameOfThrones().city();
    private final int movingRateSeconds;
    private final String carDetectedTopicName;

    @Getter(AccessLevel.NONE)
    private KafkaProducer<String, CarDetectedEvent> kafkaProducer;

    @Getter(AccessLevel.NONE)
    private final Map<String, Car> cars = new HashMap<>();

    @Getter(AccessLevel.NONE)
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    public City(int size) {
        Properties configuration = Configuration.get();

        this.size = size;
        this.carDetectedTopicName = (String) configuration.get(KAFKA_CAR_DETECTED_TOPIC_NAME_PROPERTY);
        movingRateSeconds = Integer.parseInt((String) configuration.get(MOVING_RATE_CONFIG_PROPERTY));
    }

    public City initKafka() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = KafkaAdminClient.create(Configuration.get())) {
            if (!adminClient.listTopics().names().get().contains(carDetectedTopicName)) {
                log.info("Creating topic {}...", carDetectedTopicName);
                adminClient.createTopics(Collections.singletonList(
                                new org.apache.kafka.clients.admin.NewTopic(carDetectedTopicName,
                                        Optional.of(1),
                                        Optional.empty())))
                        .all().get();
            }
        }
        kafkaProducer = new KafkaProducer<>(Configuration.get());
        return this;
    }

    public List<Car> startNewCars() {
        return startNewCars(null, 1);
    }

    public List<Car> startNewCars(String followedCarVin, int count) {
        Car followedCar;
        if (followedCarVin != null) {
            followedCar = cars.get(followedCarVin);
            if (followedCar == null) {
                return null;
            }
        } else {
            followedCar = null;
        }

        List<Car> retValue = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Car car = new Car(this, followedCarVin);

            Position position = new Position(R.nextInt(size), R.nextInt(size));
            car.setPosition(position);
            car.setLastPosition(position);

            log.info("New car({}) added to city: [{}]", car.getLicensePlate(), car.getPosition());
            executorService.scheduleAtFixedRate(() -> {
                moveCarToNextPosition(car, followedCar);
                carDetectedAtPosition(car);
            }, 0, movingRateSeconds, TimeUnit.SECONDS);

            cars.put(car.getVin(), car);

            retValue.add(car);
        }
        return retValue;
    }

    void moveCarToNextPosition(Car car, Car followedCar) {
        if (followedCar != null) {
            if (car.getPosition().compareTo(followedCar.getPosition()) > 1) {
                // Let some distance between the two cars ;)
                car.setLastPosition(car.getPosition());
                car.setPosition(new Position(
                        followedCar.getLastPosition().getX(),
                        followedCar.getLastPosition().getY()));
            }
        } else {
            Position currentPosition = car.getPosition();
            Position lastPosition = car.getLastPosition();

            boolean moveX = ableToMove(lastPosition.getX(), currentPosition.getX()) &&
                    // If we cannot move on Y we need to move on X
                    (!ableToMove(lastPosition.getY(), currentPosition.getY()) || R.nextBoolean());

            int currentCoordinate = moveX ? currentPosition.getX() : currentPosition.getY();
            int lastCoordinate = moveX ? lastPosition.getX() : lastPosition.getY();
            int nextCoordinate = getNextCoordinate(currentCoordinate, lastCoordinate);

            car.setLastPosition(car.getPosition());

            car.setPosition(new Position(
                    moveX ? nextCoordinate : currentPosition.getX(),
                    moveX ? currentPosition.getY() : nextCoordinate)
            );
        }
        log.trace("Car({}) moved to new position: [{}]", car.getLicensePlate(), car.getPosition());
    }

    private void carDetectedAtPosition(Car car) {
        CarDetectedEvent event = new CarDetectedEvent(
                sensorId(car),
                car.getVin(),
                car.getLicensePlate(),
                this.name,
                car.getPosition().getX(),
                car.getPosition().getY(),
                Instant.now()
        );

        if (kafkaProducer != null) {
            ProducerRecord record = new ProducerRecord<>(carDetectedTopicName,
                    sensorId(car),
                    event);
            try {
                kafkaProducer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("Exception while sending event to Kafka:", exception);
                    } else {
                        log.debug("Car({}) detected at position: [{}]", car.getLicensePlate(), car.getPosition());
                    }
                });
            } catch (Exception exception) {
                log.error("Exception while sending event to Kafka:", exception);
            }
        }
    }

    @NotNull
    private String sensorId(Car car) {
        return String.join("-", this.name, Integer.toString(car.getPosition().getX()), Integer.toString(car.getPosition().getY()));
    }

    private int getNextCoordinate(int currentCoordinate, int lastCoordinate) {
        int nextCoordinate;
        if (currentCoordinate == 0) {
            nextCoordinate = 1;
        } else if (currentCoordinate == size - 1) {
            nextCoordinate = size - 2;
        } else if (currentCoordinate == lastCoordinate) {
            nextCoordinate = R.nextBoolean() ? currentCoordinate + 1 : currentCoordinate - 1;
        } else {
            nextCoordinate = currentCoordinate + (currentCoordinate - lastCoordinate);
        }
        return nextCoordinate;
    }


    boolean ableToMove(int lastPosition, int currentPosition) {
        return (currentPosition > 0 && currentPosition < size - 1)
                || (currentPosition == 0 && lastPosition == 0)
                || (currentPosition == size - 1 && lastPosition == size - 1);
    }

    @Override
    public void close() {
        if (kafkaProducer != null) {
            this.kafkaProducer.close();
        }
        executorService.shutdown();
        try {
            log.info("Waiting for executor service to shutdown...");
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Car> cars() {
        return Collections.unmodifiableMap(cars);
    }
}
