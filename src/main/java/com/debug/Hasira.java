package com.debug;

import com.google.j2objc.annotations.ReflectionSupport;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.gen.Invoker;
import static com.mojang.text2speech.Narrator.LOGGER;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Hasira {
    private static long clientTick = 0;
    private static final long SPAWN_INTERVAL_TICKS = 1200;
    private static final double SPAWN_RADIUS = 50.0;
    private static final double EFFECT_Y = 128.0;

    private static final List<Effect> activeEffects = new CopyOnWriteArrayList<>();
    private static final Random random = new Random();

    private static class Effect {
        public final Vec3 center;
        public final long startTick;
        public final int type;
        public final int fadeInTicks;
        public final long removalTick;
        public final long groupId;
        public Effect(Vec3 c, long s, int t, int f, long rTick, long gid) {
            center = c; startTick = s; type = t; fadeInTicks = f; removalTick = rTick; groupId = gid;
        }
    }
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.END) return;
        clientTick++;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (clientTick % SPAWN_INTERVAL_TICKS == 0) {
            double px = mc.player.getX();
            double pz = mc.player.getZ();

            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = random.nextDouble() * SPAWN_RADIUS;
            double ox = Math.cos(angle) * r;
            double oz = Math.sin(angle) * r;

            long groupId = clientTick;

            Vec3 center1 = new Vec3(px + ox, EFFECT_Y, pz + oz);
            long secondStart = clientTick + 5 * 20;
            Vec3 center2 = new Vec3(px + ox, EFFECT_Y + 20.0, pz + oz);
            long thirdStart = secondStart + 10 * 20;
            Vec3 center3 = new Vec3(px + ox, EFFECT_Y + 60.0, pz + oz);

            int fadeInTicks = 2 * 20;
            long thirdRemoval = thirdStart + fadeInTicks + 10 * 20;

            activeEffects.add(new Effect(center1, clientTick, 1, 0, -1L, groupId));
            activeEffects.add(new Effect(center2, secondStart, 2, fadeInTicks, -1L, groupId));
            activeEffects.add(new Effect(center3, thirdStart, 3, fadeInTicks, thirdRemoval, groupId));
        }

        Set<Long> groupsToRemove = new HashSet<>();
        Iterator<Effect> iter = activeEffects.iterator();
        while (iter.hasNext()) {
            Effect e = iter.next();
            if (e.removalTick > 0 && clientTick >= e.removalTick) {
                groupsToRemove.add(e.groupId);
            }
        }
        activeEffects.removeIf(e -> groupsToRemove.contains(e.groupId));
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            MultiBufferSource buffer = mc.renderBuffers().bufferSource();
            VertexConsumer lineConsumer = buffer.getBuffer(RenderType.lines());

            for (Effect e : new ArrayList<>(activeEffects)) {
                long age = clientTick - e.startTick;
                if (age < 0) continue;

                float alpha = 1.0f;
                if (e.fadeInTicks > 0) {
                    alpha = Math.min(1.0f, (float) age / (float) e.fadeInTicks);
                    if (alpha <= 0f) continue;
                }

                long elapsedMillis = age * 50;

                double size;
                double polygonR;
                if (e.type == 1) {
                    size = 6.0;
                    polygonR = 8.48;
                } else if (e.type == 2) {
                    size = 12.0;
                    polygonR = 16.96;
                } else {
                    size = 24.0;
                    polygonR = 33.92;
                }

                poseStack.pushPose();
                poseStack.translate(e.center.x, e.center.y, e.center.z);

                float whiteAlpha = alpha;
                drawSquare(poseStack, buffer, lineConsumer, elapsedMillis, 3000, 1.0f, 1.0f, 1.0f, 0.0f, size, whiteAlpha);
                drawSquare(poseStack, buffer, lineConsumer, elapsedMillis, 3000, 1.0f, 1.0f, 1.0f, 45.0f, size, whiteAlpha);
                drawTriangle1(poseStack, buffer, lineConsumer, elapsedMillis, 2000, 1.0f, 1.0f, 1.0f, 0.0f, (float) size, whiteAlpha);
                drawTriangle2(poseStack, buffer, lineConsumer, elapsedMillis, 2000, 1.0f, 1.0f, 1.0f, 0.0f, (float) size, whiteAlpha);
                draw32gon1(poseStack, buffer, lineConsumer, elapsedMillis, 4000, 1.0f, 1.0f, 1.0f, 0.0f, whiteAlpha, polygonR);
                draw32gon2(poseStack, buffer, lineConsumer, elapsedMillis, 4000, 1.0f, 1.0f, 1.0f, 0.0f, whiteAlpha, polygonR + 0.02);

                float cyanAlpha = Math.min(1.0f, 0.7f * alpha);
                float cr = 0.5f, cg = 0.9f, cb = 1.0f;
                double thicknessFactor = Math.max(0.06, size * 0.0025);

                drawSquareThick(poseStack, buffer, lineConsumer, elapsedMillis, 3000, cr, cg, cb, 0.0f, size, cyanAlpha, thicknessFactor);
                drawSquareThick(poseStack, buffer, lineConsumer, elapsedMillis, 3000, cr, cg, cb, 45.0f, size, cyanAlpha, thicknessFactor);
                drawTriangle1Thick(poseStack, buffer, lineConsumer, elapsedMillis, 2000, cr, cg, cb, 0.0f, (float) size, cyanAlpha, thicknessFactor);
                drawTriangle2Thick(poseStack, buffer, lineConsumer, elapsedMillis, 2000, cr, cg, cb, 0.0f, (float) size, cyanAlpha, thicknessFactor);
                draw32gon1Thick(poseStack, buffer, lineConsumer, elapsedMillis, 4000, cr, cg, cb, 0.0f, cyanAlpha, polygonR, thicknessFactor);
                draw32gon2Thick(poseStack, buffer, lineConsumer, elapsedMillis, 4000, cr, cg, cb, 0.0f, cyanAlpha, polygonR + 0.02, thicknessFactor);

                poseStack.popPose();
            }

            // 球体描画
            for (Effect e : new ArrayList<>(activeEffects)) {
                if (e.type == 1) {
                    boolean groupActive = false;
                    Effect type3Effect = null;
                    for (Effect e3 : activeEffects) {
                        if (e3.groupId == e.groupId && e3.type == 3) {
                            long age3 = clientTick - e3.startTick;
                            if (e3.removalTick < 0 || age3 < (e3.removalTick - e3.startTick)) {
                                groupActive = true;
                                type3Effect = e3;
                                break;
                            }
                        }
                    }
                    if (groupActive && type3Effect != null) {
                        Vec3 sphereCenter = new Vec3(e.center.x, e.center.y - 5.0, e.center.z);
                        double radius;
                        long type1Start = e.startTick;
                        long type3Start = type3Effect.startTick;
                        if (clientTick < type3Start) {
                            long durationToType3 = type3Start - type1Start;
                            double progress = Math.min(1.0, (double) (clientTick - type1Start) / durationToType3);
                            radius = 1.0 + 4.0 * progress;
                        } else {
                            radius = 5.0;
                        }

                        long elapsedMillis = (clientTick - type1Start) * 50;
                        float animRoll = (float) ((elapsedMillis % 36000) / 100.0);
                        float animPitch = (float) ((elapsedMillis % 18000) / 50.0);

                        poseStack.pushPose();
                        poseStack.translate(sphereCenter.x, sphereCenter.y, sphereCenter.z);

                        Quaternionf rollQuat = new Quaternionf().rotationY((float) Math.toRadians(animRoll));
                        Quaternionf pitchQuat = new Quaternionf().rotationX((float) Math.toRadians(animPitch));
                        poseStack.mulPose(rollQuat);
                        poseStack.mulPose(pitchQuat);

                        // 例: RenderLayer や HUD のレンダリングコール内（レンダースレッドで実行すること）
                        //ResourceLocation white = new ResourceLocation("textures/atlas/debug_atlas.png");
                        //LOGGER.info("Using texture: {}", white);
                        // 既存の呼び出しを以下に置き換え
                        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
                        /*RenderType whiteFillType = RenderType.create(
                                "white_fill",
                                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                                VertexFormat.Mode.TRIANGLES,
                                256,
                                false,
                               false,
                                RenderType.CompositeState.builder()
                                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                                        .setTextureState(RenderStateShard.NO_TEXTURE)
                                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                                        .setLightmapState(RenderStateShard.LIGHTMAP)
                                        .setOverlayState(RenderStateShard.OVERLAY)
                                        .createCompositeState(false)
                        );
                        VertexConsumer consumer = buffer.getBuffer(whiteFillType);*/
                        //VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(net.minecraft.util.CommonColors.WHITE));
                        //ResourceLocation white = new ResourceLocation("minecraft", "block/white_concrete");
                        UltimateSphereRenderer.drawSphereWireframe(
                                consumer,
                                poseStack.last().pose(),
                                (float) radius,
                                128,   // slices(最大512)
                                128   // stacks（同上）
                        );
                        poseStack.popPose();
                    }
                }
            }
        }
        finally {
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }

    private static Vec3 add(Vec3 a, Vec3 b) { return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z); }

        // ====== 毎フレーム呼べる最強関数（slices/stacks 512までOK）======
        public static final class UltimateSphereRenderer {

            // テーブルは同じ（再利用）
            private static final int MAX_SLICES = 512;
            private static final int MAX_STACKS = 512;

            private static final float[] PHI_SIN = new float[MAX_SLICES + 1];
            private static final float[] PHI_COS = new float[MAX_SLICES + 1];
            private static final float[] THETA_COS = new float[MAX_STACKS + 1];
            private static final float[] THETA_SIN = new float[MAX_STACKS + 1];

            static {
                for (int i = 0; i <= MAX_SLICES; i++) {
                    float phi = (float) Math.PI * i / MAX_SLICES - (float) Math.PI / 2.0f;
                    PHI_SIN[i] = Mth.sin(phi);
                    PHI_COS[i] = Mth.cos(phi);
                }
                for (int j = 0; j <= MAX_STACKS; j++) {
                    float theta = (float) (j * Math.PI * 2.0f / MAX_STACKS);
                    THETA_COS[j] = Mth.cos(theta);
                    THETA_SIN[j] = Mth.sin(theta);
                }
            }

            /**
             * 球の輪郭を線で描画（ワイヤーフレーム風）
             */
            public static void drawSphereWireframe(
                    VertexConsumer consumer,
                    Matrix4f pose,
                    float radius,
                    int slices,
                    int stacks
            ) {
                if (!Float.isFinite(radius) || radius <= 0f) return;

                slices = Mth.clamp(slices, 3, MAX_SLICES);
                stacks = Mth.clamp(stacks, 3, MAX_STACKS);

                int packedLight = LightTexture.FULL_BRIGHT;

                // 緯度線（横の輪）
                for (int i = 0; i <= slices; i++) {
                    float phi = (float) Math.PI * i / slices - (float) Math.PI / 2.0f;
                    float y = radius * Mth.sin(phi);
                    float r = radius * Mth.cos(phi);

                    for (int j = 0; j < stacks; j++) {
                        float theta0 = (float) (j * Math.PI * 2.0f / stacks);
                        float theta1 = (float) ((j + 1) * Math.PI * 2.0f / stacks);

                        float x0 = r * Mth.cos(theta0);
                        float z0 = r * Mth.sin(theta0);
                        float x1 = r * Mth.cos(theta1);
                        float z1 = r * Mth.sin(theta1);

                        // 線分1点目
                        consumer.vertex(pose, x0, y, z0)
                                .color(1.0f, 1.0f, 1.0f, 1.0f)
                                .normal(0.0f, 1.0f, 0.0f)  // ダミー法線（必須）
                                .uv2(packedLight)
                                .endVertex();

                        // 線分2点目
                        consumer.vertex(pose, x1, y, z1)
                                .color(1.0f, 1.0f, 1.0f, 1.0f)
                                .normal(0.0f, 1.0f, 0.0f)
                                .uv2(packedLight)
                                .endVertex();
                    }
                }

                // 経度線（縦の輪）
                for (int j = 0; j < stacks; j++) {
                    float theta = (float) (j * Math.PI * 2.0f / stacks);
                    float cosTheta = Mth.cos(theta);
                    float sinTheta = Mth.sin(theta);

                    for (int i = 0; i < slices; i++) {
                        float phi0 = (float) Math.PI * i / slices - (float) Math.PI / 2.0f;
                        float phi1 = (float) Math.PI * (i + 1) / slices - (float) Math.PI / 2.0f;

                        float y0 = radius * Mth.sin(phi0);
                        float r0 = radius * Mth.cos(phi0);
                        float y1 = radius * Mth.sin(phi1);
                        float r1 = radius * Mth.cos(phi1);

                        float x0 = r0 * cosTheta;
                        float z0 = r0 * sinTheta;
                        float x1 = r1 * cosTheta;
                        float z1 = r1 * sinTheta;

                        consumer.vertex(pose, x0, y0, z0)
                                .color(1.0f, 1.0f, 1.0f, 1.0f)
                                .normal(0.0f, 1.0f, 0.0f)
                                .uv2(packedLight)
                                .endVertex();

                        consumer.vertex(pose, x1, y1, z1)
                                .color(1.0f, 1.0f, 1.0f, 1.0f)
                                .normal(0.0f, 1.0f, 0.0f)
                                .uv2(packedLight)
                                .endVertex();
                    }
                }
            }

            // LODはそのまま使える
            public static int[] recommendedResolutionByDistance(float distance) {
                if (distance < 8f) return new int[]{128, 128};
                if (distance < 24f) return new int[]{64, 64};
                if (distance < 64f) return new int[]{32, 32};
                return new int[]{16, 16};
            }
        }




        private static void drawSquare(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, double size, float alpha) {
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

        drawLine(consumer, matrix, vecA, vecB, red, green, blue, alpha);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue, alpha);
        drawLine(consumer, matrix, vecC, vecD, red, green, blue, alpha);
        drawLine(consumer, matrix, vecD, vecA, red, green, blue, alpha);

        poseStack.popPose();
    }

    private static void drawSquareThick(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, double size, float alpha, double thicknessOffset) {
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

        for (int pass = -2; pass <= 2; pass++) {
            double offset = pass * thicknessOffset;
            Vec3 o = new Vec3(offset, 0, offset);
            drawLine(consumer, matrix, add(vecA, o), add(vecB, o), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecB, o), add(vecC, o), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecC, o), add(vecD, o), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecD, o), add(vecA, o), red, green, blue, alpha);
        }

        poseStack.popPose();
    }

    private static void drawTriangle1(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, double r, float alpha) {
        poseStack.pushPose();
        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;
        float angleRadians = (float) Math.toRadians(angleDegrees);

        Quaternionf quaternion = new Quaternionf().rotationY(angleRadians);
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        double angle1 = (0 + offsetDegrees) * Math.PI / 180;
        double angle2 = (120 + offsetDegrees) * Math.PI / 180;
        double angle3 = (240 + offsetDegrees) * Math.PI / 180;

        Vec3 vecA = new Vec3(r * Math.cos(angle1), 0, r * Math.sin(angle1));
        Vec3 vecB = new Vec3(r * Math.cos(angle2), 0, r * Math.sin(angle2));
        Vec3 vecC = new Vec3(r * Math.cos(angle3), 0, r * Math.sin(angle3));

        drawLine(consumer, matrix, vecA, vecB, red, green, blue, alpha);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue, alpha);
        drawLine(consumer, matrix, vecC, vecA, red, green, blue, alpha);

        poseStack.popPose();
    }

    private static void drawTriangle1Thick(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, float r, float alpha, double thicknessOffset) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        double angle1 = (0 + offsetDegrees) * Math.PI / 180;
        double angle2 = (120 + offsetDegrees) * Math.PI / 180;
        double angle3 = (240 + offsetDegrees) * Math.PI / 180;

        Vec3 vecA = new Vec3(r * Math.cos(angle1), 0, r * Math.sin(angle1));
        Vec3 vecB = new Vec3(r * Math.cos(angle2), 0, r * Math.sin(angle2));
        Vec3 vecC = new Vec3(r * Math.cos(angle3), 0, r * Math.sin(angle3));

        for (int pass = -2; pass <= 2; pass++) {
            double o = pass * thicknessOffset;
            Vec3 off = new Vec3(o, 0, o);
            drawLine(consumer, matrix, add(vecA, off), add(vecB, off), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecB, off), add(vecC, off), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecC, off), add(vecA, off), red, green, blue, alpha);
        }

        poseStack.popPose();
    }

    private static void drawTriangle2(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, double r, float alpha) {
        poseStack.pushPose();
        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        double angle1 = (0 + offsetDegrees) * Math.PI / 180 + Math.toRadians(45);
        double angle2 = (120 + offsetDegrees) * Math.PI / 180 + Math.toRadians(45);
        double angle3 = (240 + offsetDegrees) * Math.PI / 180 + Math.toRadians(45);

        Vec3 vecA = new Vec3(r * Math.cos(angle1), 0, r * Math.sin(angle1));
        Vec3 vecB = new Vec3(r * Math.cos(angle2), 0, r * Math.sin(angle2));
        Vec3 vecC = new Vec3(r * Math.cos(angle3), 0, r * Math.sin(angle3));

        drawLine(consumer, matrix, vecA, vecB, red, green, blue, alpha);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue, alpha);
        drawLine(consumer, matrix, vecC, vecA, red, green, blue, alpha);

        poseStack.popPose();
    }

    private static void drawTriangle2Thick(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, float r, float alpha, double thicknessOffset) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        double angle1 = (0 + offsetDegrees) * Math.PI / 180 + Math.toRadians(45);
        double angle2 = (120 + offsetDegrees) * Math.PI / 180 + Math.toRadians(45);
        double angle3 = (240 + offsetDegrees) * Math.PI / 180 + Math.toRadians(45);

        Vec3 vecA = new Vec3(r * Math.cos(angle1), 0, r * Math.sin(angle1));
        Vec3 vecB = new Vec3(r * Math.cos(angle2), 0, r * Math.sin(angle2));
        Vec3 vecC = new Vec3(r * Math.cos(angle3), 0, r * Math.sin(angle3));

        for (int pass = -2; pass <= 2; pass++) {
            double o = pass * thicknessOffset;
            Vec3 off = new Vec3(o, 0, o);
            drawLine(consumer, matrix, add(vecA, off), add(vecB, off), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecB, off), add(vecC, off), red, green, blue, alpha);
            drawLine(consumer, matrix, add(vecC, off), add(vecA, off), red, green, blue, alpha);
        }

        poseStack.popPose();
    }

    private static void draw32gon1(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, float alpha, double r) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < 32; i++) {
            double theta1 = (i * 11.25 + angleDegrees) * Math.PI / 180;
            double theta2 = (((i + 1) % 32) * 11.25 + angleDegrees) * Math.PI / 180;

            Vec3 vec1 = new Vec3(r * Math.cos(theta1), 0, r * Math.sin(theta1));
            Vec3 vec2 = new Vec3(r * Math.cos(theta2), 0, r * Math.sin(theta2));

            drawLine(consumer, matrix, vec1, vec2, red, green, blue, alpha);
        }

        poseStack.popPose();
    }

    private static void draw32gon1Thick(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, float alpha, double r, double thicknessOffset) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < 32; i++) {
            double theta1 = (i * 11.25 + angleDegrees) * Math.PI / 180;
            double theta2 = (((i + 1) % 32) * 11.25 + angleDegrees) * Math.PI / 180;

            Vec3 vec1 = new Vec3(r * Math.cos(theta1), 0, r * Math.sin(theta1));
            Vec3 vec2 = new Vec3(r * Math.cos(theta2), 0, r * Math.sin(theta2));

            for (int pass = -2; pass <= 2; pass++) {
                double o = pass * thicknessOffset;
                Vec3 off = new Vec3(o, 0, o);
                drawLine(consumer, matrix, add(vec1, off), add(vec2, off), red, green, blue, alpha);
            }
        }

        poseStack.popPose();
    }

    private static void draw32gon2(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, float alpha, double r) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < 32; i++) {
            double theta1 = (i * 11.25 + angleDegrees) * Math.PI / 180;
            double theta2 = (((i + 1) % 32) * 11.25 + angleDegrees) * Math.PI / 180;

            Vec3 vec1 = new Vec3(r * Math.cos(theta1), 0, r * Math.sin(theta1));
            Vec3 vec2 = new Vec3(r * Math.cos(theta2), 0, r * Math.sin(theta2));

            drawLine(consumer, matrix, vec1, vec2, red, green, blue, alpha);
        }

        poseStack.popPose();
    }

    private static void draw32gon2Thick(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, long period, float red, float green, float blue, float offsetDegrees, float alpha, double r, double thicknessOffset) {
        poseStack.pushPose();

        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        float angleDegrees = (baseAngleDegrees + offsetDegrees) % 360.0f;

        Quaternionf quaternion = new Quaternionf().rotationY((float)Math.toRadians(angleDegrees));
        poseStack.mulPose(quaternion);

        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < 32; i++) {
            double theta1 = (i * 11.25 + angleDegrees) * Math.PI / 180;
            double theta2 = (((i + 1) % 32) * 11.25 + angleDegrees) * Math.PI / 180;

            Vec3 vec1 = new Vec3(r * Math.cos(theta1), 0, r * Math.sin(theta1));
            Vec3 vec2 = new Vec3(r * Math.cos(theta2), 0, r * Math.sin(theta2));

            for (int pass = -2; pass <= 2; pass++) {
                double o = pass * thicknessOffset;
                Vec3 off = new Vec3(o, 0, o);
                drawLine(consumer, matrix, add(vec1, off), add(vec2, off), red, green, blue, alpha);
            }
        }

        poseStack.popPose();
    }

    private static void drawSquareXYZ(PoseStack poseStack, MultiBufferSource buffer, VertexConsumer consumer, long elapsed, float red, float green, float blue, float offsetDegrees, double size, float alpha) {
        poseStack.pushPose();

        float xAngle = (float) ((elapsed % 1000) / 1000.0 * 360.0f);
        float yAngle = (float) ((elapsed % 3000) / 3000.0 * 360.0f + offsetDegrees) % 360.0f;
        float zAngle = (float) ((elapsed % 5000) / 5000.0 * 360.0f);

        Quaternionf zRot = new Quaternionf().rotationZ((float)Math.toRadians(zAngle));
        Quaternionf yRot = new Quaternionf().rotationY((float)Math.toRadians(yAngle));
        Quaternionf xRot = new Quaternionf().rotationX((float)Math.toRadians(xAngle));
        poseStack.mulPose(zRot);
        poseStack.mulPose(yRot);
        poseStack.mulPose(xRot);

        Matrix4f matrix = poseStack.last().pose();

        Vec3 vecA = new Vec3(-size, 0, -size);
        Vec3 vecB = new Vec3(size, 0, -size);
        Vec3 vecC = new Vec3(size, 0, size);
        Vec3 vecD = new Vec3(-size, 0, size);

        drawLine(consumer, matrix, vecA, vecB, red, green, blue, alpha);
        drawLine(consumer, matrix, vecB, vecC, red, green, blue, alpha);
        drawLine(consumer, matrix, vecC, vecD, red, green, blue, alpha);
        drawLine(consumer, matrix, vecD, vecA, red, green, blue, alpha);

        poseStack.popPose();
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, float red, float green, float blue, float alpha) {
        int ri = Math.max(0, Math.min(255, (int)(red * 255)));
        int gi = Math.max(0, Math.min(255, (int)(green * 255)));
        int bi = Math.max(0, Math.min(255, (int)(blue * 255)));
        int ai = Math.max(0, Math.min(255, (int)(alpha * 255)));
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color(ri, gi, bi, ai)
                .normal(0, 1, 0)
                .uv2(240, 240)
                .endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color(ri, gi, bi, ai)
                .normal(0, 1, 0)
                .uv2(240, 240)
                .endVertex();
    }
}