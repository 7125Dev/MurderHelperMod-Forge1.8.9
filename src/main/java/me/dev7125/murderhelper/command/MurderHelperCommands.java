package me.dev7125.murderhelper.command;

import me.dev7125.murderhelper.MurderHelperMod;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

public class MurderHelperCommands extends CommandBase {

    @Override
    public String getCommandName() {
        return "mh";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mh <startmurder|calloutmurder>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Any player can use this command
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sendMessage(sender, EnumChatFormatting.YELLOW + "MurderHelper Commands:");
            sendMessage(sender, EnumChatFormatting.GRAY + "/mh startmurder - Manually start game detection");
            sendMessage(sender, EnumChatFormatting.GRAY + "/mh calloutmurder - Toggle shout feature");
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "startmurder":
                handleStartMurder(sender);
                break;

            case "calloutmurder":
                handleCalloutMurder(sender);
                break;

            default:
                sendMessage(sender, EnumChatFormatting.RED + "Unknown command. Use /mh for help.");
                break;
        }
    }

    private void handleStartMurder(ICommandSender sender) {
        // Manually set inGame to true for servers without maps
        if (!MurderHelperMod.gameState.isInGame()) {
            MurderHelperMod.manuallyStartGame();
            sendMessage(sender, EnumChatFormatting.GREEN + "Murder Mystery game manually started!");
            sendMessage(sender, EnumChatFormatting.GRAY + "Role detection is now active.");
        } else {
            sendMessage(sender, EnumChatFormatting.YELLOW + "Game is already active!");
        }
    }

    private void handleCalloutMurder(ICommandSender sender) {
        // Temporarily toggle shout feature (not saved to config)
        MurderHelperMod.config.shoutEnabled = !MurderHelperMod.config.shoutEnabled;

        String status = MurderHelperMod.config.shoutEnabled ?
            EnumChatFormatting.GREEN + "enabled" :
            EnumChatFormatting.RED + "disabled";

        sendMessage(sender, EnumChatFormatting.YELLOW + "Shout feature temporarily " + status);
        sendMessage(sender, EnumChatFormatting.GRAY + "(Will reset when game ends)");
    }

    private void sendMessage(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "startmurder", "calloutmurder");
        }
        return null;
    }
}