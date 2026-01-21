package ch.heigvd;

import ch.heigvd.controller.AirplaneController;
import ch.heigvd.controller.CompanyController;
import io.javalin.Javalin;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    public static final int PORT = 8080;

    public static void main(String[] args) {
        Javalin app = Javalin.create( config -> {
            config.validation.register(LocalDateTime.class, LocalDateTime::parse);
        });


        // begin juste pour le front end
        app.options("/*", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "*");
            ctx.status(204);
        });

        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
        });
        // fin

        app.get("/avions", AirplaneController::getAvions);
        app.post("/avions", AirplaneController::postAvion);
        app.delete("/avions", AirplaneController::deleteAvion);
        app.put("/avions", AirplaneController::putAvion);

        app.post("/company", CompanyController::postCompany);
        app.get("/company", CompanyController::getCompany);
        app.delete("/company", CompanyController::deleteCompany);

        app.put("/company/{cmpICAO}/buy", CompanyController::addAircraft);
        app.put("/company/{cmpICAO}/sell", CompanyController::sellAircraft);

        app.start(PORT);
    }

    public static void logger(String fct, String message) {
        LocalDateTime now = LocalDateTime.now();
        System.out.println(now.format(DateTimeFormatter.ISO_TIME) + " [" + fct  + "] LOGGER - " + message);
    }
}