package bzh.breizhcamp.city.model;

import bzh.breizhcamp.faker.FakerInstance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString()
@RequiredArgsConstructor
public class City {
    private final int size;
    private final String name = FakerInstance.get().gameOfThrones().city();
    private final String id = name.toLowerCase().replaceAll(" ", "_");
}
