package bzh.breizhcamp.controllers;

import bzh.breizhcamp.city.Car;
import bzh.breizhcamp.city.City;
import bzh.breizhcamp.json.JacksonInstance;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CityController {
    // Keep it simple for this demo
    private static final Map<String, City> CITIES = new HashMap<>();

    public static void getAll(@NotNull Context context) throws JsonProcessingException {
        context.result(JacksonInstance.get().writeValueAsString(CITIES.values()));
    }

    public static void create(@NotNull Context context) throws JsonProcessingException, ExecutionException, InterruptedException {
        CityCreationRequest cityCreationRequest = context.bodyAsClass(CityCreationRequest.class);
        City city = new City(cityCreationRequest.getSize()).initKafka();
        CITIES.put(city.getName(), city);
        context.result(JacksonInstance.get().writeValueAsString(city));
    }

    public static void getOne(@NotNull Context context) throws JsonProcessingException {
        String name = context.pathParam("name");
        City city = CITIES.get(name);
        if (city == null) {
            context.status(404);
        } else {
            context.result(JacksonInstance.get().writeValueAsString(city));
        }
    }

    public static void delete(@NotNull Context context) {
        String name = context.pathParam("name");
        City city = CITIES.remove(name);
        if (city == null) {
            context.status(404);
        } else {
            city.close();
            CITIES.remove(name);
            context.status(200);
        }
    }

    public static void startNewCars(@NotNull Context context) throws JsonProcessingException {
        String name = context.pathParam("name");
        String vin = context.pathParamMap().get("vin");
        int count = Integer.parseInt(context.pathParamMap().getOrDefault("count", "1"));
        City city = CITIES.get(name);
        if (city == null) {
            context.status(404);
        } else {
            List<Car> cars = city.startNewCars(vin, count);
            if (cars == null) {
                context.status(404);
            } else {
                context.result(JacksonInstance.get().writeValueAsString(cars));
            }
        }
    }

    public static void getAllCars(@NotNull Context context) throws JsonProcessingException {
        String name = context.pathParam("name");
        City city = CITIES.get(name);
        if (city == null) {
            context.status(404);
        } else {
            context.result(JacksonInstance.get().writeValueAsString(city.cars().values()));
        }
    }

    private static class CityCreationRequest {
        private int size;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
