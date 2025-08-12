package carpet.commands;

import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("NullableProblems")
public class CommandRefresh extends CommandCarpetBase {
    @Override
    public String getName() {
        return "refresh";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/refresh chunk <current | all | inrange | at>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || !args[0].equalsIgnoreCase("chunk")) {
            throw new WrongUsageException(getUsage(sender));
        }

        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("Must be a player to use this command");
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        WorldServer world = (WorldServer) player.world;
        ChunkProviderServer provider = world.getChunkProvider();
        int refreshed = 0;

        if (args.length == 1) {
            throw new WrongUsageException(getUsage(sender));
        }

        switch (args[1].toLowerCase()) {
            case "current": {
                int cx = MathHelper.floor(player.posX) >> 4;
                int cz = MathHelper.floor(player.posZ) >> 4;
                refreshed = refreshChunk(world, player, cx, cz);
                break;
            }
            case "all": {
                for (Chunk chunk : provider.loadedChunks.values()) {
                    refreshed += refreshChunk(world, player, chunk.x, chunk.z);
                }
                break;
            }
            case "inrange": {
                int range = server.getPlayerList().getViewDistance();
                int cx = MathHelper.floor(player.posX) >> 4;
                int cz = MathHelper.floor(player.posZ) >> 4;
                for (int x = cx - range; x <= cx + range; x++) {
                    for (int z = cz - range; z <= cz + range; z++) {
                        if (provider.chunkExists(x, z)) {
                            refreshed += refreshChunk(world, player, x, z);
                        }
                    }
                }
                break;
            }
            case "at": {
                if (args.length < 4) {
                    throw new WrongUsageException("/refresh chunk at <chunkX> <chunkZ>");
                }
                int cx = parseInt(args[2], -30000000 >> 4, 30000000 >> 4);
                int cz = parseInt(args[3], -30000000 >> 4, 30000000 >> 4);
                refreshed = refreshChunk(world, player, cx, cz);
                break;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }

        Messenger.m(player, "y Refreshed " + refreshed + " chunk(s)");
    }

    private int refreshChunk(WorldServer world, EntityPlayerMP player, int cx, int cz) {
        PlayerChunkMap pcm = world.getPlayerChunkMap();
        PlayerChunkMapEntry entry = pcm.getEntry(cx, cz);
        if (entry != null && entry.sentToPlayers) {
            Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
            if (chunk != null) {
                player.connection.sendPacket(new SPacketChunkData(chunk, 65535));
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "chunk");
        }

        if ("chunk".equalsIgnoreCase(args[0])) {
            switch (args.length) {
                case 2: {
                    return getListOfStringsMatchingLastWord(args, "current", "all", "inrange", "at");
                }

                case 3: {
                    if ("inrange".equalsIgnoreCase(args[1])) {
                        return getListOfStringsMatchingLastWord(args, "2", "4", "8", "16");
                    }
                    if ("at".equalsIgnoreCase(args[1]) && sender instanceof EntityPlayerMP) {
                        int cx = MathHelper.floor(((EntityPlayerMP) sender).posX) >> 4;

                        return getListOfStringsMatchingLastWord(args, String.valueOf(cx));
                    }
                    break;
                }

                case 4: {
                    if ("at".equalsIgnoreCase(args[1]) && sender instanceof EntityPlayerMP) {
                        int cz = MathHelper.floor(((EntityPlayerMP) sender).posZ) >> 4;

                        return getListOfStringsMatchingLastWord(args, String.valueOf(cz));
                    }
                    break;
                }
            }
        }

        return Collections.emptyList();
    }
}
