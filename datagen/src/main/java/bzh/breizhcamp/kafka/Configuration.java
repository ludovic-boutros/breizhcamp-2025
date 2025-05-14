package bzh.breizhcamp.kafka;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Properties;

public class Configuration {
    public static Properties get() {
        Config conf = ConfigFactory.load();
        Properties properties = new Properties();
        conf.entrySet().forEach(e -> properties.put(e.getKey(), e.getValue().unwrapped().toString()));

        return properties;
    }

    public static String MOVING_RATE_CONFIG_PROPERTY = "car.moving.fixed.rate.seconds";
    public static String KAFKA_CAR_DETECTED_TOPIC_NAME_PROPERTY = "kafka.car.detected.topic.name";
}
