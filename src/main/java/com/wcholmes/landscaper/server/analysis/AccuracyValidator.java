package com.wcholmes.landscaper.server.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Validates terrain modification accuracy by comparing before/after snapshots.
 * Ensures modifications match sampled terrain characteristics.
 */
public class AccuracyValidator {

    public static class Snapshot {
        private final Map<Block, Integer> surfaceBlocks;
        private final List<Integer> elevations;
        private final int totalPositions;

        public Snapshot(Map<Block, Integer> surfaceBlocks, List<Integer> elevations, int totalPositions) {
            this.surfaceBlocks = surfaceBlocks;
            this.elevations = elevations;
            this.totalPositions = totalPositions;
        }

        public double getConsistency() {
            if (surfaceBlocks.isEmpty()) return 1.0;
            int maxCount = surfaceBlocks.values().stream().max(Integer::compare).orElse(0);
            return (double) maxCount / Math.max(1, totalPositions);
        }

        public int getElevationRange() {
            if (elevations.isEmpty()) return 0;
            return elevations.stream().max(Integer::compare).orElse(0) -
                   elevations.stream().min(Integer::compare).orElse(0);
        }

        public Block getDominantBlock() {
            return surfaceBlocks.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }
    }

    /**
     * Capture terrain snapshot for validation
     */
    public static Snapshot captureSnapshot(Level level, List<BlockPos> positions) {
        Map<Block, Integer> surfaceBlocks = new HashMap<>();
        List<Integer> elevations = new ArrayList<>();

        for (BlockPos pos : positions) {
            // Find surface
            BlockPos surface = com.wcholmes.landscaper.common.util.TerrainUtils.findSurface(level, pos);
            if (surface == null) continue;

            elevations.add(surface.getY());

            BlockState state = level.getBlockState(surface);
            if (!state.isAir()) {
                surfaceBlocks.merge(state.getBlock(), 1, Integer::sum);
            }
        }

        return new Snapshot(surfaceBlocks, elevations, positions.size());
    }

    /**
     * Compare sample area palette with result area palette
     */
    public static String compareBlockPalettes(TerrainProfile sampleProfile, Snapshot resultSnapshot) {
        StringBuilder comparison = new StringBuilder();
        comparison.append("§6═══ Block Palette Comparison ═══\n");
        comparison.append("§7Sample (3-chunk) vs Result (target area)\n\n");

        Map<Block, Integer> samplePalette = sampleProfile.getSurfaceBlockPalette();
        Map<Block, Integer> resultPalette = resultSnapshot.surfaceBlocks;

        // Get all unique blocks from both
        Set<Block> allBlocks = new HashSet<>();
        allBlocks.addAll(samplePalette.keySet());
        allBlocks.addAll(resultPalette.keySet());

        // Calculate totals
        int sampleTotal = samplePalette.values().stream().mapToInt(Integer::intValue).sum();
        int resultTotal = resultPalette.values().stream().mapToInt(Integer::intValue).sum();

        for (Block block : allBlocks) {
            int sampleCount = samplePalette.getOrDefault(block, 0);
            int resultCount = resultPalette.getOrDefault(block, 0);

            double samplePercent = sampleTotal > 0 ? (double) sampleCount / sampleTotal * 100 : 0;
            double resultPercent = resultTotal > 0 ? (double) resultCount / resultTotal * 100 : 0;
            double diff = resultPercent - samplePercent;

            String diffStr;
            if (Math.abs(diff) < 1) {
                diffStr = "§a≈"; // Nearly identical
            } else if (diff > 0) {
                diffStr = String.format("§c+%.1f%%", diff);
            } else {
                diffStr = String.format("§e%.1f%%", diff);
            }

            String blockName = block.getName().getString().replace("block.minecraft.", "");
            comparison.append(String.format("§7%s: §e%.1f%% §7→ §e%.1f%% §7(%s§7)\n",
                blockName, samplePercent, resultPercent, diffStr));
        }

        return comparison.toString();
    }

    /**
     * Compare before/after and calculate accuracy metrics
     */
    public static ValidationResult validate(Snapshot before, Snapshot after, TerrainProfile expectedProfile) {
        // Consistency accuracy: Did mono areas stay mono?
        double beforeConsistency = before.getConsistency();
        double afterConsistency = after.getConsistency();
        double consistencyMatch = 1.0 - Math.abs(beforeConsistency - afterConsistency);

        // Elevation preservation: Did height range stay similar?
        int beforeRange = before.getElevationRange();
        int afterRange = after.getElevationRange();
        double elevationPreservation = beforeRange > 0 ?
            1.0 - (Math.abs(beforeRange - afterRange) / (double) beforeRange) : 1.0;

        // Block match: Did we use the right dominant block?
        Block expectedBlock = expectedProfile.getDominantSurfaceBlock();
        Block actualBlock = after.getDominantBlock();
        boolean blockMatch = expectedBlock != null && expectedBlock.equals(actualBlock);

        // Overall accuracy score
        double overallScore = (consistencyMatch * 0.4) +
                             (elevationPreservation * 0.4) +
                             (blockMatch ? 0.2 : 0.0);

        return new ValidationResult(
            consistencyMatch,
            elevationPreservation,
            blockMatch,
            overallScore,
            beforeConsistency,
            afterConsistency,
            beforeRange,
            afterRange
        );
    }

    public static class ValidationResult {
        public final double consistencyMatch;
        public final double elevationPreservation;
        public final boolean blockMatch;
        public final double overallScore;
        public final double beforeConsistency;
        public final double afterConsistency;
        public final int beforeRange;
        public final int afterRange;

        public ValidationResult(double consistencyMatch, double elevationPreservation,
                              boolean blockMatch, double overallScore,
                              double beforeConsistency, double afterConsistency,
                              int beforeRange, int afterRange) {
            this.consistencyMatch = consistencyMatch;
            this.elevationPreservation = elevationPreservation;
            this.blockMatch = blockMatch;
            this.overallScore = overallScore;
            this.beforeConsistency = beforeConsistency;
            this.afterConsistency = afterConsistency;
            this.beforeRange = beforeRange;
            this.afterRange = afterRange;
        }

        public String getGrade() {
            if (overallScore >= 0.95) return "§a§lA+";
            if (overallScore >= 0.90) return "§aA";
            if (overallScore >= 0.80) return "§eB";
            if (overallScore >= 0.70) return "§6C";
            return "§c§lNEEDS WORK";
        }
    }
}
