package com.pedrodalben.bigbangworld.domain;

public class WorldDefinition {
    private String id;
    private String displayName;
    private WorldType type;
    private long seed;
    private String dimensionKey;
    private WorldLifecycleState state;
    private boolean publicAccess;
    private String createdAt;
    private String lastResetAt;
    private int resetCount;
    private SpawnPosition spawn;
    private BorderConfig border;
    private WorldPolicies policies;

    public WorldDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public WorldType getType() { return type; }
    public void setType(WorldType type) { this.type = type; }

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public String getDimensionKey() { return dimensionKey; }
    public void setDimensionKey(String dimensionKey) { this.dimensionKey = dimensionKey; }

    public WorldLifecycleState getState() { return state; }
    public void setState(WorldLifecycleState state) { this.state = state; }

    public boolean isPublicAccess() { return publicAccess; }
    public void setPublicAccess(boolean publicAccess) { this.publicAccess = publicAccess; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getLastResetAt() { return lastResetAt; }
    public void setLastResetAt(String lastResetAt) { this.lastResetAt = lastResetAt; }

    public int getResetCount() { return resetCount; }
    public void setResetCount(int resetCount) { this.resetCount = resetCount; }

    public SpawnPosition getSpawn() { return spawn; }
    public void setSpawn(SpawnPosition spawn) { this.spawn = spawn; }

    public BorderConfig getBorder() { return border; }
    public void setBorder(BorderConfig border) { this.border = border; }

    public WorldPolicies getPolicies() { return policies; }
    public void setPolicies(WorldPolicies policies) { this.policies = policies; }
}
