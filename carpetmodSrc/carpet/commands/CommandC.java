package carpet.commands;

import carpet.utils.CameraData;
import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketRespawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("NullableProblems")
public class CommandC extends CommandCarpetBase {
    private static final int SAFE_RADIUS = 32;

    @Override
    public String getName() {
        return "c";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/c [player|x y z [in <dimension>]]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!command_enabled("commandCameramode", sender)) return;
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (args.length == 0) {
            toggleCameraMode(server, player);
            return;
        }

        if (args.length == 1) {
            EntityPlayerMP target;
            try {
                target = getPlayer(server, sender, args[0]);
            } catch (PlayerNotFoundException e) {
                Messenger.m(player, "r Player not found");
                return;
            }
            if (!player.isSpectator()) {
                toSpectator(player);
            }
            teleportPlayerTo(player, new Vec3d(target.posX, target.posY, target.posZ), target.dimension);
            return;
        }

        if (args.length != 3 && args.length != 5) {
            throw new WrongUsageException(getUsage(sender));
        }

        BlockPos pos = parseBlockPos(sender, args, 0, false);
        int targetDim;
        if (args.length == 5) {
            if (!"in".equalsIgnoreCase(args[3])) throw new WrongUsageException(getUsage(sender));
            WorldServer targetWorld = getDimension(server, args[4]);
            targetDim = targetWorld.provider.getDimensionType().getId();
        } else {
            targetDim = player.dimension;
        }

        if (!player.isSpectator()) {
            toSpectator(player);
        }

