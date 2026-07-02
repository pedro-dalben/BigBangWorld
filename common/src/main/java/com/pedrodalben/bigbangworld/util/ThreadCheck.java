package com.pedrodalben.bigbangworld.util;

import net.minecraft.server.MinecraftServer;

public final class ThreadCheck {
    private ThreadCheck() {}

    public static void requireServerThread(MinecraftServer server, String operation) {
        if (server == null) {
            throw new IllegalStateException(
                "BigBangWorld: Server is null for operation '" + operation + "'"
            );
        }
        if (!server.isSameThread()) {
            Thread current = Thread.currentThread();
            throw new IllegalStateException(
                "BigBangWorld: Operation '" + operation + "' must run on server thread. " +
                "Current thread: " + current.getName() + " (id=" + current.getId() + ")"
            );
        }
    }
}
