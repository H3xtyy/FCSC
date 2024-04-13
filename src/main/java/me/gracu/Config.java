package me.gracu;

import java.io.*;
import java.util.Properties;

public class Config {
    private final Properties properties;

    public Config() throws IOException {
        createConfigFileIfNotExists();
        properties = loadProperties();
    }

    public String getToken() {
        return properties.getProperty("token");
    }

    public String getChannelId() {
        return properties.getProperty("channel_id");
    }

    public int getCheckInterval() {
        return Integer.parseInt(properties.getProperty("check_interval"));
    }

    public int getPlayerThreshold() {
        return Integer.parseInt(properties.getProperty("player_threshold"));
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
}
