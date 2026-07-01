package com.pedrodalben.bigbangworld.api;

public class BigBangWorldApi {
    private static WorldPolicyApi instance;

    public static WorldPolicyApi get() {
        return instance;
    }

    public static void register(WorldPolicyApi api) {
        instance = api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }
}
