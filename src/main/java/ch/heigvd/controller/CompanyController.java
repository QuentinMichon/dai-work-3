package ch.heigvd.controller;

import ch.heigvd.types.AvionJSON;
import ch.heigvd.types.CompanyJSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    private static int fleetSize(CompanyJSON company) {
        if (company == null || company.fleet == null || company.fleet.isEmpty()) return 0;
        return company.fleet.stream().mapToInt(t -> t.quantity).sum();
    }

    // update ICAO aircraft if the ICAO change
    public static boolean updateAircraftICAO(String oldICAO, String newICAO) {
        List<CompanyJSON> companies = readCompany(JSON_FILEPATH);
        ObjectMapper mapper = new ObjectMapper();

        if(companies.isEmpty()) return true;

        for(CompanyJSON company : companies) {
            if(company.fleet == null || company.fleet.isEmpty()) continue;

            for(CompanyJSON.AircraftTuple tuple : company.fleet) {
                if(tuple.aircraftICAO.equals(oldICAO)) {
                    tuple.aircraftICAO = newICAO;
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
    }

    //-------------- ENDPOINT FUNCTIONS --------------

    public static void getCompany(Context ctx) {
        List<CompanyJSON> companies;

        // filters
        String countryFilter = ctx.queryParam("country");
        List<String> fleetSizeFilters = ctx.queryParams("fleetSize");

        // sort conditions
        // [companyICAO | name | country | fleetSize]
        List<String> sorts = ctx.queryParams("sort");

        // fetch datas
        companies = readCompany(JSON_FILEPATH);

        // filter
        if (countryFilter != null) {
            companies = companies.stream()
                                 .filter(cmp -> cmp.country.equalsIgnoreCase(countryFilter))
                                 .collect(Collectors.toList());
        }

        if(!fleetSizeFilters.isEmpty()) {
            for(String fleetSizeFilter : fleetSizeFilters) {
                boolean less = fleetSizeFilter.startsWith("-");
                fleetSizeFilter = less ? fleetSizeFilter.substring(1) : fleetSizeFilter;
                try {
                    int fleetSize = Integer.parseInt(fleetSizeFilter);
                } catch (NumberFormatException e) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid quantity format");
                    return;
                }

                if(less) {
                    companies = companies.stream()
                                         .filter(cmp -> fleetSize(cmp) <= fleetSize)
                                         .collect(Collectors.toList());
                } else {
                    companies = companies.stream()
                            .filter(cmp -> fleetSize(cmp) >= fleetSize)
                            .collect(Collectors.toList());
                }
            }
        }

        // sort
        if (!sorts.isEmpty()) {
            Comparator<CompanyJSON> comparator = null;
            Comparator<CompanyJSON> c;

            for (String s : sorts) {
                boolean desc = s.startsWith("-");
                String field = desc ? s.substring(1) : s;

                switch (field) {
                    case "companyICAO":
                        c = Comparator.comparing(cmp -> cmp.companyICAO.toLowerCase());
                        break;
                    case "name":
                        c = Comparator.comparing(cmp -> cmp.name.toLowerCase());
                        break;
                    case "country":
                        c = Comparator.comparing(cmp -> cmp.country.toLowerCase());
                        break;
                    case "fleetSize":
                        c = Comparator.comparing(CompanyController::fleetSize);
                        break;
                    default:
                        ctx.status(HttpStatus.BAD_REQUEST).result("Sort parameters incorrect");
                        return;
                }

                if (desc) c = c.reversed();
                comparator = (comparator == null) ? c : comparator.thenComparing(c);
            }

            if(comparator != null) {
                companies.sort(comparator);
            }
        }

        // send data
        ctx.json(companies);
    }

    public static void postCompany(Context ctx) {
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
                    ctx.status(HttpStatus.CONFLICT).result("Company ICAO already exists");
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

    public static void deleteCompany(Context ctx) {
        ObjectMapper mapper = new ObjectMapper();

        String companyICAO = ctx.queryParam("companyICAO");

        if(companyICAO == null || companyICAO.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter companyICAO not empty");
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
            return;
        }

        // send the removed company
        ctx.json(companyRemoved);
    }

    // Aircraft handler for company
    public static void addAircraft(Context ctx) {
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
            return;
        }

        companies = readCompany(JSON_FILEPATH);
        company = companies.stream()
                            .filter(cmp -> cmp.companyICAO.equalsIgnoreCase(companyICAO))
                            .findFirst()
                            .orElse(null);
        if(company == null) {
            ctx.status(HttpStatus.NOT_FOUND).result("Company does not exist");
            return;
        }
        // company OK

        // check param aircraftICAO
        if(aircraftICAO == null || aircraftICAO.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter aircraftICAO not empty");
            return;
        }

        aircrafts = AirplaneController.readAvions(AirplaneController.JSON_FILEPATH);

        if(aircrafts.stream().filter(a -> a.ICAO.equalsIgnoreCase(aircraftICAO)).count() != 1) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Airplane " +  aircraftICAO + " is not into the catalog");
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
            return;
        }

        ctx.status(HttpStatus.ACCEPTED).json(company);
    }

    public static void sellAircraft(Context ctx) {
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
            return;
        }

        companies = readCompany(JSON_FILEPATH);
        company = companies.stream()
                .filter(cmp -> cmp.companyICAO.equalsIgnoreCase(companyICAO))
                .findFirst()
                .orElse(null);
        if(company == null) {
            ctx.status(HttpStatus.NOT_FOUND).result("Company does not exist");
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
            return;
        }

        // check param aircraftICAO
        if(aircraftICAO == null || aircraftICAO.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid request, need parameter aircraftICAO not empty");
            return;
        }

        aircraftToSell = company.fleet.stream().filter(a -> a.aircraftICAO.equals(aircraftICAO)).findFirst().orElse(null);

        if(aircraftToSell == null) {
            ctx.status(HttpStatus.FAILED_DEPENDENCY).result("This company does not own this aircraft");
            return;
        }

        if(aircraftToSell.quantity < nb) {
            ctx.status(HttpStatus.CONFLICT).result("You can't sell more than "+ aircraftToSell.quantity +" aircrafts");
            return;
        } else if(aircraftToSell.quantity == nb) {
            company.fleet.remove(aircraftToSell);
            ctx.status(HttpStatus.ACCEPTED).json(0);
        } else {
            aircraftToSell.quantity -= nb;
            ctx.status(HttpStatus.ACCEPTED).json(aircraftToSell.quantity);
        }

        // update JSON file
        try (Writer writer = new FileWriter(JSON_FILEPATH, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(writer);
        ) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(bw, companies);
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
            return;
        }
    }
}
