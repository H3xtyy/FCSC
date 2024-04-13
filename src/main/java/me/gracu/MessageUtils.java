package me.gracu;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.concurrent.CompletableFuture;

public class MessageUtils {
    public static Message sendMessage(JDA jda, String channelId, MessageEmbed message) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        jda.getTextChannelById(channelId).sendMessageEmbeds(message).queue(future::complete);
        return future.join();
    }

    public static void editMessage(Message message, MessageEmbed updatedMessage) {
        message.editMessageEmbeds(updatedMessage).queue();
    }

    public static void deleteMessage(Message message) {
        message.delete().queue();
    }

    public static void shutdownBot(JDA jda, Message sentMessage) {
        if (sentMessage != null) {
            deleteMessage(sentMessage);
        }
        jda.shutdown();
    }
}
