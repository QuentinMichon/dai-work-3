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
        if (company == null) return 0;
        if (company.fleet.isEmpty()) return 0;

        int count = 0;

        for(CompanyJSON.AircraftTuple tuple : company.fleet) {
            count += tuple.quantity;
        }
        return count;
    }

    // update ICAO aircraft if the ICAO change
    public static boolean updateAircraftICAO(String oldICAO, String newICAO) {
        List<CompanyJSON> companies = readCompany(JSON_FILEPATH);
        ObjectMapper mapper = new ObjectMapper();

        if(companies.isEmpty()) return true;

        for(CompanyJSON company : companies) {
            if(company.fleet.isEmpty()) continue;

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
                int fleetSize = Integer.parseInt(fleetSizeFilter);

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
        // TODO : add sort by fleet size ?
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
                                              .toList()
                                              .getFirst();

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
}
