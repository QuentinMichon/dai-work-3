package ch.heigvd.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import ch.heigvd.types.AvionJSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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
        List<String> sorts = ctx.queryParams("sort");

        // fetch data
        if (constructor == null) {
            list = readAvions(JSON_FILEPATH);
        } else {
            list = readAvions(JSON_FILEPATH)
                    .stream()
                    .filter(a -> constructor.equalsIgnoreCase(a.constructor))
                    .collect(Collectors.toList());
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

}
