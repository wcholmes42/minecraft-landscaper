package com.wcholmes.landscaper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wcholmes.landscaper.config.NaturalizationConfig;
import com.wcholmes.landscaper.item.NaturalizationStaff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Random;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class HighlightRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Only render during the AFTER_TRANSLUCENT_BLOCKS stage
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // Check if highlight is enabled
        if (!NaturalizationConfig.showHighlight()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        // Check if player is holding Naturalization Staff
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof NaturalizationStaff)) {
            return;
        }

        // Get player's target block
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos targetPos = BlockPos.containing(hitResult.getLocation());

        // Get config values
        int radius = NaturalizationConfig.getRadius();
        boolean isCircle = NaturalizationConfig.isCircleShape();
        boolean messyEdge = NaturalizationConfig.isMessyEdge();

        // Render the highlight
        renderHighlight(event.getPoseStack(), event.getCamera().getPosition(),
                       targetPos, radius, isCircle, messyEdge, mc.renderBuffers().bufferSource());
    }

    private static void renderHighlight(PoseStack poseStack, Vec3 cameraPos,
                                        BlockPos center, int radius, boolean isCircle, boolean messyEdge,
                                        MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();

        // Translate to world position relative to camera
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer builder = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        // Color: White with alpha
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float a = 0.8f;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            poseStack.popPose();
            return;
        }

        // Iterate through the area and show all affected blocks
        int effectiveRadius = radius - 1; // radius 1 = 0 range, radius 2 = 1 range, etc.
        for (int x = -effectiveRadius; x <= effectiveRadius; x++) {
            for (int z = -effectiveRadius; z <= effectiveRadius; z++) {
                // Check if within radius (circle or square)
                boolean withinRadius = isCircle ? (x * x + z * z <= effectiveRadius * effectiveRadius) : true;

                // Apply messy edge effect if enabled
                if (withinRadius && messyEdge) {
                    withinRadius = shouldApplyMessyEdge(x, z, radius, isCircle, center);
                }

                if (withinRadius) {
                    BlockPos pos = center.offset(x, 0, z);
                    BlockPos surfacePos = findSurface(mc.level, pos);

                    if (surfacePos != null) {
                        // Draw outline box around this surface block
                        drawBlockOutline(matrix, builder, surfacePos, r, g, b, a);
                    }
                }
            }
        }

        poseStack.popPose();
    }

    private static void drawBlockOutline(Matrix4f matrix, VertexConsumer builder,
                                         BlockPos pos, float r, float g, float b, float a) {
        double minX = pos.getX();
        double minY = pos.getY() + 1.0; // Top of block
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1.0;
        double maxY = pos.getY() + 1.05; // Slightly above for visibility
        double maxZ = pos.getZ() + 1.0;

        // Draw 4 horizontal lines forming a square on top of the block
        // Bottom-front edge
        builder.vertex(matrix, (float) minX, (float) minY, (float) minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, (float) maxX, (float) minY, (float) minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

        // Right edge
        builder.vertex(matrix, (float) maxX, (float) minY, (float) minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

        // Top-back edge
        builder.vertex(matrix, (float) maxX, (float) minY, (float) maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, (float) minX, (float) minY, (float) maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

        // Left edge
        builder.vertex(matrix, (float) minX, (float) minY, (float) maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, (float) minX, (float) minY, (float) minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
    }

    private static BlockPos findSurface(net.minecraft.world.level.Level level, BlockPos start) {
        // Search upward first
        for (int y = 0; y < 10; y++) {
            BlockPos checkPos = start.offset(0, y, 0);
            net.minecraft.world.level.block.state.BlockState current = level.getBlockState(checkPos);

            if (NaturalizationConfig.getSafeBlocks().contains(current.getBlock())) {
                return checkPos;
            }
        }

        // Search downward
        for (int y = 0; y > -15; y--) {
            BlockPos checkPos = start.offset(0, y, 0);
            net.minecraft.world.level.block.state.BlockState current = level.getBlockState(checkPos);

            if (NaturalizationConfig.getSafeBlocks().contains(current.getBlock())) {
                return checkPos;
            }
        }

        return null;
    }

    private static boolean shouldApplyMessyEdge(int x, int z, int radius, boolean isCircle, BlockPos center) {
        // effectiveRadius calculation: radius 1 = 0, radius 2 = 1, etc.
        int effectiveRadius = radius - 1;

        // Calculate distance from center
        double distance = isCircle ? Math.sqrt(x * x + z * z) : Math.max(Math.abs(x), Math.abs(z));
        double edgeDistance = effectiveRadius - distance;

        // If we're more than 2 blocks from edge, always include
        if (edgeDistance > 2) {
            return true;
        }

        // Use deterministic random based on position so highlight is consistent
        // Combine center position with offset for unique seed per block
        long seed = ((long)center.getX() + x) * 31L + ((long)center.getZ() + z) * 37L;
        Random random = new Random(seed);

        // If we're exactly at or beyond effective radius, randomly extend by 1-2 blocks
        if (edgeDistance <= 0) {
            int extension = random.nextInt(3); // 0, 1, or 2 blocks
            return distance <= effectiveRadius + extension;
        }

        // We're within 2 blocks of edge - randomly fade out
        // Closer to edge = higher chance of being excluded
        double fadeChance = (2.0 - edgeDistance) / 3.0; // 0% at edge-2, 33% at edge-1, 66% at edge
        return random.nextDouble() > fadeChance;
    }
}
