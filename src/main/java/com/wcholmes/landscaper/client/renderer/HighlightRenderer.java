package com.wcholmes.landscaper.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wcholmes.landscaper.common.config.NaturalizationConfig;
import com.wcholmes.landscaper.common.item.NaturalizationStaff;
import com.wcholmes.landscaper.common.util.TerrainUtils;
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

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class HighlightRenderer {

    // Rendering constants
    private static final float HIGHLIGHT_Y_OFFSET = 1.01f;  // Just above block surface
    private static final float HIGHLIGHT_COLOR_R = 1.0f;   // White
    private static final float HIGHLIGHT_COLOR_G = 1.0f;
    private static final float HIGHLIGHT_COLOR_B = 1.0f;
    private static final float HIGHLIGHT_COLOR_A = 1.0f;   // Full opacity for better visibility

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
        int messyEdgeExtension = NaturalizationConfig.getMessyEdgeExtension();

        // Render the highlight
        renderHighlight(event.getPoseStack(), event.getCamera().getPosition(),
                       targetPos, radius, isCircle, messyEdgeExtension, mc.renderBuffers().bufferSource());
    }

    private static void renderHighlight(PoseStack poseStack, Vec3 cameraPos,
                                        BlockPos center, int radius, boolean isCircle, int messyEdgeExtension,
                                        MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();

        // Translate to world position relative to camera
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Enable polygon offset to prevent z-fighting and moiré patterns
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-3.0f, -3.0f);  // Push lines away from surfaces in depth buffer

        VertexConsumer builder = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            poseStack.popPose();
            return;
        }

        // Iterate through the area and show all affected blocks
        int effectiveRadius = radius - 1; // radius 1 = 0 range, radius 2 = 1 range, etc.

        // Expand search range if messy edge is enabled
        int searchRadius = messyEdgeExtension > 0 ? effectiveRadius + messyEdgeExtension : effectiveRadius;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                boolean withinRadius;

                if (messyEdgeExtension > 0) {
                    // Use messy edge logic for all blocks in expanded range
                    withinRadius = TerrainUtils.shouldApplyMessyEdge(x, z, radius, isCircle, center, messyEdgeExtension);
                } else {
                    // Standard circle or square check
                    withinRadius = isCircle ? (x * x + z * z <= effectiveRadius * effectiveRadius) : true;
                }

                if (withinRadius) {
                    BlockPos pos = center.offset(x, 0, z);
                    BlockPos surfacePos = TerrainUtils.findSurface(mc.level, pos);

                    if (surfacePos != null) {
                        // Draw outline box around this surface block
                        drawBlockOutline(matrix, builder, surfacePos, HIGHLIGHT_COLOR_R, HIGHLIGHT_COLOR_G, HIGHLIGHT_COLOR_B, HIGHLIGHT_COLOR_A);
                    }
                }
            }
        }

        // Disable polygon offset after rendering
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();

        poseStack.popPose();
    }

    private static void drawBlockOutline(Matrix4f matrix, VertexConsumer builder,
                                         BlockPos pos, float r, float g, float b, float a) {
        float minX = pos.getX();
        float y = pos.getY() + HIGHLIGHT_Y_OFFSET; // Slightly above top of block
        float minZ = pos.getZ();
        float maxX = pos.getX() + 1.0f;
        float maxZ = pos.getZ() + 1.0f;

        // Draw 4 lines forming a square on top of the block
        // All lines at same Y height to avoid any rendering inconsistencies

        // North edge (along X axis, min Z)
        builder.vertex(matrix, minX, y, minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, maxX, y, minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

        // East edge (along Z axis, max X)
        builder.vertex(matrix, maxX, y, minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, maxX, y, maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

        // South edge (along X axis, max Z)
        builder.vertex(matrix, maxX, y, maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, minX, y, maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

        // West edge (along Z axis, min X)
        builder.vertex(matrix, minX, y, maxZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        builder.vertex(matrix, minX, y, minZ)
               .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
    }
}
