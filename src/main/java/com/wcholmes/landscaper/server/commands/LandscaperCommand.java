package com.wcholmes.landscaper.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.NaturalizationMode;
import com.wcholmes.landscaper.common.strategy.*;
import com.wcholmes.landscaper.common.util.TerrainUtils;
import com.wcholmes.landscaper.server.PlayerSettings;
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
            // SMART NATURALIZE - Analyzes surrounding terrain and replicates style
            .then(Commands.literal("naturalize")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                    .executes(LandscaperCommand::smartNaturalize)
                )
            )
            // MANUAL MODE - Traditional mode-based naturalization
            .then(Commands.literal("apply")
                .executes(ctx -> naturalize(ctx, null, null))
                .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        for (NaturalizationMode mode : NaturalizationMode.values()) {
                            builder.suggest(mode.name().toLowerCase());
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> naturalize(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode"), null))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> naturalize(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode"), IntegerArgumentType.getInteger(ctx, "radius")))
                    )
                )
            )
            .then(Commands.literal("mode")
                .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        for (NaturalizationMode mode : NaturalizationMode.values()) {
                            builder.suggest(mode.name().toLowerCase());
                        }
                        return builder.buildFuture();
                    })
                    .executes(LandscaperCommand::setMode)
                )
            )
            .then(Commands.literal("radius")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                    .executes(LandscaperCommand::setRadius)
                )
            )
            .then(Commands.literal("settings")
                .executes(LandscaperCommand::showSettings)
            )
        );
    }

    private static int naturalize(CommandContext<CommandSourceStack> ctx, String modeStr, Integer radiusArg) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        PlayerSettings settings = PlayerSettings.get(player);

        // Get mode (from arg or player default)
        NaturalizationMode mode;
        if (modeStr != null) {
            try {
                mode = NaturalizationMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("Invalid mode: " + modeStr));
                return 0;
            }
        } else {
            mode = settings.getMode();
        }

        // Get radius (from arg or player default or config default)
        int radius = radiusArg != null ? radiusArg : settings.getRadius();

        // Get player's looking position
        BlockPos pos = player.blockPosition();
        BlockPos surfacePos = TerrainUtils.findSurface(level, pos);

        if (surfacePos == null) {
            source.sendFailure(Component.literal("Could not find valid surface"));
            return 0;
        }

        // Apply naturalization
        applyNaturalization(level, player, surfacePos, mode, radius);

        source.sendSuccess(() -> Component.literal("§6Naturalized §e" + radius + "§6 block radius with mode §e" + mode.getDisplayName()), false);

        return 1;
    }

    /**
     * SMART NATURALIZE - Analyzes surrounding terrain and replicates natural style
     */
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

        // ANALYZE surrounding terrain (3 chunk radius = 48 blocks)
        source.sendSuccess(() -> Component.literal("§6Analyzing 3-chunk radius (48 blocks)..."), false);
        TerrainProfile profile = TerrainAnalyzer.analyze(level, surfacePos);

        // Show analysis results
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

        // Get positions for validation
        java.util.List<BlockPos> targetPositions = NaturalizationConfig.isCircleShape() ?
            getCirclePositions(surfacePos, radius) :
            getSquarePositions(surfacePos, radius);

        // CAPTURE BEFORE snapshot
        AccuracyValidator.Snapshot before = AccuracyValidator.captureSnapshot(level, targetPositions);

        // APPLY using intelligent strategy
        int blocksChanged = IntelligentNaturalizeStrategy.apply(
            level,
            surfacePos,
            radius,
            profile,
            NaturalizationConfig.isCircleShape(),
            NaturalizationConfig.getMessyEdgeExtension()
        );

        // CAPTURE AFTER snapshot and validate
        AccuracyValidator.Snapshot after = AccuracyValidator.captureSnapshot(level, targetPositions);
        AccuracyValidator.ValidationResult validation = AccuracyValidator.validate(before, after, profile);

        // Generate detailed block palette comparison
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

        // Print detailed block comparison
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

    private static void applyNaturalization(ServerLevel level, ServerPlayer player, BlockPos center, NaturalizationMode mode, int radius) {
        NaturalizationConfig.load();

        // Create player-specific settings for this operation with the radius override
        com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings playerSettings =
            new com.wcholmes.landscaper.common.config.PlayerConfig.PlayerSettings(
                radius,
                NaturalizationConfig.getVegetationDensity(),
                NaturalizationConfig.getMessyEdgeExtension(),
                false, // No resource consumption for commands
                NaturalizationConfig.isOverworldOnly(),
                NaturalizationConfig.getMaxFlattenHeight(),
                NaturalizationConfig.getErosionStrength(),
                NaturalizationConfig.getRoughnessAmount()
            );

        // Get strategy for this mode
        TerrainModificationStrategy strategy = getStrategy(mode);

        // Apply strategy
        strategy.modify(level, center, player, mode, new java.util.HashMap<>(), playerSettings);
    }

    private static TerrainModificationStrategy getStrategy(NaturalizationMode mode) {
        return switch (mode.getStrategyType()) {
            case REPLACE -> new ReplaceStrategy();
            case FILL -> new FillStrategy();
            case FLATTEN -> new FlattenStrategy();
            case FLOOD -> new FloodStrategy();
            case NATURALIZE -> new NaturalizeStrategy();
        };
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        String modeStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode");

        try {
            NaturalizationMode mode = NaturalizationMode.valueOf(modeStr.toUpperCase());
            PlayerSettings settings = PlayerSettings.get(player);
            settings.setMode(mode);

            source.sendSuccess(() -> Component.literal("§6Mode set to: §e" + mode.getDisplayName()), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid mode: " + modeStr));
            return 0;
        }
    }

    private static int setRadius(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        PlayerSettings settings = PlayerSettings.get(player);
        settings.setRadius(radius);

        source.sendSuccess(() -> Component.literal("§6Radius set to: §e" + radius), false);
        return 1;
    }

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        PlayerSettings settings = PlayerSettings.get(player);

        source.sendSuccess(() -> Component.literal(
            "§6=== Landscaper Settings ===\n" +
            "§6Mode: §e" + settings.getMode().getDisplayName() + "\n" +
            "§6Radius: §e" + settings.getRadius() + "\n" +
            "§6Shape: §e" + (NaturalizationConfig.isCircleShape() ? "Circle" : "Square") + "\n" +
            "§6Messy Edge: §e" + NaturalizationConfig.getMessyEdgeExtension()
        ), false);

        return 1;
    }
}
