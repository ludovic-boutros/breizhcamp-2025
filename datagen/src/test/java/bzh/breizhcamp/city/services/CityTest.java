package bzh.breizhcamp.city.services;

import bzh.breizhcamp.city.model.Car;
import bzh.breizhcamp.city.model.City;
import bzh.breizhcamp.city.model.Position;
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
        Assertions.assertEquals("{\"size\":10,\"name\":\"" + city.getName() + "\",\"id\":\"" + city.getId() + "\"}", cityAsString);
    }

    @Test
    public void testMoveLimits() {
        // Given
        CityService cityService = new CityService(1);
        Car car = new Car(cityService.getCity(), null);
        car.setPosition(new Position(0, 1));
        car.setLastPosition(new Position(0, 0));

        // When
        cityService.moveCarToNextPosition(car, null);

        // Then
        Assertions.assertEquals(new Position(1, 1), car.getPosition());
    }

    @Test
    public void testFollowerMove() {
        // Given
        CityService cityService = new CityService(1);
        Car car = new Car(cityService.getCity(), null);
        Car followingCar = new Car(cityService.getCity(), car.getFollowedCarVin());
        car.addFollowingCar(followingCar);

        car.setPosition(new Position(0, 1));
        car.setLastPosition(new Position(0, 0));

        // When
        cityService.moveCarToNextPosition(car, null);

        // Then
        Assertions.assertEquals(new Position(0, 1), followingCar.getPosition());
    }
}