        boolean _unused = teleportPlayerTo(player, new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), targetDim);
    }

    public static void restoreCamera(EntityPlayerMP player) {
        CameraData cameraData = CameraData.getForPlayer(player);
        if (cameraData == null) {
            if (safeTeleportNearby(player, player.getPosition(), SAFE_RADIUS)) {
                Messenger.m(player, "g Camera data missing, teleported to safe nearby location");

                // fix fall
                player.fallDistance = 0.0f;
                player.setGameType(GameType.SURVIVAL);
                // player.removePotionEffect(MobEffects.INVISIBILITY);
            } else {
                Messenger.m(player, "r Failed to restore camera mode; check data or position safety");
            }
            return;
        }

        Vec3d storedPos = cameraData.getStoredPos();
        teleportPlayerTo(player, storedPos, cameraData.getStoredDim(), cameraData.getStoreYaw(), cameraData.getStorePitch());

        player.fallDistance = cameraData.getStoreFallDistance();
        player.setGameType(GameType.SURVIVAL);
        // player.removePotionEffect(MobEffects.INVISIBILITY);

        cameraData.applyEffectsToPlayer(player);
        CameraData.removeForPlayer(player);
    }

    public static void toggleCameraMode(MinecraftServer server, EntityPlayerMP player) {
        if (player.isSpectator()) {
            restoreCamera(player);
            Messenger.m(player, "g Exited camera mode");
        } else {
            toSpectator(player);
        }
    }

    private static void toSpectator(EntityPlayerMP player) {
        BlockPos pos = player.getPosition();

        CameraData.storeForPlayer(player);
        player.setGameType(GameType.SPECTATOR);
        // player.removePotionEffect(MobEffects.INVISIBILITY);
        player.connection.setPlayerLocation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.rotationYaw, player.rotationPitch);

        Messenger.m(player, "y Entered camera mode");
    }

    public static boolean teleportPlayerTo(EntityPlayerMP player, Vec3d targetPos, int targetDim) {
        return teleportPlayerTo(player, targetPos, targetDim, player.rotationYaw, player.rotationPitch);
    }

    public static boolean teleportPlayerTo(EntityPlayerMP player, Vec3d targetPos, int targetDim, float targetYaw, float targetPitch) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        int currentDim = player.dimension;
        if (currentDim != targetDim) {
            WorldServer currentWorld = player.getServerWorld();
            WorldServer targetWorld = server.getWorld(targetDim);

            player.dimension = targetDim;
            player.connection.sendPacket(new SPacketRespawn(targetDim, targetWorld.getDifficulty(), targetWorld.getWorldInfo().getTerrainType(), player.interactionManager.getGameType()));
            server.getPlayerList().updatePermissionLevel(player);

            currentWorld.removeEntity(player);
            player.isDead = false;
            currentWorld.getChunk(player.chunkCoordX, player.chunkCoordZ).removeEntityAtIndex(player, player.chunkCoordY);

            if (player.isEntityAlive()) {
                player.setLocationAndAngles(targetPos.x, targetPos.y, targetPos.z, targetYaw, targetPitch);
                targetWorld.spawnEntity(player);
                targetWorld.updateEntityWithOptionalForce(player, false);
            }

            player.setWorld(targetWorld);
            server.getPlayerList().preparePlayer(player, currentWorld);
            player.setPositionAndUpdate(targetPos.x, targetPos.y, targetPos.z);
            player.interactionManager.setWorld(targetWorld);
            server.getPlayerList().updateTimeAndWeatherForPlayer(player, targetWorld);
            server.getPlayerList().syncPlayerInventory(player);

            return true;
        } else {
            double dist = Math.sqrt(new BlockPos(targetPos.x, targetPos.y, targetPos.z).distanceSq(player.posX, player.posY, player.posZ));
            player.connection.setPlayerLocation(targetPos.x, targetPos.y, targetPos.z, targetYaw, targetPitch);

            int viewDist = server.getPlayerList().getViewDistance();
            return dist > (viewDist - 2) * 16;
        }
    }

    private static boolean isSafeLocation(WorldServer world, BlockPos pos) {
        return world.isAirBlock(pos) && world.isAirBlock(pos.up()) && world.getBlockState(pos.down()).isFullCube();
    }

    public static boolean safeTeleportNearby(EntityPlayerMP player, BlockPos targetPos, int maxRadius) {
        WorldServer world = player.getServerWorld();

        // Check the target position first
        if (isSafeLocation(world, targetPos)) {
            return teleport(player, targetPos);
        }

        // Spiral search for a safe spot
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    BlockPos check = findSafeY(world, targetPos.add(dx, 0, dz), 2);
                    if (check != null) return teleport(player, check);
                }
            }
        }
        return false; // No safe location found
    }

    private static BlockPos findSafeY(WorldServer world, BlockPos pos, int range) {
        for (int dy = -range; dy <= range; dy++) {
            BlockPos newPos = pos.add(0, dy, 0);
            if (isSafeLocation(world, newPos)) return newPos;
        }
        return null;
    }

    private static boolean teleport(EntityPlayerMP player, BlockPos pos) {
        player.connection.setPlayerLocation(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
        return true;
    }

    private WorldServer getDimension(MinecraftServer server, String dimensionArg) throws CommandException {
        try {
            return server.getWorld(Integer.parseInt(dimensionArg));
        } catch (NumberFormatException e) {
            for (WorldServer ws : server.worlds) {
                String dimName = ws.provider.getDimensionType().getName();
                if (dimName.equalsIgnoreCase(dimensionArg)) {
                    return ws;
                }
            }

            throw new CommandException("Dimension name '" + dimensionArg + "' does not exist");
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length >= 1 && args.length <= 3) {
            List<String> options = new ArrayList<>();
            if (args.length == 1) {
                options.addAll(Arrays.asList(server.getOnlinePlayerNames()));
            }

            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;

                if (args.length == 1) {
                    options.add("~ ~ ~");
                    options.add(String.valueOf((int) player.posX));
                } else if (args.length == 2) {
                    options.add("~ ~");
                    options.add(String.valueOf((int) player.posY));
                } else {
                    options.add(String.valueOf((int) player.posZ));
                }
            }

            options.add("~");
            return getListOfStringsMatchingLastWord(args, options);
        } else if (args.length == 4) {
            return getListOfStringsMatchingLastWord(args, Collections.singletonList("in"));
        } else if (args.length == 5) {
            List<String> dimensionNames = new ArrayList<>();
            for (WorldServer ws : server.worlds) {
                dimensionNames.add(ws.provider.getDimensionType().getName());
            }
            return getListOfStringsMatchingLastWord(args, dimensionNames);
        }
        return Collections.emptyList();
    }
}
