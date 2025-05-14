package bzh.breizhcamp.faker;

import net.datafaker.Faker;

public class FakerInstance {
    private static final Faker INSTANCE = new Faker();

    public static Faker get() {
        return INSTANCE;
    }
}
