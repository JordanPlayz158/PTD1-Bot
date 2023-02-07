package xyz.jordanplayz158.ptdbot.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import xyz.jordanplayz158.ptdbot.PTDBot;
import xyz.jordanplayz158.ptdbot.slash.DexVerifyCommand;
import xyz.jordanplayz158.ptdbot.slash.SlashCommand;
import xyz.jordanplayz158.ptdbot.slash.SlashData;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class SlashListener extends ListenerAdapter {
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Guild guild = event.getGuild();

        if(guild == null || !guild.getId().equals(PTDBot.dotenv.get("DISCORD_GUILD_ID"))) {
            event.reply("You must run this command in the allowed guild.").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "dex-verify":
                new DexVerifyCommand().onCall(event);
                break;
            default:
                event.reply("This command does not exist. How did you even do that?").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        UUID interactionUuid = UUID.fromString(event.getComponentId());

        SlashData data = PTDBot.slashData.get(interactionUuid);

        if(data == null) {
            event.reply("This interaction is no longer valid. Did you restart the bot in between interactions?").setEphemeral(true).queue();
            return;
        }

        if(!event.getUser().getId().equals(data.memberId())) {
            event.reply("You are not the user who executed the slash command so you do not have permission to interact to the slash command's prompts").setEphemeral(true).queue();
            return;
        }

        try {
            SlashCommand commandInstance = data.commandClass().getConstructor().newInstance();
            commandInstance.onButtonClick(event, data);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        PTDBot.slashData.remove(interactionUuid);
    }
}
