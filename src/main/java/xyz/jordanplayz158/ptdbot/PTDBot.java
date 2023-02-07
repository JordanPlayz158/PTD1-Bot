package xyz.jordanplayz158.ptdbot;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import xyz.jordanplayz158.ptdbot.listeners.SlashListener;
import xyz.jordanplayz158.ptdbot.slash.SlashData;

import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.UUID;

public class PTDBot {
    public static final Dotenv dotenv = Dotenv.load();
    public static final HashMap<UUID, SlashData> slashData = new HashMap<>();

    public static void main(String[] args) throws LoginException {
        JDA jda = JDABuilder.createLight(dotenv.get("DISCORD_TOKEN"))
                .addEventListeners(new SlashListener())
                .build();

        CommandListUpdateAction commands = jda.updateCommands();

        commands.addCommands(
                new CommandData("dex-verify", "This command will very a dex for a certain profile on the requested account id.")
                        .addOption(OptionType.INTEGER,
                                "user-id",
                                String.format("Can be obtained by going to %s/games/ptd/debug.php", dotenv.get("PTD_INSTANCE")),
                                true)
        );

        commands.queue();
    }

    public static UUID addSlashData(UUID uuid, SlashData data) {
        while(slashData.containsKey(uuid)) {
            uuid = UUID.randomUUID();
        }

        slashData.put(uuid, data);

        return uuid;
    }
}
