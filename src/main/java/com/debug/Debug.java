package com.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import java.lang.Math;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Debug.MOD_ID)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class Debug {
    public static final String MOD_ID = "debug";  // mods.toml と一致

    private static long startTime = -1;

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // プレイヤー座標を取り込み
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);  // カメラ相対

        RenderSystem.disableDepthTest();  // ブロック越し

        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        if (startTime == -1) {
            startTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // 共通: プレイヤー位置に移動（中心固定）
        poseStack.translate(px, py, pz);

        // 既存図形 (緑四角2つ + 青三角2つ + 黄32角形 + 魔法陣 + 立体四角)
        //drawSquare(poseStack, buffer, consumer, elapsed, 3000, 0.0f, 1.0f, 0.0f, 0.0f, 3);  // 緑1
        //drawSquare(poseStack, buffer, consumer, elapsed, 3000, 0.0f, 1.0f, 0.0f, 45.0f, 3);  // 緑2
        //drawTriangle1(poseStack, buffer, consumer, elapsed, 2000, 0.0f, 0.0f, 1.0f, 0.0f);  // 青三角1 (offset=0)
        //drawTriangle2(poseStack, buffer, consumer, elapsed, 2000, 0.0f, 0.0f, 1.0f, 0.0f);  // 青三角2 (offset=45)
        //draw32gon1(poseStack, buffer, consumer, elapsed, 4000, 1.0f, 1.0f, 0.0f, 0.0f);  // 黄32角形
        //draw32gon2(poseStack, buffer, consumer, elapsed, 4000, 1.0f, 1.0f, 0.0f, 0.0f);  // 黄32角形

        RenderSystem.enableDepthTest();
        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    // 四角描画メソッド (Y軸回転)
    private static void drawSquare(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, double size) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;
        float angleRadians = (float) Math.toRadians(angleDegrees);

        Quaternionf quaternion = new Quaternionf().rotationY(angleRadians);
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        Vec3 vecA = new Vec3(-size, 0, -size);
        Vec3 vecB = new Vec3(size, 0, -size);
        Vec3 vecC = new Vec3(size, 0, size);
        Vec3 vecD = new Vec3(-size, 0, size);

        drawLine(consumer, matrix, vecA, vecB, red, green, blue);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue);
        drawLine(consumer, matrix, vecC, vecD, red, green, blue);
        drawLine(consumer, matrix, vecD, vecA, red, green, blue);

        poseStack.popPose();
    }

    // 三角形描画メソッド (Y軸回転) – offset パラメータ追加で統合
    private static void drawTriangle1(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;
        float angleRadians = (float) Math.toRadians(angleDegrees);

        Quaternionf quaternion = new Quaternionf().rotationY(angleRadians);
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();
        double r = 3;

        double angle1 = (0 + offsetDegrees) * Math.PI / 180;
        double angle2 = (120 + offsetDegrees) * Math.PI / 180;
        double angle3 = (240 + offsetDegrees) * Math.PI / 180;

        Vec3 vecA = new Vec3(r * Math.cos(angle1), 0, r * Math.sin(angle1));
        Vec3 vecB = new Vec3(r * Math.cos(angle2), 0, r * Math.sin(angle2));
        Vec3 vecC = new Vec3(r * Math.cos(angle3), 0, r * Math.sin(angle3));

        drawLine(consumer, matrix, vecA, vecB, red, green, blue);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue);
        drawLine(consumer, matrix, vecC, vecA, red, green, blue);

        poseStack.popPose();
    }
    private static void drawTriangle2(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;
        float angleRadians = (float) Math.toRadians(angleDegrees);

        Quaternionf quaternion = new Quaternionf().rotationY(angleRadians);
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();
        double r = 3;

        double angle1 = (0 + offsetDegrees) * Math.PI / 180 + 45;
        double angle2 = (120 + offsetDegrees) * Math.PI / 180 + 45;
        double angle3 = (240 + offsetDegrees) * Math.PI / 180 + 45;

        Vec3 vecA = new Vec3(r * Math.cos(angle1), 0, r * Math.sin(angle1));
        Vec3 vecB = new Vec3(r * Math.cos(angle2), 0, r * Math.sin(angle2));
        Vec3 vecC = new Vec3(r * Math.cos(angle3), 0, r * Math.sin(angle3));

        drawLine(consumer, matrix, vecA, vecB, red, green, blue);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue);
        drawLine(consumer, matrix, vecC, vecA, red, green, blue);

        poseStack.popPose();
    }

    // 32角形描画メソッド (Y軸回転) – offset 適用修正
    private static void draw32gon1(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;
        float angleRadians = (float) Math.toRadians(angleDegrees);

        Quaternionf quaternion = new Quaternionf().rotationY(angleRadians);
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();
        double r = 4.24;

        for (int i = 0; i < 32; i++) {
            double theta1 = (i * 11.25 + angleDegrees) * Math.PI / 180;  // offset 適用
            double theta2 = ((i + 1) % 32 * 11.25 + angleDegrees) * Math.PI / 180;

            Vec3 vec1 = new Vec3(r * Math.cos(theta1), 0, r * Math.sin(theta1));
            Vec3 vec2 = new Vec3(r * Math.cos(theta2), 0, r * Math.sin(theta2));

            drawLine(consumer, matrix, vec1, vec2, red, green, blue);
        }

        poseStack.popPose();
    }
    private static void draw32gon2(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;
        float angleRadians = (float) Math.toRadians(angleDegrees);

        Quaternionf quaternion = new Quaternionf().rotationY(angleRadians);
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();
        double r = 4.25;

        for (int i = 0; i < 32; i++) {
            double theta1 = (i * 11.25 + angleDegrees) * Math.PI / 180;  // offset 適用
            double theta2 = ((i + 1) % 32 * 11.25 + angleDegrees) * Math.PI / 180;

            Vec3 vec1 = new Vec3(r * Math.cos(theta1), 0, r * Math.sin(theta1));
            Vec3 vec2 = new Vec3(r * Math.cos(theta2), 0, r * Math.sin(theta2));

            drawLine(consumer, matrix, vec1, vec2, red, green, blue);
        }

        poseStack.popPose();
    }
    // 立体四角描画メソッド (X/Y/Z同時回転) – offset 呼び出し修正
    private static void drawSquareXYZ(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, float red, float green, float blue, float offsetDegrees, double size) {
        poseStack.pushPose();  // レイヤー分離

        // 各軸の回転角度計算 (時間ベース)
        float xAngle = (float) ((elapsed % 1000) / 1000.0 * 360.0);  // X軸: 1秒1回転
        float yAngle = (float) ((elapsed % 3000) / 3000.0 * 360.0 + offsetDegrees) % 360.0f;  // Y軸: 3秒1回転 + offset
        float zAngle = (float) ((elapsed % 5000) / 5000.0 * 360.0);  // Z軸: 5秒1回転

        float xRadians = (float) Math.toRadians(xAngle);
        float yRadians = (float) Math.toRadians(yAngle);
        float zRadians = (float) Math.toRadians(zAngle);

        // 同時回転: Z → Y → X の順で適用 (Euler角順序)
        Quaternionf zRot = new Quaternionf().rotationZ(zRadians);
        Quaternionf yRot = new Quaternionf().rotationY(yRadians);
        Quaternionf xRot = new Quaternionf().rotationX(xRadians);
        poseStack.mulPose(zRot);  // 先にZ
        poseStack.mulPose(yRot);  // 次にY
        poseStack.mulPose(xRot);  // 最後にX

        Matrix4f matrix = poseStack.last().pose();

        // 頂点 (XZ平面、Y=0)
        Vec3 vecA = new Vec3(-size, 0, -size);
        Vec3 vecB = new Vec3(size, 0, -size);
        Vec3 vecC = new Vec3(size, 0, size);
        Vec3 vecD = new Vec3(-size, 0, size);

        drawLine(consumer, matrix, vecA, vecB, red, green, blue);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue);
        drawLine(consumer, matrix, vecC, vecD, red, green, blue);
        drawLine(consumer, matrix, vecD, vecA, red, green, blue);

        poseStack.popPose();
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, float red, float green, float blue) {
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color((int)(red * 255), (int)(green * 255), (int)(blue * 255), 255)
                .normal(0, 1, 0)
                .uv2(240, 240)  // 輝き
                .endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color((int)(red * 255), (int)(green * 255), (int)(blue * 255), 255)
                .normal(0, 1, 0)
                .uv2(240, 240)  // 輝き
                .endVertex();
    }
}