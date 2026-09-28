package id.menki.cdrmoonfishing.fishing;

import id.menki.cdrmoonfishing.model.FishDefinition;

import java.util.UUID;

public final class FishingSession {
    private final UUID playerId;
    private final FishDefinition fish;
    private final String region;
    private final int depth;
    private final long startedAt;

    private double tension;
    private double progress;
    private int dangerTicks;
    private long lastPulseAt;

    public FishingSession(UUID playerId, FishDefinition fish, String region, int depth, double startTension) {
        this.playerId = playerId;
        this.fish = fish;
        this.region = region;
        this.depth = depth;
        this.startedAt = System.currentTimeMillis();
        this.tension = startTension;
    }

    public UUID playerId() {
        return playerId;
    }

    public FishDefinition fish() {
        return fish;
    }

    public String region() {
        return region;
    }

    public int depth() {
        return depth;
    }

    public long startedAt() {
        return startedAt;
    }

    public double tension() {
        return tension;
    }

    public void tension(double tension) {
        this.tension = tension;
    }

    public double progress() {
        return progress;
    }

    public void progress(double progress) {
        this.progress = progress;
    }

    public int dangerTicks() {
        return dangerTicks;
    }

    public void dangerTicks(int dangerTicks) {
        this.dangerTicks = dangerTicks;
    }

    public long lastPulseAt() {
        return lastPulseAt;
    }

    public void lastPulseAt(long lastPulseAt) {
        this.lastPulseAt = lastPulseAt;
    }
}
