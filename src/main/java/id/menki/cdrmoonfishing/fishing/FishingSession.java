package id.menki.cdrmoonfishing.fishing;

import id.menki.cdrmoonfishing.model.FishDefinition;

import java.util.UUID;

public final class FishingSession {
    private final UUID playerId;
    private final FishDefinition fish;
    private final String region;
    private final int depth;
    private final String baitId;
    private final long startedAt;

    private double tension;
    private double progress;
    private int dangerTicks;
    private int behaviorTicks;
    private long lastPulseAt;
    private String activePhaseId;

    public FishingSession(UUID playerId, FishDefinition fish, String region, int depth, String baitId, double startTension) {
        this.playerId = playerId;
        this.fish = fish;
        this.region = region;
        this.depth = depth;
        this.baitId = baitId;
        this.startedAt = System.currentTimeMillis();
        this.tension = startTension;
    }

    public UUID playerId() { return playerId; }
    public FishDefinition fish() { return fish; }
    public String region() { return region; }
    public int depth() { return depth; }
    public String baitId() { return baitId; }
    public long startedAt() { return startedAt; }
    public double tension() { return tension; }
    public void tension(double tension) { this.tension = tension; }
    public double progress() { return progress; }
    public void progress(double progress) { this.progress = progress; }
    public int dangerTicks() { return dangerTicks; }
    public void dangerTicks(int dangerTicks) { this.dangerTicks = dangerTicks; }
    public int behaviorTicks() { return behaviorTicks; }
    public void behaviorTicks(int behaviorTicks) { this.behaviorTicks = behaviorTicks; }
    public long lastPulseAt() { return lastPulseAt; }
    public void lastPulseAt(long lastPulseAt) { this.lastPulseAt = lastPulseAt; }
    public String activePhaseId() { return activePhaseId; }
    public void activePhaseId(String activePhaseId) { this.activePhaseId = activePhaseId; }
}
