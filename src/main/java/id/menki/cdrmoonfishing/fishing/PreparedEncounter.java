package id.menki.cdrmoonfishing.fishing;

import id.menki.cdrmoonfishing.model.FishDefinition;

public record PreparedEncounter(
        FishDefinition fish,
        String region,
        int depth,
        String weather,
        String time,
        String baitId
) {
}
