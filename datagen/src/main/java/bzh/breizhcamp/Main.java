package bzh.breizhcamp;

import bzh.breizhcamp.controllers.CityController;
import io.javalin.Javalin;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static io.javalin.apibuilder.ApiBuilder.*;

public class Main {

    public static void main(String[] args) {
        var app = Javalin.create(config -> {
                    config.useVirtualThreads = true;
                    config.http.asyncTimeout = 10_000L;
                    config.router.apiBuilder(() -> {
                        path("/cities", () -> {
                            get(CityController::getAll);
                            post(CityController::create);
                            path("/{name}", () -> {
                                get(CityController::getOne);
                                delete(CityController::delete);
                                path("/cars", () -> {
                                            post(CityController::startNewCars);
                                            get(CityController::getAllCars);
                                            path("/following/{vin}", () -> {
                                                post(CityController::startNewCars);
                                            });
                                        }
                                );
                            });
                        });
                    });
                }).exception(Exception.class, (e, ctx) -> {
                    ctx.status(500);
                    ctx.result("Internal Server Error: " + ExceptionUtils.getStackTrace(e));
                })
                .start(7070);
    }
}