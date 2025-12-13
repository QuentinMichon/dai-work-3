package ch.heigvd.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import ch.heigvd.types.AvionJSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

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



}
