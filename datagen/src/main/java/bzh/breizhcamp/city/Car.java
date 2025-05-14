package bzh.breizhcamp.city;

import bzh.breizhcamp.faker.FakerInstance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@RequiredArgsConstructor
@Setter
@Getter
@ToString
public class Car {
    private final String vin = FakerInstance.get().vehicle().vin();
    private final String licensePlate = FakerInstance.get().vehicle().licensePlate();

    private final City city;
    private final String followedCarVin;
    private Position position;
    private Position lastPosition;
}
