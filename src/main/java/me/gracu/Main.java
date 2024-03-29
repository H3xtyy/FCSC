package me.gracu;

import com.google.gson.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
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
import java.io.*;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends ListenerAdapter {
    private final String channelId;
    private final int checkInterval;
    private final int playerThreshold;

    public static void main(String[] args) throws IOException {
        Properties properties = loadProperties();
        String token = properties.getProperty("token");
        String channelId = properties.getProperty("channel_id");
        int checkInterval = Integer.parseInt(properties.getProperty("check_interval"));
        int playerThreshold = Integer.parseInt(properties.getProperty("player_threshold"));

        JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
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
                    if (getPlayerCount(json) >= playerThreshold) {
                        String message = generateMessage(json);
                        sendMessage(jda, channelId, message);
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

    private int getPlayerCount(JsonObject json) {
        if (!json.has("servers")) {
            System.err.println("No 'servers' key found in JSON data.");
            return 0;
        }

        JsonArray servers = json.getAsJsonArray("servers");
        int playerCount = 0;
        for (JsonElement element : servers) {
            JsonObject server = element.getAsJsonObject();
            String players = server.get("players").getAsString();
            String[] parts = players.split("/");
            if (parts.length == 2) {
                int currentPlayers = Integer.parseInt(parts[0].trim());
                playerCount += currentPlayers;
            }
        }
        return playerCount;
    }

    private String generateMessage(JsonObject json) {
        StringBuilder message = new StringBuilder();
        JsonArray servers = json.getAsJsonArray("servers");
        for (JsonElement element : servers) {
            JsonObject server = element.getAsJsonObject();
            String players = server.get("players").getAsString();
            String[] parts = players.split("/");
            if (parts.length == 2) {
                int currentPlayers = Integer.parseInt(parts[0].trim());
                if (currentPlayers >= playerThreshold) {
                    message.append("There are ").append(currentPlayers).append(" players on ").append(server.get("name").getAsString()).append(".\n");
                }
            }
        }
        return message.toString();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        System.out.println(event.getMessage().getContentDisplay());
    }

    private void sendMessage(JDA jda, String channelId, String message) {
        jda.getTextChannelById(channelId).sendMessage(message).queue();
    }
}