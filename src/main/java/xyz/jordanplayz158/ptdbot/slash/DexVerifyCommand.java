package xyz.jordanplayz158.ptdbot.slash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import xyz.jordanplayz158.ptdbot.PTDBot;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DexVerifyCommand extends SlashCommand {
    private final HttpClient client = HttpClient.newBuilder().build();
    private final HttpRequest.Builder initialRequest = HttpRequest.newBuilder()
            .header("Authorization", "Bearer " + PTDBot.dotenv.get("PTD_ADMIN_API_KEY"));

    private final Gson gson = new GsonBuilder().create();

    private UUID uuid = UUID.randomUUID();


    /**
     * When the slash command is first called (via /command)
     */
    public void onCall(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        List<Role> roles = member.getRoles();

        if(!roles.contains(guild.getRoleById(PTDBot.dotenv.get("DISCORD_DEX_VERIFY_APPROVED_ROLE_ID")))) {
            event.reply("You must have the approved role to execute this command.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue(); // Let the user know we received the command before doing anything else
        InteractionHook hook = event.getHook(); // This is a special webhook that allows you to send messages without having permissions in the channel and also allows ephemeral messages
        hook.setEphemeral(false); // All messages here will now be ephemeral implicitly (Which means only the user can see it)

        try {
            long userId = event.getOption("user-id").getAsLong();

            HttpRequest request = initialRequest
                    .uri(new URI(PTDBot.dotenv.get("PTD_INSTANCE") + "/api/admin/user/" + userId + "?route=/saves?exclude=id,user_id,advanced,advanced_a,classic,classic_a,challenge,avatar,npcTrade,shinyHunt,version,created_at,updated_at,pokemon"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, List<String>> headers = response.headers().map();

            if(response.statusCode() != 200 || (!headers.containsKey("Content-Type") || !headers.get("Content-Type").contains("application/json"))) {
                hook.editOriginal("User does not exist or the api key does not exist or does not have admin permissions.").queue();
                return;
            }

            JsonArray saveResponse = gson.fromJson(response.body(), JsonArray.class);
            MessageBuilder message = new MessageBuilder();

            List<Component> actionRowComponents = new ArrayList<>();

            for (JsonElement saveElement : saveResponse) {
                JsonObject save = saveElement.getAsJsonObject();

                try {
                    int saveNum = save.get("num").getAsInt() + 1;

                    String saveNumEmoji = numToDiscordEmoji(saveNum);

                    String nickname = save.get("nickname").getAsString();
                    int badges = save.get("badges").getAsInt();
                    long money = save.get("money").getAsLong();

                    message.appendFormat("%s | Nickname: %s, Badges: %d, Money: %d%n", saveNumEmoji, nickname, badges, money);

                    HashMap<String, String> data = new HashMap<>();
                    data.put("interaction", "profile");
                    data.put("num", "" + (saveNum - 1));
                    data.put("id", "" + userId);

                    uuid = PTDBot.addSlashData(uuid, new SlashData(member.getId(), getClass(), data));

                    actionRowComponents.add(Button.primary(uuid.toString(), "" + saveNum));
                } catch (UnsupportedOperationException ignored) {}
            }

            hook.editOriginal(message.build()).setActionRow(actionRowComponents).queue();
        } catch (URISyntaxException | IOException | InterruptedException | JsonSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void onButtonClick(ButtonClickEvent event, SlashData data) {
        event.deferEdit().queue(); // Let the user know we received the command before doing anything else
        InteractionHook hook = event.getHook(); // This is a special webhook that allows you to send messages without having permissions in the channel and also allows ephemeral messages
        hook.setEphemeral(false); // All messages here will now be ephemeral implicitly (Which means only the user can see it)

        HashMap<String, String> map = data.map();

        String interactionKey = map.get("interaction");
        switch (interactionKey) {
            case "profile" -> {
                List<Component> components = new ArrayList<>();
                for (String dex : new String[]{"Normal", "Shiny", "Shadow"}) {
                    HashMap<String, String> dataMap = new HashMap<>();
                    dataMap.put("interaction", "dex");
                    dataMap.put("num", map.get("num"));
                    dataMap.put("id", map.get("id"));
                    dataMap.put("dex", dex.toLowerCase(Locale.ENGLISH));

                    uuid = PTDBot.addSlashData(uuid, new SlashData(data.memberId(), data.commandClass(), dataMap));
                    components.add(Button.primary(uuid.toString(), dex));
                }
                hook.editOriginal("Which dex do you wish to check for your profile?").setActionRow(components).queue();
            }
            case "dex" -> {
                String userId = map.get("id");
                String saveNum = map.get("num");
                String checkingDexType = map.get("dex");
                byte rarity = rarityToNumeric(checkingDexType);

                String url = PTDBot.dotenv.get("PTD_INSTANCE") + "/api/admin/user/" + userId + "?route=/saves/" + saveNum + "/pokemon?exclude=id,save_id,pId,nickname,exp,lvl,m1,m2,m3,m4,ability,mSel,targetType,item,pos,created_at,updated_at,offers,requests";
                try {
                    LinkedHashMap<String, List<Integer>> dex = new LinkedHashMap<>();
                    dex.put("n", new ArrayList<>());
                    dex.put("h", new ArrayList<>());
                    dex.put("rf", new ArrayList<>());

                    int lastPage = 1;
                    for (int i = 1; i <= lastPage; i++) {
                        HttpRequest request = initialRequest
                                .uri(new URI(url + "%26page=" + i))
                                .GET()
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);

                        for (JsonElement pokemonElement : responseJson.getAsJsonArray("data")) {
                            JsonObject pokemon = pokemonElement.getAsJsonObject();

                            try {
                                int pokemonNumber = pokemon.get("pNum").getAsInt();
                                String tag = pokemon.get("tag").getAsString();
                                byte shiny = pokemon.get("shiny").getAsByte();

                                if (shiny == rarity) {
                                    dex.putIfAbsent(tag, new ArrayList<>());
                                    dex.get(tag).add(pokemonNumber);
                                }
                            } catch (UnsupportedOperationException ignored) {
                            }
                        }

                        if (lastPage == 1) {
                            lastPage = responseJson.get("last_page").getAsInt();
                        }
                    }

                    MessageBuilder message = new MessageBuilder();
                    MessageBuilder privateMessage = new MessageBuilder();

                    int[] mixedDexes = new int[151];

                    TextChannel channel = event.getGuild().getTextChannelById(PTDBot.dotenv.get("DISCORD_DEX_VERIFY_SLASH_COMMAND_RESULT_MESSAGE_CHANNEL_ID"));

                    final String id = "ID: " + uuid.toString();

                    LinkedHashMap<String, Integer> pokemonUsed = new LinkedHashMap<>();

                    for (Map.Entry<String, List<Integer>> tagEntry : dex.entrySet()) {
                        String tag = tagEntry.getKey();

                        for (Integer integer : tagEntry.getValue()) {
                            if (integer > mixedDexes.length) {
                                continue;
                            }

                            if (mixedDexes[integer - 1] == 0) {
                                mixedDexes[integer - 1] = 1;

                                Integer previousValue = pokemonUsed.get(tag);
                                pokemonUsed.put(tag, previousValue != null ? previousValue + 1 : 1);
                            }
                        }
                    }


                    privateMessage.appendFormat("%s | User ID: %s | Profile: %s | has a %s dex consisting of%n", id, userId, saveNum, checkingDexType);
                    pokemonUsed.forEach((k, v) -> privateMessage.appendFormat("%d %s tag pokemon%n", v, tagToName(k)));

                    if (Arrays.stream(mixedDexes).allMatch(num -> num == 1)) {
                        message.appendFormat("%s | You passed the %s dex verification check!", id, checkingDexType);
                        channel.sendMessage(privateMessage.build()).queue();
                    } else {
                        message.appendFormat("%s | You failed the %s dex verification check.", id, checkingDexType);
                        privateMessage.appendFormat("%nThe user was missing pokemon numbers: ");

                        for (int i = 1; i < mixedDexes.length; i++) {
                            if (mixedDexes[i] == 0) {
                                privateMessage.append(String.valueOf(i + 1)).append(" ");
                            }
                        }

                        channel.sendMessage(privateMessage.build()).queue();
                    }

                    hook.editOriginal(message.build()).queue();
                } catch (URISyntaxException | IOException | InterruptedException | JsonSyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            default -> event.editMessage("Interaction '" + interactionKey + "' is not defined.").queue();
        }
    }

    public String numToDiscordEmoji(int num) {
        return switch (num) {
            case 1 -> ":one:";
            case 2 -> ":two:";
            case 3 -> ":three:";
            default -> ":question:";
        };
    }

    private byte rarityToNumeric(String rarity) {
        return switch (rarity) {
            case "normal" -> 0;
            case "shiny" -> 1;
            case "shadow" -> 2;
            default -> -1;
        };
    }

    private String tagToName(String tag) {
        return switch (tag) {
            case "n" -> "Normal";
            case "h" -> "Hacked";
            case "rf" -> "Regional Forms";
            default -> "Unknown";
        };
    }
}
