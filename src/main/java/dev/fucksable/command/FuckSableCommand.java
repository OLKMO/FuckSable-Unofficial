package dev.fucksable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.fucksable.FuckSable;
import dev.fucksable.debug.BlockUpdateMonitor;
import dev.fucksable.fix.FixEntry;
import dev.fucksable.fix.FixRegistry;
import dev.fucksable.i18n.LanguageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public class FuckSableCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fucksable")
            .requires(source -> source.hasPermission(2))
            .executes(FuckSableCommand::listFixes)
            .then(Commands.argument("fix", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String input = builder.getRemaining().toLowerCase();
                    for (FixEntry entry : FixRegistry.getAllFixes()) {
                        if (entry.getId().toLowerCase().startsWith(input)) {
                            builder.suggest(entry.getId());
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(FuckSableCommand::showFix)
                .then(Commands.literal("on")
                    .executes(ctx -> toggleFix(ctx, true)))
                .then(Commands.literal("off")
                    .executes(ctx -> toggleFix(ctx, false)))
            )
            .then(Commands.literal("fstemp")
                .then(Commands.argument("block", StringArgumentType.word())
                    .executes(FuckSableCommand::findBlocks)
                )
            )
            .then(Commands.literal("fs2temp")
                .then(Commands.literal("on")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(FuckSableCommand::startMonitor)
                    )
                )
                .then(Commands.literal("off")
                    .executes(FuckSableCommand::stopAllMonitors)
                )
            )
        );
    }

    private static int listFixes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(LanguageManager.get("command.list-header")), false);

        for (FixEntry entry : FixRegistry.getAllFixes()) {
            String status;
            if (!entry.isEnvironmentMet()) {
                status = LanguageManager.get("command.env-unmet");
            } else {
                String statusKey = entry.isEnabled() ? "command.enabled" : "command.disabled";
                status = LanguageManager.get(statusKey);
            }
            String line = LanguageManager.get("command.fix-status", entry.getId(), status);
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return FixRegistry.getAllFixes().size();
    }

    private static int showFix(CommandContext<CommandSourceStack> context) {
        String fixId = StringArgumentType.getString(context, "fix");
        FixEntry entry = FixRegistry.getFix(fixId);

        if (entry == null) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-unknown", fixId)));
            return 0;
        }

        String desc = entry.getDescription();
        String status;
        if (!entry.isEnvironmentMet()) {
            status = LanguageManager.get("command.env-unmet");
        } else {
            String statusKey = entry.isEnabled() ? "command.enabled" : "command.disabled";
            status = LanguageManager.get(statusKey);
        }

        StringBuilder info = new StringBuilder();
        info.append(LanguageManager.get("command.fix-status", entry.getId(), status)).append("\n");
        info.append(LanguageManager.get("command.fix-desc", desc));
        if (!entry.getRequiredMods().isEmpty()) {
            info.append("\n").append(LanguageManager.get("command.fix-requires", String.join(", ", entry.getRequiredMods())));
        }

        String finalInfo = info.toString();
        context.getSource().sendSuccess(() -> Component.literal(finalInfo), false);
        return 1;
    }

    private static int toggleFix(CommandContext<CommandSourceStack> context, boolean enabled) {
        String fixId = StringArgumentType.getString(context, "fix");
        FixEntry entry = FixRegistry.getFix(fixId);

        if (entry == null) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-unknown", fixId)));
            return 0;
        }

        if (!entry.isEnvironmentMet()) {
            context.getSource().sendFailure(Component.literal(LanguageManager.get("command.fix-env-blocked", fixId)));
            return 0;
        }

        entry.setEnabled(enabled);
        FuckSable.saveConfig();
        String key = enabled ? "command.fix-enabled" : "command.fix-disabled";
        context.getSource().sendSuccess(() -> Component.literal(LanguageManager.get(key, fixId)), true);
        return 1;
    }

    private static int findBlocks(CommandContext<CommandSourceStack> context) {
        String blockId = StringArgumentType.getString(context, "block");
        CommandSourceStack source = context.getSource();

        ResourceLocation targetId = ResourceLocation.tryParse(blockId);
        if (targetId == null) {
            source.sendFailure(Component.literal("无效的方块ID: " + blockId));
            return 0;
        }

        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, targetId));
        if (holder.isEmpty()) {
            source.sendFailure(Component.literal("未找到方块: " + targetId));
            return 0;
        }

        var block = holder.get().value();
        List<String> results = new ArrayList<>();
        int[] totalChunks = {0};

        for (ServerLevel level : source.getServer().getAllLevels()) {
            java.util.Set<Long> scannedChunks = new java.util.HashSet<>();

            // 扫描玩家视距内的区块
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player.serverLevel() != level) continue;
                int chunkRadius = level.getServer().getPlayerList().getViewDistance();
                int playerChunkX = player.blockPosition().getX() >> 4;
                int playerChunkZ = player.blockPosition().getZ() >> 4;

                for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
                    for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                        long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                        if (!scannedChunks.add(chunkKey)) continue;

                        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                        if (chunk == null) continue;
                        totalChunks[0]++;
                        scanChunkForBlock(chunk, block, blockId, level, results);
                    }
                }
            }

            // 扫描 sub-level 的 plot 区块
            if (level instanceof dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder holder2) {
                var container = holder2.sable$getPlotContainer();
                if (container != null) {
                    for (var subLevel : container.getAllSubLevels()) {
                        var plot = subLevel.getPlot();
                        var plotBounds = plot.getBoundingBox();
                        int minCX = plotBounds.minX() >> 4;
                        int minCZ = plotBounds.minZ() >> 4;
                        int maxCX = plotBounds.maxX() >> 4;
                        int maxCZ = plotBounds.maxZ() >> 4;

                        for (int cx = minCX; cx <= maxCX; cx++) {
                            for (int cz = minCZ; cz <= maxCZ; cz++) {
                                long chunkKey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                                if (!scannedChunks.add(chunkKey)) continue;

                                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                                if (chunk == null) continue;
                                totalChunks[0]++;
                                scanChunkForBlockInSubLevel(chunk, block, blockId, level, results, subLevel, plotBounds);
                            }
                        }
                    }
                }
            }
        }

        int chunks = totalChunks[0];
        source.sendSuccess(() -> Component.literal("扫描完成，扫描了 " + chunks + " 个区块"), false);
        if (results.isEmpty()) {
            source.sendSuccess(() -> Component.literal("未找到方块: " + blockId), false);
        } else {
            int count = results.size();
            source.sendSuccess(() -> Component.literal("找到 " + count + " 个 " + blockId + ":"), false);
            for (String line : results) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return results.size();
    }

    private static void scanChunkForBlock(LevelChunk chunk, net.minecraft.world.level.block.Block block, String blockId, ServerLevel level, List<String> results) {
        int minSection = chunk.getMinSection();
        int maxSection = chunk.getMaxSection();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            var sectionData = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (sectionData.hasOnlyAir()) continue;

            int baseY = sectionY << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        var state = sectionData.getBlockState(x, y, z);
                        if (state.is(block)) {
                            BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                            results.add(String.format("  [%s] %s 维度=%s",
                                blockId, pos, level.dimension().location()));
                        }
                    }
                }
            }
        }
    }

    private static void scanChunkForBlockInSubLevel(LevelChunk chunk, net.minecraft.world.level.block.Block block, String blockId, ServerLevel level, List<String> results, dev.ryanhcode.sable.sublevel.SubLevel subLevel, dev.ryanhcode.sable.companion.math.BoundingBox3ic plotBounds) {
        int minSection = chunk.getMinSection();
        int maxSection = chunk.getMaxSection();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            var sectionData = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (sectionData.hasOnlyAir()) continue;

            int baseY = sectionY << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        var state = sectionData.getBlockState(x, y, z);
                        if (state.is(block)) {
                            int worldX = (cx << 4) + x;
                            int worldY = baseY + y;
                            int worldZ = (cz << 4) + z;

                            // 仅处理 plot 范围内的方块
                            if (worldX < plotBounds.minX() || worldX > plotBounds.maxX() ||
                                worldY < plotBounds.minY() || worldY > plotBounds.maxY() ||
                                worldZ < plotBounds.minZ() || worldZ > plotBounds.maxZ()) continue;

                            BlockPos localPos = new BlockPos(worldX, worldY, worldZ);

                            // 将局部坐标转换为全局坐标
                            net.minecraft.world.phys.Vec3 globalPos = dev.ryanhcode.sable.companion.math.JOMLConversion.toMojang(
                                subLevel.logicalPose().transformPosition(
                                    dev.ryanhcode.sable.companion.math.JOMLConversion.toJOML(
                                        new net.minecraft.world.phys.Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5)
                                    )
                                )
                            );

                            results.add(String.format("  [%s] 局部=%s 全局=(%d, %d, %d) 维度=%s",
                                blockId, localPos,
                                (int) globalPos.x, (int) globalPos.y, (int) globalPos.z,
                                level.dimension().location()));
                        }
                    }
                }
            }
        }
    }

    private static int startMonitor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }

        Coordinates coords = Vec3Argument.getCoordinates(context, "pos");
        BlockPos pos = coords.getBlockPos(source);
        BlockUpdateMonitor.startMonitoring(pos, player);

        source.sendSuccess(() -> Component.literal("已开始监控位置 " + pos.toShortString() + " 的方块更新（点击消息可复制调用链）"), false);
        return 1;
    }

    private static int stopAllMonitors(CommandContext<CommandSourceStack> context) {
        int count = BlockUpdateMonitor.getAllMonitors().size();
        BlockUpdateMonitor.stopAll();
        context.getSource().sendSuccess(() -> Component.literal("已停止所有监控（共 " + count + " 个）"), false);
        return count;
    }
}
