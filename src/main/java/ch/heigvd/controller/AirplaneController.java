package ch.heigvd.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import ch.heigvd.types.AvionJSON;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AirplaneController {

    public static final String JSON_FILEPATH = "src/main/java/ch/heigvd/datas/avion.json";

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

        // MUTEX LOCK
        MutexAPI.LOCK.lock();

        try {
            List<AvionJSON> list;

            // filters
            String constructor = ctx.queryParam("constructor");
            List<String> paramCapacity = ctx.queryParams("capacity");
            String paramRange = ctx.queryParam("range");

            // sort conditions
            List<String> sorts = ctx.queryParams("sort");

            // fetch data and apply filter as AND condition
            if (constructor == null) {
                list = readAvions(JSON_FILEPATH);
            } else {
                list = readAvions(JSON_FILEPATH)
                        .stream()
                        .filter(a -> constructor.equalsIgnoreCase(a.constructor))
                        .collect(Collectors.toList());
            }

            if(!paramCapacity.isEmpty()) {
                for (String paramCap : paramCapacity) {
                    boolean less = paramCap.startsWith("-");
                    paramCap = less ? paramCap.substring(1) : paramCap;
                    int capacity = Integer.parseInt(paramCap);

                    if(less) {
                        list = list.stream().filter(a -> a.maxCapacity <= capacity).collect(Collectors.toList());;
                    } else {
                        list = list.stream().filter(a -> a.maxCapacity >= capacity).collect(Collectors.toList());;
                    }
                }
            }

            if(paramRange != null) {
                boolean less = paramRange.startsWith("-");
                paramRange = less ? paramRange.substring(1) : paramRange;
                int range = Integer.parseInt(paramRange);

                if(less) {
                    list = list.stream().filter(a -> a.range <= range).collect(Collectors.toList());
                } else {
                    list = list.stream().filter(a -> a.range >= range).collect(Collectors.toList());
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
        } finally {
            MutexAPI.LOCK.unlock();
        }

    }

    public static void postAvion(Context ctx) {

        // MUTEX LOCK
        MutexAPI.LOCK.lock();

        try {
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
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    public static void deleteAvion(Context ctx) {

        // MUTEX LOCK
        MutexAPI.LOCK.lock();

        try {
            ObjectMapper mapper = new ObjectMapper();

            // read params
            //String icao = ctx.queryParam("icao");
            String constructor = ctx.queryParam("constructor");

            // control params
            if (constructor == null) {
                ctx.result("Invalid constructor need parameter <constructor> xor <icao>")
                        .status(HttpStatus.BAD_REQUEST);
                return;
            }

            // fetch current data
            List<AvionJSON> avions = readAvions(JSON_FILEPATH);

            // delete airplanes
            List<AvionJSON> removed = avions.stream().filter(a -> constructor.equalsIgnoreCase(a.constructor)).toList();

            avions.removeAll(removed);

            // update JSON file
            try (Writer writer = new FileWriter(JSON_FILEPATH, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer);
            ) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(bw, avions);
            } catch (IOException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
                return;
            }

            // send the removed airplanes
            ctx.json(removed);
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }

    /**
     * Brief :
     * modifie l'avion si le champ n'est pas NULL
     *  s'il est NULL, on reprend la valeur existante
     *
     *  controle supplementaire pour la vaidité du range et capacityMax
     *  ICAO verification d'unicité
     **/
    public static void putAvion(Context ctx) {

        // MUTEX LOCK
        MutexAPI.LOCK.lock();

        try {
            ObjectMapper mapper = new ObjectMapper();

            String paramICAO = ctx.queryParam("icao");

            if (paramICAO == null) {
                ctx.result("Invalid icao need parameter <icao>").status(HttpStatus.BAD_REQUEST);
                return;
            }

            // fetch data
            List<AvionJSON> avions = readAvions(JSON_FILEPATH);
            // find requested airplane index (use chatGPT 5.2)
            int index = IntStream.range(0, avions.size())
                    .filter(i -> avions.get(i).ICAO.equalsIgnoreCase(paramICAO))
                    .findFirst()
                    .orElse(-1);

            if(index == -1) {
                ctx.result("No airplane with this ICAO exists").status(HttpStatus.NO_CONTENT);
                return;
            }

            AvionJSON newAvion;
            try {
                newAvion = ctx.bodyAsClass(AvionJSON.class);
            } catch (Exception e) {
                ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
                return;
            }
            AvionJSON oldAvion = avions.get(index);

            // check if ICAO is modified AND if the new icao is unique
            if(newAvion.ICAO != null) {
                if (!avions.stream()
                        .filter(a -> a.ICAO.equalsIgnoreCase(newAvion.ICAO))
                        .toList()
                        .isEmpty()
                        && !newAvion.ICAO.equals(oldAvion.ICAO)) {
                    ctx.result("An airplane with this ICAO already exists").status(HttpStatus.CONFLICT);
                    return;
                } else {
                    // update the ICAO into the companies fleets
                    boolean ret = CompanyController.updateAircraftICAO(oldAvion.ICAO, newAvion.ICAO);
                    if(!ret) {
                        ctx.result("Companies can't be updated").status(HttpStatus.FAILED_DEPENDENCY);
                    }
                }
            } else {
                // no change
                newAvion.ICAO = oldAvion.ICAO;
            }

            if(newAvion.constructor == null) {
                newAvion.constructor = oldAvion.constructor;
            }

            if(newAvion.range != null) {
                if(newAvion.range <= 0) {
                    ctx.result("Invalid range, it must be a positive number").status(HttpStatus.BAD_REQUEST);
                    return;
                }
            } else {
                newAvion.range = oldAvion.range;
            }

            if(newAvion.maxCapacity != null) {
                if(newAvion.maxCapacity <= 0) {
                    ctx.result("Invalid maximum airplane capacity, it must be a positive number").status(HttpStatus.BAD_REQUEST);
                    return;
                }
            } else {
                newAvion.maxCapacity = oldAvion.maxCapacity;
            }

            // update into the list
            avions.set(index, newAvion);

            // update JSON file
            try (Writer writer = new FileWriter(JSON_FILEPATH, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer);
            ) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(bw, avions);
            } catch (IOException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to write JSON file");
                return;
            }

            // return the new airplane
            ctx.status(HttpStatus.ACCEPTED).json(newAvion);
        } finally {
            MutexAPI.LOCK.unlock();
        }
    }
}
