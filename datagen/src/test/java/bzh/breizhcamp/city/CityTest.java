package bzh.breizhcamp.city;

import bzh.breizhcamp.json.JacksonInstance;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CityTest {

    @Test
    public void testJacksonSerialization() throws JsonProcessingException {
        // Given
        City city = new City(10);

        // When
        String cityAsString = JacksonInstance.get().writeValueAsString(city);

        // Then
        Assertions.assertEquals("{\"size\":10,\"name\":\"" + city.getName() + "\",\"movingRateSeconds\":1,\"carDetectedTopicName\":\"car-detected\"}", cityAsString);
    }

    @Test
    public void testMoveLimits() {
        // Given
        City city = new City(2);
        Car car = new Car(city, null);
        car.setPosition(new Position(0, 1));
        car.setLastPosition(new Position(0, 0));

        // When
        city.moveCarToNextPosition(car, null);

        // Then
        Assertions.assertEquals(new Position(1, 1), car.getPosition());
    }
}