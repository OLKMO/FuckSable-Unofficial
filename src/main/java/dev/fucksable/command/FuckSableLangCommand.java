package dev.fucksable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.fucksable.FuckSable;
import dev.fucksable.i18n.LanguageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class FuckSableLangCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fucksablelang")
            .requires(source -> source.hasPermission(2))
            .executes(FuckSableLangCommand::listLanguages)
            .then(Commands.argument("lang", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String input = builder.getRemaining().toLowerCase();
                    for (String lang : LanguageManager.getAvailableLanguages()) {
                        if (lang.toLowerCase().startsWith(input)) {
                            builder.suggest(lang);
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(FuckSableLangCommand::switchLanguage)
            )
        );
    }

    private static int listLanguages(CommandContext<CommandSourceStack> context) {
        String current = LanguageManager.getCurrentLanguage();
        Set<String> available = LanguageManager.getAvailableLanguages();
        String msg = LanguageManager.get("command.lang-current", current, String.join(", ", available));
        context.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int switchLanguage(CommandContext<CommandSourceStack> context) {
        String lang = StringArgumentType.getString(context, "lang");

        if (!LanguageManager.getAvailableLanguages().contains(lang)) {
            context.getSource().sendFailure(Component.literal(
                LanguageManager.get("command.lang-not-found", lang)
            ));
            return 0;
        }

        LanguageManager.setLanguage(lang);
        FuckSable.saveConfig();
        context.getSource().sendSuccess(() -> Component.literal(
            LanguageManager.get("command.lang-switched", lang)
        ), true);
        return 1;
    }
}
