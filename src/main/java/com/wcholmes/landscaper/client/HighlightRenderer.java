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

        // Render the highlight
        renderHighlight(event.getPoseStack(), event.getCamera().getPosition(),
                       targetPos, radius, isCircle, mc.renderBuffers().bufferSource());
    }

    private static void renderHighlight(PoseStack poseStack, Vec3 cameraPos,
                                        BlockPos center, int radius, boolean isCircle,
                                        MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();

        // Translate to world position relative to camera
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumer builder = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        // Color: Green with alpha
        float r = 0.0f;
        float g = 1.0f;
        float b = 0.0f;
        float a = 0.8f;

        double centerX = center.getX() + 0.5;
        double centerY = center.getY() + 1.0; // Slightly above block
        double centerZ = center.getZ() + 0.5;

        if (isCircle) {
            // Draw circle outline
            int segments = Math.max(32, radius * 4); // More segments for larger radius
            for (int i = 0; i < segments; i++) {
                double angle1 = (i * 2 * Math.PI) / segments;
                double angle2 = ((i + 1) * 2 * Math.PI) / segments;

                double x1 = centerX + Math.cos(angle1) * radius;
                double z1 = centerZ + Math.sin(angle1) * radius;
                double x2 = centerX + Math.cos(angle2) * radius;
                double z2 = centerZ + Math.sin(angle2) * radius;

                builder.vertex(matrix, (float) x1, (float) centerY, (float) z1)
                       .color(r, g, b, a)
                       .normal(0f, 1f, 0f)
                       .endVertex();
                builder.vertex(matrix, (float) x2, (float) centerY, (float) z2)
                       .color(r, g, b, a)
                       .normal(0f, 1f, 0f)
                       .endVertex();
            }
        } else {
            // Draw square outline
            double x1 = centerX - radius;
            double x2 = centerX + radius;
            double z1 = centerZ - radius;
            double z2 = centerZ + radius;

            // Top edge
            builder.vertex(matrix, (float) x1, (float) centerY, (float) z1)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
            builder.vertex(matrix, (float) x2, (float) centerY, (float) z1)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

            // Right edge
            builder.vertex(matrix, (float) x2, (float) centerY, (float) z1)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
            builder.vertex(matrix, (float) x2, (float) centerY, (float) z2)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

            // Bottom edge
            builder.vertex(matrix, (float) x2, (float) centerY, (float) z2)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
            builder.vertex(matrix, (float) x1, (float) centerY, (float) z2)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();

            // Left edge
            builder.vertex(matrix, (float) x1, (float) centerY, (float) z2)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
            builder.vertex(matrix, (float) x1, (float) centerY, (float) z1)
                   .color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        }

        poseStack.popPose();
    }
}
