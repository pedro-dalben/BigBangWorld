package com.pedrodalben.bigbangworld.domain;

public class WorldPolicies {
    private boolean allowHomeCreation;
    private boolean allowWaystones;
    private boolean allowClaims;
    private boolean allowChunkLoading;

    public WorldPolicies() {}

    public WorldPolicies(boolean allowHomeCreation, boolean allowWaystones, boolean allowClaims, boolean allowChunkLoading) {
        this.allowHomeCreation = allowHomeCreation;
        this.allowWaystones = allowWaystones;
        this.allowClaims = allowClaims;
        this.allowChunkLoading = allowChunkLoading;
    }

    public boolean isAllowHomeCreation() { return allowHomeCreation; }
    public void setAllowHomeCreation(boolean allowHomeCreation) { this.allowHomeCreation = allowHomeCreation; }

    public boolean isAllowWaystones() { return allowWaystones; }
    public void setAllowWaystones(boolean allowWaystones) { this.allowWaystones = allowWaystones; }

    public boolean isAllowClaims() { return allowClaims; }
    public void setAllowClaims(boolean allowClaims) { this.allowClaims = allowClaims; }

    public boolean isAllowChunkLoading() { return allowChunkLoading; }
    public void setAllowChunkLoading(boolean allowChunkLoading) { this.allowChunkLoading = allowChunkLoading; }
}
