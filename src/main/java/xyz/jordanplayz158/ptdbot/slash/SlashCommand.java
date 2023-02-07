package xyz.jordanplayz158.ptdbot.slash;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

public abstract class SlashCommand {
    public void onCall(SlashCommandEvent event) {}
    public void onButtonClick(ButtonClickEvent event, SlashData data) {}
}
