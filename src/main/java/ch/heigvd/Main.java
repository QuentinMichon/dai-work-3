package ch.heigvd;

import ch.heigvd.controller.AirplaneController;
import ch.heigvd.controller.CompanyController;
import io.javalin.Javalin;

public class Main {
    public static final int PORT = 8080;

    public static void main(String[] args) {
        Javalin app = Javalin.create();

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
}