package carpet.commands;

import carpet.gui.CTECInventoryPlayer;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("NullableProblems")
public class CommandView extends CommandCarpetBase {
    @Override
    public String getName() {
        return "view";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/view <player>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }

        EntityPlayerMP viewer = getCommandSenderAsPlayer(sender);
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(args[0]);
        if (target == null) {
            throw new PlayerNotFoundException("Player " + args[0] + " not found");
        }

        viewer.displayGUIChest(target.getCTECInventoryPlayer());
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        return Arrays.asList(server.getOnlinePlayerNames());
    }
}
