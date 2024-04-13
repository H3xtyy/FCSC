package me.gracu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.io.IOException;

public class ServerDataFetcher {
    public JsonObject getServerData() throws IOException {
        acceptSelfSignedCertificates(); //UNCOMMENT THIS IF FEAR COMMUNITY SITE CERTIFICATE IS EXPIRED
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

    public int getPlayerCount(JsonObject json) {
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

    public MessageEmbed generateMessage(JsonObject json, int playerThreshold) {
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
}
