package ch.heigvd.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import ch.heigvd.types.AvionJSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AirplaneController {

    public static final String JSON_FILEPATH = "src/main/java/ch/heigvd/datas/avion/avion.json";

    public static List<AvionJSON> readAvions(String filename) {
        ObjectMapper mapper = new ObjectMapper();

        try(Reader reader = new FileReader(filename, StandardCharsets.UTF_8);
            BufferedReader avionJSON = new BufferedReader(reader))
        {

            return mapper.readValue(avionJSON, new TypeReference<>(){});

        } catch (IOException e) {
            System.err.println("Error reading : " + filename + e);
            return List.of();
        }
    }

    //-------------- ENDPOINT FUNCTIONS --------------

    public static void getAvions(Context ctx) {

        List<AvionJSON> list;

        String constructor = ctx.queryParam("constructor");
        String paramCapacity = ctx.queryParam("capacity");
        List<String> sorts = ctx.queryParams("sort");

        // fetch data
        if (constructor == null) {
            list = readAvions(JSON_FILEPATH);
        } else {
            list = readAvions(JSON_FILEPATH)
                    .stream()
                    .filter(a -> constructor.equalsIgnoreCase(a.constructor))
                    .toList();
        }

        if(paramCapacity != null) {
            boolean less = paramCapacity.startsWith("-");
            paramCapacity = less ? paramCapacity.substring(1) : paramCapacity;
            int capacity = Integer.parseInt(paramCapacity);

            if(less) {
                list = list.stream().filter(a -> a.maxCapacity < capacity).toList();
            } else {
                list = list.stream().filter(a -> a.maxCapacity > capacity).toList();
            }
        }

        // SORT
        if (!sorts.isEmpty()) {
            Comparator<AvionJSON> comparator = null;
            Comparator<AvionJSON> c;

            for (String s : sorts) {
                boolean desc = s.startsWith("-");
                String field = desc ? s.substring(1) : s;

                switch (field) {
                    case "constructor":
                        c = Comparator.comparing(a -> a.constructor.toLowerCase());
                        break;
                    case "range":
                        c = Comparator.comparingInt(a -> a.range);
                        break;
                    case "icao":
                        c = Comparator.comparing(a -> a.ICAO.toLowerCase());
                        break;
                    default:
                        ctx.status(HttpStatus.BAD_REQUEST).result("Sort parameters incorrect");
                        return;
                }

                if (desc) c = c.reversed();
                comparator = (comparator == null) ? c : comparator.thenComparing(c);
            }

            if(comparator != null) {
                list.sort(comparator);
            }
        }

        // send data
        ctx.json(list);
    }

    public static void postAvion(Context ctx) {
        ObjectMapper mapper = new ObjectMapper();

        // parse JSON body -> AvionJSON
        AvionJSON newAvion;
        try {
            newAvion = ctx.bodyAsClass(AvionJSON.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }

        // validation
        if (newAvion == null
                || newAvion.constructor == null || newAvion.constructor.isBlank()
                || newAvion.ICAO == null || newAvion.ICAO.isBlank()
                || newAvion.range <= 0
                || newAvion.maxCapacity <= 0) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Missing/invalid fields (constructor, ICAO, range>0, maxCapacity>0)");
            return;
        }

        // fetch data
        List<AvionJSON> avions = readAvions(JSON_FILEPATH);

        // check if the ICAO provided is UNIQUE ?
        boolean exists = avions.stream()
                .anyMatch(a -> a.ICAO != null && a.ICAO.equalsIgnoreCase(newAvion.ICAO));
        if (exists) {
            ctx.result("An airplane with this ICAO already exists")
                    .status(HttpStatus.CONFLICT);
            return;
        }

        // add to the list
        List<AvionJSON> updated = new java.util.ArrayList<>(avions);
        updated.add(newAvion);

        // write the avion.json
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(JSON_FILEPATH), StandardCharsets.UTF_8)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, updated);
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
            return;
        }

        // send the airplane added to confirm the process
        ctx.status(HttpStatus.CREATED).json(newAvion);
    }
}
