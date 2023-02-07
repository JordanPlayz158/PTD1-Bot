package xyz.jordanplayz158.ptdbot.slash;


import java.util.HashMap;

public record SlashData(String memberId, Class<? extends SlashCommand> commandClass, HashMap<String, String> map) {}