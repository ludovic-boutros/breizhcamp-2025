package bzh.breizhcamp.city.model;

import bzh.breizhcamp.faker.FakerInstance;
import lombok.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
@Setter
@Getter
@ToString
public class Car {
    private final String vin = FakerInstance.get().vehicle().vin();
    private final String licensePlate = FakerInstance.get().vehicle().licensePlate();
    @Getter(AccessLevel.NONE)
    private final Set<Car> followingCars = new HashSet<>();

    private final City city;
    private final String followedCarVin;
    private Position position;
    private Position lastPosition;

    public void addFollowingCar(Car car) {
        followingCars.add(car);
    }

    public Set<Car> getFollowingCars() {
        return Collections.unmodifiableSet(followingCars);
    }
}
