package com.wcholmes.landscaper.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.util.TerrainUtils;
import com.wcholmes.landscaper.server.analysis.TerrainAnalyzer;
import com.wcholmes.landscaper.server.analysis.TerrainProfile;
import com.wcholmes.landscaper.server.analysis.IntelligentNaturalizeStrategy;
import com.wcholmes.landscaper.server.analysis.AccuracyValidator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class LandscaperCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("landscaper")
            .then(Commands.literal("naturalize")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                    .executes(LandscaperCommand::smartNaturalize)
                )
            )
        );
    }

    private static int smartNaturalize(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        int radius = IntegerArgumentType.getInteger(ctx, "radius");

        BlockPos pos = player.blockPosition();
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);

        if (surfacePos == null) {
            source.sendFailure(Component.literal("Could not find valid surface"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6Analyzing 3-chunk radius (48 blocks)..."), false);
        TerrainProfile profile = TerrainAnalyzer.analyze(level, surfacePos);

        String consistency = profile.isHomogeneous() ? "§aHOMOGENEOUS" : "§eDIVERSE";
        source.sendSuccess(() -> Component.literal(
            "§6Analysis complete!\n" +
            "§7Surface: §e" + profile.getBlockPalette().size() + " types §7(" + consistency + " §e" + String.format("%.0f%%", profile.getSurfaceConsistency() * 100) + "§7)\n" +
            "§7Dominant: §e" + profile.getDominantSurfaceBlock().getName().getString() + "\n" +
            "§7Vegetation: §e" + profile.getVegetationPalette().size() + " types (§e" + String.format("%.1f%%", profile.getVegetationDensity() * 100) + ")\n" +
            "§7Height: §e" + profile.getMinY() + "-" + profile.getMaxY() + " §7(avg: §e" + profile.getAverageY() + "§7)\n" +
            "§7Smoothness: §e" + String.format("%.1f%%", profile.getSmoothness() * 100) + "\n" +
            "§7Water: §e" + profile.getWaterType() + " §7(§e" + String.format("%.1f%%", profile.getWaterDensity() * 100) + "§7)\n" +
            "§6Applying natural style to §e" + radius + "§6 block radius..."
        ), false);

        java.util.List<BlockPos> targetPositions = NaturalizationConfig.isCircleShape() ?
            getCirclePositions(surfacePos, radius) :
            getSquarePositions(surfacePos, radius);

        AccuracyValidator.Snapshot before = AccuracyValidator.captureSnapshot(level, targetPositions);

        int blocksChanged = IntelligentNaturalizeStrategy.apply(
            level,
            surfacePos,
            radius,
            profile,
            NaturalizationConfig.isCircleShape(),
            NaturalizationConfig.getMessyEdgeExtension()
        );

        AccuracyValidator.Snapshot after = AccuracyValidator.captureSnapshot(level, targetPositions);
        AccuracyValidator.ValidationResult validation = AccuracyValidator.validate(before, after, profile);

        String paletteComparison = AccuracyValidator.compareBlockPalettes(profile, after);

        source.sendSuccess(() -> Component.literal(
            "§a✓ Complete! §6Modified §e" + blocksChanged + " §6blocks\n" +
            "§7Accuracy: " + validation.getGrade() + " §e" + String.format("%.0f%%", validation.overallScore * 100) + "\n" +
            "§7  Consistency: §e" + String.format("%.0f%%", validation.consistencyMatch * 100) + " §7(" +
                String.format("%.0f%%", validation.beforeConsistency * 100) + " → " +
                String.format("%.0f%%", validation.afterConsistency * 100) + ")\n" +
            "§7  Elevation: §e" + String.format("%.0f%%", validation.elevationPreservation * 100) + " §7(" +
                validation.beforeRange + " → " + validation.afterRange + " range)\n" +
            "§7  Block Match: " + (validation.blockMatch ? "§a✓" : "§c✗")
        ), false);

        source.sendSuccess(() -> Component.literal(paletteComparison), false);

        return 1;
    }

    private static java.util.List<BlockPos> getCirclePositions(BlockPos center, int radius) {
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.sqrt(x * x + z * z) <= radius) {
                    positions.add(center.offset(x, 0, z));
                }
            }
        }
        return positions;
    }

    private static java.util.List<BlockPos> getSquarePositions(BlockPos center, int radius) {
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                positions.add(center.offset(x, 0, z));
            }
        }
        return positions;
    }
}
