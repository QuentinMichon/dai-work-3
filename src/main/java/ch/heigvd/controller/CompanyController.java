package ch.heigvd.controller;

import ch.heigvd.Main;
import ch.heigvd.types.AvionJSON;
import ch.heigvd.types.CompanyJSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.NotModifiedResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CompanyController {

    public static final String JSON_FILEPATH = "src/main/java/ch/heigvd/datas/company.json";

    // ### CACHE ###
    private static LocalDateTime lastUpdate = LocalDateTime.now();

    public static List<CompanyJSON> readCompany(String filename) {
        ObjectMapper mapper = new ObjectMapper();

        try(Reader reader = new FileReader(filename, StandardCharsets.UTF_8);
            BufferedReader avionJSON = new BufferedReader(reader)) {
            return mapper.readValue(avionJSON, new TypeReference<>(){});
        } catch (IOException e) {
            System.err.println("Error reading : " + filename + e);
            return List.of();
        }
    }

    private static int fleetSize(CompanyJSON company) {
        if (company == null || company.fleet == null || company.fleet.isEmpty()) return 0;
        return company.fleet.stream().mapToInt(t -> t.quantity).sum();
    }

    // update ICAO aircraft if the ICAO change
    public static boolean updateAircraftICAO(String oldICAO, String newICAO) {
        MutexAPI.LOCK.lock();

        try {
            List<CompanyJSON> companies = readCompany(JSON_FILEPATH);
            ObjectMapper mapper = new ObjectMapper();

            if(companies.isEmpty()) return true;

            for(CompanyJSON company : companies) {
                if(company.fleet == null || company.fleet.isEmpty()) continue;

                for(CompanyJSON.AircraftTuple tuple : company.fleet) {
                    if(tuple.aircraftICAO.equals(oldICAO)) {
                        tuple.aircraftICAO = newICAO;
                        // update last updated cache info
                        lastUpdate = LocalDateTime.now();
                    }
                }
            }

            // write the avion.json
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(JSON_FILEPATH), StandardCharsets.UTF_8)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, companies);
                return true;
            } catch (IOException e) {
                return false;
            }
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    //-------------- ENDPOINT FUNCTIONS --------------

    public static void getCompany(Context ctx) {
        MutexAPI.LOCK.lock();

        try {
            List<CompanyJSON> companies;

            Main.logger("getCompany", ctx.url());

            // get header : If-Modified-Since
            LocalDateTime headerIfModifiedSince = ctx.headerAsClass("If-Modified-Since", LocalDateTime.class).getOrDefault(null);

            if(headerIfModifiedSince == null || headerIfModifiedSince.isBefore(lastUpdate)) {
                // ### CACHE MISS ###
                Main.logger("getCompany", "CACHE MISS");
                // fetch datas
                companies = readCompany(JSON_FILEPATH);

                // sort data by its ICAO
                Comparator<CompanyJSON> comparator = Comparator.comparing(cmp -> cmp.companyICAO.toLowerCase());
                companies.sort(comparator);

                // save the timestamp
                lastUpdate = LocalDateTime.now();

                // send data
                ctx.json(companies).header("Last-Modified", lastUpdate.toString());
            } else {
                // ### CACHE HIT ###
                Main.logger("getCompany", "CACHE HIT");
                ctx.status(HttpStatus.NOT_MODIFIED).header("Last-Modified", lastUpdate.toString());
            }

        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    public static void postCompany(Context ctx) {
        MutexAPI.LOCK.lock();

        try {
            ObjectMapper mapper = new ObjectMapper();

            // parse JSON body -> CompanyJSON
            CompanyJSON newCompany;
            try {
                newCompany = ctx.bodyAsClass(CompanyJSON.class);
            } catch (Exception e){
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
                Main.logger("postCompany", "Invalid JSON body");
                return;
            }

            // validation
            if(newCompany == null
                    || newCompany.name == null  || newCompany.name.isBlank()
                    || newCompany.companyICAO == null || newCompany.companyICAO.isBlank()
                    || newCompany.country == null || newCompany.country.isBlank()
                    || newCompany.fleet == null)
            {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
                Main.logger("postCompany", "Invalid JSON body");
                return;
            }

            // companyICAO unique validation
            List<CompanyJSON> companies = readCompany(JSON_FILEPATH);
            if(!companies.isEmpty()) {
                for(CompanyJSON company : companies) {
                    if(company.companyICAO.equals(newCompany.companyICAO)) {
                        ctx.status(HttpStatus.CONFLICT).result("Company ICAO already exists");
                        Main.logger("postCompany", "Company ICAO already exists");
                        return;
                    }
                }
            }

            // aircraftICAO and quantity validation
            if(!newCompany.fleet.isEmpty()) {
                List<AvionJSON> avions = AirplaneController.readAvions(AirplaneController.JSON_FILEPATH);

                List<String> aircraftICAOs = avions.stream()
                        .map(a -> a.ICAO)
                        .toList();

                for(CompanyJSON.AircraftTuple aircraftTuple : newCompany.fleet) {
                    if(!aircraftICAOs.contains(aircraftTuple.aircraftICAO)) {
                        ctx.status(HttpStatus.CONFLICT).result("The aircraft " + aircraftTuple.aircraftICAO + " use an ICAO that does not exist");
                        Main.logger("postCompany", "The aircraft " + aircraftTuple.aircraftICAO + " use an ICAO that does not exist");
                        return;
                    } else {
                        if(aircraftTuple.quantity <= 0) {
                            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body, quantity must be greater than 0");
                            Main.logger("postCompany", "Invalid JSON body, quantity must be greater than 0");
                            return;
                        }
                    }
                }
            }

            // add the company to the list
            companies.add(newCompany);

            // write the company.json
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(JSON_FILEPATH), StandardCharsets.UTF_8)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, companies);
            } catch (IOException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
                Main.logger("postCompany", "Failed to write JSON file");
                return;
            }

            // response
            ctx.status(HttpStatus.CREATED).json(newCompany);
            Main.logger("postCompany", "Successfully posted Company");
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    public static void deleteCompany(Context ctx) {

        MutexAPI.LOCK.lock();

        try {
            ObjectMapper mapper = new ObjectMapper();

            String companyICAO = ctx.queryParam("companyICAO");

            if(companyICAO == null || companyICAO.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter companyICAO not empty");
                Main.logger("postCompany", "Invalid request, need parameter companyICAO not empty");
                return;
            }

            // fetch data
            List<CompanyJSON> companies = readCompany(JSON_FILEPATH);

            // delete company
            CompanyJSON companyRemoved = companies.stream()
                    .filter(cmp -> cmp.companyICAO.equalsIgnoreCase(companyICAO))
                    .findFirst()
                    .orElse(null);

            if(companyRemoved == null) {
                ctx.status(HttpStatus.NOT_FOUND).result("This company does not exists");
                Main.logger("postCompany", "This company does not exists");
                return;
            }
            companies.remove(companyRemoved);

            // update JSON file
            try (Writer writer = new FileWriter(JSON_FILEPATH, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer);
            ) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(bw, companies);
            } catch (IOException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
                Main.logger("postCompany", "Failed to write JSON file");
                return;
            }

            // update cache timestamp
            lastUpdate = LocalDateTime.now();

            // send the removed company
            ctx.json(companyRemoved);
            Main.logger("postCompany", "Successfully deleted Company");
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    // Aircraft handler for company
    public static void addAircraft(Context ctx) {

        MutexAPI.LOCK.lock();

        try {
            ObjectMapper mapper = new ObjectMapper();

            // company/{company}/?aircraftICAO=xxxx&quantity=zz

            String companyICAO = ctx.pathParam("cmpICAO");
            String aircraftICAO = ctx.queryParam("aircraftICAO");
            String quantity = ctx.queryParam("quantity");

            int nb;
            List<CompanyJSON> companies;
            CompanyJSON company;
            List<AvionJSON> aircrafts;

            // check company
            if(companyICAO.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter company not empty");
                Main.logger("postCompany", "Invalid request, need parameter company not empty");
                return;
            }

            companies = readCompany(JSON_FILEPATH);
            company = companies.stream()
                    .filter(cmp -> cmp.companyICAO.equalsIgnoreCase(companyICAO))
                    .findFirst()
                    .orElse(null);
            if(company == null) {
                ctx.status(HttpStatus.NOT_FOUND).result("Company does not exist");
                Main.logger("postCompany", "Company does not exist");
                return;
            }
            // company OK

            // check param aircraftICAO
            if(aircraftICAO == null || aircraftICAO.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter aircraftICAO not empty");
                Main.logger("postCompany", "Invalid request, need parameter aircraftICAO not empty");
                return;
            }

            aircrafts = AirplaneController.readAvions(AirplaneController.JSON_FILEPATH);

            if(aircrafts.stream().filter(a -> a.ICAO.equalsIgnoreCase(aircraftICAO)).count() != 1) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Airplane " +  aircraftICAO + " is not into the catalog");
                Main.logger("postCompany", "Airplane " + aircraftICAO + " is not into the catalog");
                return;
            }
            // avion ICAO OK

            // check quantity (optional
            try {
                if(quantity == null || quantity.isBlank()) {
                    nb = 1;
                } else {
                    nb = Integer.parseUnsignedInt(quantity);
                }
            } catch (NumberFormatException e) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid quantity format");
                Main.logger("postCompany", "Invalid quantity format");
                return;
            }
            // quantity OK

            // modification de company
            CompanyJSON.AircraftTuple tuple = company.fleet.stream()
                    .filter(a -> a.aircraftICAO.equalsIgnoreCase(aircraftICAO))
                    .findFirst()
                    .orElse(null);
            if(tuple != null) {
                tuple.quantity += nb;
            } else {
                CompanyJSON.AircraftTuple newTuple = new CompanyJSON.AircraftTuple();
                newTuple.aircraftICAO = aircraftICAO;
                newTuple.quantity = nb;
                company.fleet.add(newTuple);
            }

            // update JSON file
            try (Writer writer = new FileWriter(JSON_FILEPATH, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer);
            ) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(bw, companies);
            } catch (IOException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
                Main.logger("postCompany", "Failed to write JSON file");
                return;
            }

            // update cache timestamp
            lastUpdate = LocalDateTime.now();

            ctx.status(HttpStatus.ACCEPTED).json(company);
            Main.logger("postCompany", "Successfully wrote Company");
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    public static void sellAircraft(Context ctx) {

        MutexAPI.LOCK.lock();

        try {
            ObjectMapper mapper = new ObjectMapper();

            String companyICAO = ctx.pathParam("cmpICAO");
            String aircraftICAO = ctx.queryParam("aircraftICAO");
            String quantity = ctx.queryParam("quantity");

            List<CompanyJSON> companies;
            CompanyJSON company;
            CompanyJSON.AircraftTuple aircraftToSell;
            int nb;

            // check company
            if(companyICAO.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter company not empty");
                Main.logger("sellAircraft", "Invalid request, need parameter company not empty");
                return;
            }

            companies = readCompany(JSON_FILEPATH);
            company = companies.stream()
                    .filter(cmp -> cmp.companyICAO.equalsIgnoreCase(companyICAO))
                    .findFirst()
                    .orElse(null);
            if(company == null) {
                ctx.status(HttpStatus.NOT_FOUND).result("Company does not exist");
                Main.logger("sellAircraft", "Company does not exist");
                return;
            }

            // check quantity (optional
            try {
                if(quantity == null || quantity.isBlank()) {
                    nb = 1;
                } else {
                    nb = Integer.parseUnsignedInt(quantity);
                }
            } catch (NumberFormatException e) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid quantity format");
                Main.logger("sellAircraft", "Invalid quantity format");
                return;
            }

            // check param aircraftICAO
            if(aircraftICAO == null || aircraftICAO.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter aircraftICAO not empty");
                Main.logger("sellAircraft", "Invalid request, need parameter aircraftICAO not empty");
                return;
            }

            aircraftToSell = company.fleet.stream().filter(a -> a.aircraftICAO.equals(aircraftICAO)).findFirst().orElse(null);

            if(aircraftToSell == null) {
                ctx.status(HttpStatus.FAILED_DEPENDENCY).result("This company does not own this aircraft");
                Main.logger("sellAircraft", "This company does not own this aircraft");
                return;
            }

            if(aircraftToSell.quantity < nb) {
                ctx.status(HttpStatus.CONFLICT).result("You can't sell more than "+ aircraftToSell.quantity +" aircraft");
                Main.logger("sellAircraft", "You can't sell more than "+ aircraftToSell.quantity +" aircraft");
                return;
            } else if(aircraftToSell.quantity == nb) {
                company.fleet.remove(aircraftToSell);
            } else {
                aircraftToSell.quantity -= nb;
            }

            // update JSON file
            try (Writer writer = new FileWriter(JSON_FILEPATH, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer);
            ) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(bw, companies);
            } catch (IOException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
                Main.logger("sellAircraft", "Failed to write JSON file");
                return;
            }

            // update cache timestamp
            lastUpdate = LocalDateTime.now();

            ctx.json(company).status(HttpStatus.ACCEPTED);
            Main.logger("sellAircraft", "Successfully wrote Company");
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }
}
