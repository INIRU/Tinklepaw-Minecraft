package dev.nyaru.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class HudState {
    public static volatile int balance = 0;
    public static volatile String job = "";
    public static volatile String jobDisplay = "";
    public static volatile int level = 1;
    public static volatile int xp = 0;
    public static volatile int xpToNext = 100;

    public static void update(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("balance")) balance = obj.get("balance").getAsInt();
            if (obj.has("job")) {
                job = obj.get("job").getAsString();
                jobDisplay = switch (job) {
                    case "miner" -> "광부";
                    case "farmer" -> "농부";
                    default -> job.isEmpty() ? "없음" : job;
                };
            }
            if (obj.has("level")) level = obj.get("level").getAsInt();
            if (obj.has("xp")) xp = obj.get("xp").getAsInt();
            if (obj.has("xpToNext")) xpToNext = obj.get("xpToNext").getAsInt();
        } catch (Exception ignored) {}
    }

    private HudState() {}
}
