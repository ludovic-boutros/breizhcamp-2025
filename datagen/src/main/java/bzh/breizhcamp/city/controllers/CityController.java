package bzh.breizhcamp.city.controllers;

import bzh.breizhcamp.city.model.Car;
import bzh.breizhcamp.city.services.CityService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiParam;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CityController {
    // Keep it simple for this demo
    private static final Map<String, CityService> CITY_SERVICES = new HashMap<>();

    @OpenApi(
            path = "/cities",
            methods = {HttpMethod.GET},
            tags = {"City"},
            summary = "Get all cities",
            description = "Get all cities"
    )
    public static void getAll(@NotNull Context context) {
        context.json(CITY_SERVICES.values().stream().map(service -> service.getCity()).toList());
    }

    @OpenApi(
            path = "/cities",
            methods = {HttpMethod.POST},
            tags = {"City"},
            summary = "Create a new city",
            description = "Create a new city with a given size. Default size: 10.",
            queryParams = {
                    @OpenApiParam(name = "size", type = Integer.class, description = "Size of the city")
            }
    )
    public static void create(@NotNull Context context) throws ExecutionException, InterruptedException {
        int size = context.queryParamAsClass("size", Integer.class).getOrDefault(10);
        CityService cityService = new CityService(size).initKafka();
        CITY_SERVICES.put(cityService.getCity().getName(), cityService);
        context.json(cityService.getCity());
    }

    @OpenApi(
            path = "/cities/{name}",
            methods = {HttpMethod.GET},
            tags = {"City"},
            summary = "Retrieve a city",
            description = "Retrieve a city by its name.",
            pathParams = {
                    @OpenApiParam(name = "name", description = "Name of the city", required = true)
            }
    )
    public static void getOne(@NotNull Context context) {
        String name = context.pathParam("name");
        CityService cityService = CITY_SERVICES.get(name);
        if (cityService == null) {
            context.status(404).json(Map.of("message", "City not found"));
        } else {
            context.json(cityService.getCity());
        }
    }

    @OpenApi(
            path = "/cities/{name}",
            methods = {HttpMethod.DELETE},
            tags = {"City"},
            summary = "Delete a city",
            description = "Delete a city with its name.",
            pathParams = {
                    @OpenApiParam(name = "name", description = "Name of the city", required = true)
            }
    )
    public static void delete(@NotNull Context context) {
        String name = context.pathParam("name");
        CityService cityService = CITY_SERVICES.remove(name);
        if (cityService == null) {
            context.status(404).json(Map.of("message", "City not found"));
        } else {
            cityService.close();
            context.status(200);
        }
    }

    @OpenApi(
            path = "/cities/{name}/cars",
            methods = {HttpMethod.POST},
            tags = {"City", "Car"},
            summary = "Start some cars in a city.",
            description = "Start some cars in a city. These cars may follow another already present car.",
            pathParams = {
                    @OpenApiParam(name = "name", description = "Name of the city", required = true)
            },
            queryParams = {
                    @OpenApiParam(name = "vin", description = "VIN of the car to follow"),
                    @OpenApiParam(name = "count", type = Integer.class, description = "Number of cars to start")
            }
    )
    public static void startNewCars(@NotNull Context context) {
        String name = context.pathParam("name");
        String vin = context.queryParamAsClass("vin", String.class).getOrDefault(null);
        int count = context.queryParamAsClass("count", Integer.class).getOrDefault(1);
        CityService cityService = CITY_SERVICES.get(name);
        if (cityService == null) {
            context.status(404).json(Map.of("message", "City not found"));
        } else {
            List<Car> cars = cityService.startNewCars(vin, count);
            if (cars == null) {
                context.status(404).json(Map.of("message", "Car not found"));
            } else {
                context.json(cars);
            }
        }
    }

    @OpenApi(
            path = "/cities/{name}/cars",
            methods = {HttpMethod.GET},
            tags = {"City", "Car"},
            summary = "Get all car in a city.",
            description = "Get all car in a city.",
            pathParams = {
                    @OpenApiParam(name = "name", description = "Name of the city", required = true)
            }
    )
    public static void getAllCars(@NotNull Context context) {
        String name = context.pathParam("name");
        CityService cityService = CITY_SERVICES.get(name);
        if (cityService == null) {
            context.status(404).json(Map.of("message", "City not found"));
        } else {
            context.json(cityService.cars().values());
        }
    }
}
