package ch.heigvd.types;

import java.util.List;

public class CompanyJSON {
    public String companyICAO;
    public String name;
    public String country;
    public List<AircraftTuple> fleet;

    public static class AircraftTuple {
        public String aircraftICAO;
        public Integer quantity;
    }
}