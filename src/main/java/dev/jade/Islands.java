package dev.jade;

import java.util.*;

final class Islands {
    static final String BLOOD = "Catacombs - Blood Room";
    static final String OTHER = "其他地点";
    static final Map<String,String> byPlace = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        for (var place:Locations.places) byPlace.put(place.id(),group(place));
        byPlace.put(BLOOD,"Catacombs");
    }
    static String of(String id,boolean rift) {
        return byPlace.getOrDefault(id,rift?"Rift Dimension":OTHER);
    }
    private static String group(Locations.Place p) {
        String id=p.id();
        return switch (p.region()) {
            case "Private Locations" -> id;
            case "Ship Navigator" -> id.equals("Backwater Bayou")?id:"Lotus Atoll";
            case "Gold Mine and Deep Caverns" -> id.equals("Gold Mine")?id:"Deep Caverns";
            case "Dungeons" -> id;
            case "" -> switch(id) {
                case "Hub", "Hub Island", "Hub Villagers", "Hub Well", "Official Buildings", "Personal Vault",
                     "Pet Care", "Shops", "Taylor's Shop", "Hexatorum", "Golden Dante Statue", "Event Stand",
                     "Event Square", "The People's Voting", "Security Hall" -> "Hub Island";
                case "C&C Minecarts Co.", "Cult of the Fallen Star", "Lava Springs" -> "Dwarven Mines";
                case "Catacombs Puzzle Rooms","Dungeons" -> "Catacombs";
                case "Woods Race" -> "The Park";
                case "Crimson Isle","Dwarven Mines","Galatea","Glacite Mineshafts","Rift Dimension","The Park" -> id;
                default -> OTHER;
            };
            default -> p.region();
        };
    }
}
