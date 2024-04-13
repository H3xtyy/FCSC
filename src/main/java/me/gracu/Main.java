package me.gracu;

import com.google.gson.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends ListenerAdapter {
    private final String channelId;
    private final int checkInterval;
    private final int playerThreshold;
    private Message sentMessage;

    public static void main(String[] args) throws IOException {
        Config config = new Config();

        JDABuilder.createDefault(config.getToken())
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI)
                .addEventListeners(new Main(config.getChannelId(), config.getCheckInterval(), config.getPlayerThreshold()))
                .build();
    }

    public Main(String channelId, int checkInterval, int playerThreshold) {
        this.channelId = channelId;
        this.checkInterval = checkInterval;
        this.playerThreshold = playerThreshold;
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Bot is ready!");
        JDA jda = event.getJDA();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ServerDataFetcher fetcher = new ServerDataFetcher();
                    JsonObject json = fetcher.getServerData();
                    int playerCount = fetcher.getPlayerCount(json);
                    MessageEmbed message = fetcher.generateMessage(json, playerThreshold);
                    if (message != null && playerCount >= playerThreshold) {
                        if (sentMessage == null) {
                            sentMessage = MessageUtils.sendMessage(jda, channelId, message);
                        } else {
                            MessageUtils.editMessage(sentMessage, message);
                        }
                    } else if (sentMessage != null) {
                        MessageUtils.deleteMessage(sentMessage);
                        sentMessage = null;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, checkInterval * 1000);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String messageContent = event.getMessage().getContentDisplay();

        if (messageContent.equalsIgnoreCase("/fearbotstop")) {
            Member member = event.getMember();
            if (member != null && member.hasPermission(Permission.ADMINISTRATOR)) {
                event.getMessage().delete().queue();
                MessageUtils.shutdownBot(event.getJDA(), sentMessage);
            } else {
                event.getMessage().delete().queue();
            }
        }
    }
}