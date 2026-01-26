package com.debug;
/*
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod(Debug.MOD_ID)
@Mod.EventBusSubscriber(modid = Debug.MOD_ID, value = Dist.CLIENT)
public class HasiraHandler {
    // === 調整可能！ ===
    public static int RENDER_INTERVAL_TICKS = 1;

    private static long tickCounter = 0;
    private static volatile boolean shouldRenderThisFrame = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter >= RENDER_INTERVAL_TICKS) {
            tickCounter = 0;
            shouldRenderThisFrame = true;  // 次フレームで描画
        } else {
            shouldRenderThisFrame = false;
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!shouldRenderThisFrame) return;  // 間隔制御

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer linesBuffer = bufferSource.getBuffer(RenderType.lines());

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // アニメーション用時間（滑らか）
        float partialTicks = event.getPartialTick();
        long gameTime = mc.level.getGameTime();
        long time = (long) ((gameTime + partialTicks) * 50);

        try {
            GeometricRenderer.drawRotatingPolygon(32, poseStack.last().pose(), linesBuffer, new Vec3(100, 150, 100), 11.3, 0.5f, 0.9f, 1.0f, 1.0f, time, 4000, 0.0f, 3, 0.05);
            GeometricRenderer.drawRotatingSquare(poseStack.last().pose(), linesBuffer, new Vec3(100, 150, 100), 8.0, 0.5f, 0.9f, 1.0f, 1.0f, time, 4000, 45.0f, 1, 0.0);
            GeometricRenderer.drawRotatingTriangle(poseStack.last().pose(), linesBuffer, new Vec3(100, 150, 100), 8.0, 0.5f, 0.9f, 1.0f, 1.0f, time, 4000, 0.0f, 5, 0.06);
            GeometricRenderer.drawRotatingTriangle(poseStack.last().pose(), linesBuffer, new Vec3(100, 150, 100), 8.0, 0.5f, 0.9f, 1.0f, 1.0f, time, 4000, 60.0f, 5, 0.06);
            poseStack.pushPose();
            poseStack.translate(100, 140, 100);
            GeometricRenderer.drawLinearAnimatingSphereWireframe(linesBuffer, poseStack.last().pose(), new Vec3(100,100,100), 2.0f, 10.0f, 4000L, time, true, 32, 32, 0.5f, 0.9f, 1.0f, 1.0f);
            poseStack.popPose();
            //poseStack.pushPose();
            //poseStack.translate(110, 80, 110);
            //GeometricRenderer.drawSphereWireframe(linesBuffer, poseStack.last().pose(), 5.0f, 32, 32, 1.0f, 1.0f, 1.0f, 1.0f);
            //poseStack.popPose();
        }
        finally {
            poseStack.popPose();
            bufferSource.endBatch(RenderType.lines());
        }
    }
}*/