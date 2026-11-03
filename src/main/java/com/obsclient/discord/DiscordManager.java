package com.obsclient.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;

public class DiscordManager {
    private static final DiscordRPC rpc = DiscordRPC.INSTANCE;
    private static final String CLIENT_ID = "1567211301812310076";
    private boolean started = false;

    public void start() {
        if (started) return;
        started = true;

        DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.ready = (user) -> System.out.println("[ObsClient] Discord RPC Hazır: " + user.username);

        rpc.Discord_Initialize(CLIENT_ID, handlers, true, "");

        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                rpc.Discord_RunCallbacks();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    rpc.Discord_Shutdown();
                    break;
                }
            }
        }, "ObsClient-Discord-RPC").start();

        updatePresence();
    }

    public void updatePresence() {
        DiscordRichPresence presence = new DiscordRichPresence();
        presence.state = "Anarchy";
        presence.details = "Rat Yedim amq";
        presence.startTimestamp = System.currentTimeMillis() / 1000;
        presence.largeImageKey = "obsidyenlogo";
        presence.largeImageText = "ObsClient - Anarsist";
        rpc.Discord_UpdatePresence(presence);
    }

    public void shutdown() {
        rpc.Discord_Shutdown();
        started = false;
    }
}