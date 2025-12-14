package ch.heigvd.controller;

import ch.heigvd.types.AvionJSON;
import ch.heigvd.types.CompanyJSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CompanyController {

    public static final String JSON_FILEPATH = "src/main/java/ch/heigvd/datas/company.json";

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

    //-------------- ENDPOINT FUNCTIONS --------------

    public static void postAvion(Context ctx) {
        ObjectMapper mapper = new ObjectMapper();

        // parse JSON body -> CompanyJSON
        CompanyJSON newCompany;
        try {
            newCompany = ctx.bodyAsClass(CompanyJSON.class);
        } catch (Exception e){
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
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
            return;
        }

        // companyICAO unique validation
        List<CompanyJSON> companies = readCompany(JSON_FILEPATH);
        if(!companies.isEmpty()) {
            for(CompanyJSON company : companies) {
                if(company.companyICAO.equals(newCompany.companyICAO)) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Company ICAO already exists");
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
                    return;
                } else {
                    if(aircraftTuple.quantity <= 0) {
                        ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body, quantity must be greater than 0");
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
            return;
        }

        // response
        ctx.status(HttpStatus.CREATED).json(newCompany);
    }
}
