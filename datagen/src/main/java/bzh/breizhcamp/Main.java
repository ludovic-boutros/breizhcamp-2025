package bzh.breizhcamp;

import bzh.breizhcamp.city.controllers.CityController;
import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static io.javalin.apibuilder.ApiBuilder.*;

@Slf4j
public class Main {

    public static void main(String[] args) {
        var app = Javalin.create(config -> {
                    config.useVirtualThreads = true;
                    config.registerPlugin(new OpenApiPlugin(
                            openApiConfig -> openApiConfig
                                    .withDefinitionConfiguration((version, definition) -> {
                                        definition
                                                .withInfo(info -> {
                                                            info.setTitle("BreizhCamp API");
                                                            info.setVersion("1.0");
                                                        }
                                                );
                                    })
                    ));
                    config.registerPlugin(new SwaggerPlugin());
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
        log.info("Check out Swagger UI docs at http://localhost:7070/swagger");
    }
}