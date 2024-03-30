package me.gracu;

import com.google.gson.*;
import net.dv8tion.jda.api.EmbedBuilder;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.io.*;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

public class Main extends ListenerAdapter {
    private final String channelId;
    private final int checkInterval;
    private final int playerThreshold;
    private Message sentMessage;

    public static void main(String[] args) throws IOException {
        Properties properties = loadProperties();
        String token = properties.getProperty("token");
        String channelId = properties.getProperty("channel_id");
        int checkInterval = Integer.parseInt(properties.getProperty("check_interval"));
        int playerThreshold = Integer.parseInt(properties.getProperty("player_threshold"));

        JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI)
                .addEventListeners(new Main(channelId, checkInterval, playerThreshold))
                .build();
    }

    private static Properties loadProperties() throws IOException {
        createConfigFileIfNotExists();
        Properties properties = new Properties();
        properties.load(new FileInputStream("config.properties"));
        return properties;
    }

    private static void createConfigFileIfNotExists() {
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
                writer.write("token=DISCORD_BOT_TOKEN\n");
                writer.write("channel_id=CHANNEL_ID\n");
                writer.write("check_interval=300\n");
                writer.write("player_threshold=2\n");
                writer.close();
                System.out.println("config.properties file created. Please edit it with your bot token and channel ID.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
                    JsonObject json = getServerData();
                    int playerCount = getPlayerCount(json);
                    MessageEmbed message = generateMessage(json);
                    if (message != null && playerCount >= playerThreshold) {
                        if (sentMessage == null) {
                            sentMessage = sendMessage(jda, channelId, message);
                        } else {
                            editMessage(sentMessage, message);
                        }
                    } else if (sentMessage != null) {
                        deleteMessage(sentMessage);
                        sentMessage = null;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, checkInterval * 1000);
    }

    private JsonObject getServerData() throws IOException {
//        acceptSelfSignedCertificates(); //UNCOMMENT THIS IF FEAR COMMUNITY SITE CERTIFICATE IS EXPIRED
        Document doc = Jsoup.connect("https://fear-community.org/api/serverlist-new.php").get();
        return extractServerData(doc);
    }

    private void acceptSelfSignedCertificates() {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JsonObject extractServerData(Document doc) {
        Element table = doc.select("table#serverlist").first();
        if (table == null) {
            System.err.println("Table with id 'serverlist' not found in HTML.");
            return new JsonObject();
        }

        Elements rows = table.select("tr");
        JsonObject json = new JsonObject();
        JsonArray serversArray = new JsonArray();
        for (int i = 1; i < rows.size(); i++) {
            Elements cols = rows.get(i).select("td");
            if (cols.size() >= 4) {
                String serverName = cols.get(1).text();
                String players = cols.get(3).text();
                JsonObject serverObject = new JsonObject();
                serverObject.addProperty("name", serverName);
                serverObject.addProperty("players", players);
                serversArray.add(serverObject);
            }
        }
        json.add("servers", serversArray);
        return json;
    }

    private String[] parsePlayersCount(JsonObject server) {
        String players = server.get("players").getAsString();
        return players.split("/");
    }

    private int getPlayerCount(JsonObject json) {
        if (!json.has("servers")) {
            System.err.println("No 'servers' key found in JSON data.");
            return 0;
        }

        JsonArray servers = json.getAsJsonArray("servers");
        int playerCount = 0;
        for (JsonElement element : servers) {
            JsonObject server = element.getAsJsonObject();
            String[] parts = parsePlayersCount(server);
            if (parts.length == 2) {
                int currentPlayers = Integer.parseInt(parts[0].trim());
                playerCount += currentPlayers;
            }
        }
        return playerCount;
    }

    private MessageEmbed generateMessage(JsonObject json) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        JsonArray servers = json.getAsJsonArray("servers");
        boolean hasValidServers = false;

        StringBuilder serverNames = new StringBuilder();
        StringBuilder playerCounts = new StringBuilder();

        for (JsonElement element : servers) {
            JsonObject server = element.getAsJsonObject();
            String[] parts = parsePlayersCount(server);

            if (parts.length == 2) {
                int currentPlayers = Integer.parseInt(parts[0].trim());

                if (currentPlayers >= playerThreshold) {
                    hasValidServers = true;
                    if (!serverNames.isEmpty()) {
                        serverNames.append("\n");
                        playerCounts.append("\n");
                    }
                    serverNames.append(server.get("name").getAsString());
                    playerCounts.append(parts[0].trim()).append("/").append(parts[1].trim());
                }
            }
        }

        if (hasValidServers) {
            embedBuilder.setTitle("Come play with us! :video_game:");
            embedBuilder.addField("Servers:", serverNames.toString(), true);
            embedBuilder.addField("Players:", playerCounts.toString(), true);
            embedBuilder.setColor(Color.ORANGE);
            embedBuilder.setThumbnail("https://i.imgur.com/KeFpOkS.png");
            return embedBuilder.build();
        } else {
            return null;
        }
    }

    private Message sendMessage(JDA jda, String channelId, MessageEmbed message) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        jda.getTextChannelById(channelId).sendMessageEmbeds(message).queue(future::complete);
        return future.join();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String messageContent = event.getMessage().getContentDisplay();

        if (messageContent.equalsIgnoreCase("/fearbotstop")) {
            Member member = event.getMember();
            if (member != null && member.hasPermission(Permission.ADMINISTRATOR)) {
                event.getMessage().delete().queue();
                shutdownBot(event.getJDA());
            } else {
                event.getMessage().delete().queue();
            }
        }
    }

    private void editMessage(Message message, MessageEmbed updatedMessage) {
        message.editMessageEmbeds(updatedMessage).queue();
    }

    private void deleteMessage(Message message) {
        message.delete().queue();
    }

    private void shutdownBot(JDA jda) {
        if (sentMessage != null) {
            deleteMessage(sentMessage);
        }
        jda.shutdown();
    }
}