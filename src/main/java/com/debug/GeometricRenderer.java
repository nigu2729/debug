package com.debug;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID, value = Dist.CLIENT)
public class GeometricRenderer {
    private static final List<ShapeInstance> shapes = new CopyOnWriteArrayList<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        long currentTime = System.currentTimeMillis();
        Matrix4f matrix = poseStack.last().pose();

        // 登録されたすべての図形を描画
        for (ShapeInstance shape : shapes) {
            shape.render(matrix, consumer, currentTime);
        }

        bufferSource.endBatch(RenderType.lines());
    }

    public abstract static class ShapeInstance {
        public final Vec3 center;
        public final float r, g, b, a;

        protected ShapeInstance(Vec3 center, float r, float g, float b, float a) {
            this.center = center;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
        public abstract void render(Matrix4f matrix, VertexConsumer consumer, long currentTimeMillis);
    }

    private static class RotatingSquareInstance extends ShapeInstance {
        final double size;
        final long periodMillis;
        final float offsetDegrees;
        final int thicknessLayers;
        final double thicknessOffset;

        RotatingSquareInstance(Vec3 center, double size, float r, float g, float b, float a,
                               long periodMillis, float offsetDegrees, int thicknessLayers, double thicknessOffset) {
            super(center, r, g, b, a);
            this.size = size;
            this.periodMillis = periodMillis;
            this.offsetDegrees = offsetDegrees;
            this.thicknessLayers = thicknessLayers;
            this.thicknessOffset = thicknessOffset;
        }

        @Override
        public void render(Matrix4f matrix, VertexConsumer consumer, long currentTimeMillis) {
            float angleRadians = calculateRotation(currentTimeMillis, periodMillis, offsetDegrees);
            Matrix4f rotated = applyRotation(matrix, center, angleRadians);

            Vec3 vA = new Vec3(-size, 0, -size);
            Vec3 vB = new Vec3(size, 0, -size);
            Vec3 vC = new Vec3(size, 0, size);
            Vec3 vD = new Vec3(-size, 0, size);

            drawThickLineLoop(consumer, rotated, thicknessLayers, thicknessOffset, r, g, b, a, vA, vB, vC, vD);
        }
    }

    private static class AnimatingSphereInstance extends ShapeInstance {
        final float startRadius;
        final float endRadius;
        final long periodMillis;
        final boolean isExpanding;
        final long startTime;
        final int slices;
        final int stacks;

        AnimatingSphereInstance(Vec3 center, float startRadius, float endRadius,
                                long periodMillis, boolean isExpanding,
                                int slices, int stacks,
                                float r, float g, float b, float a) {
            super(center, r, g, b, a);
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.periodMillis = periodMillis;
            this.startTime = System.currentTimeMillis();
            this.isExpanding = isExpanding;
            this.slices = slices;
            this.stacks = stacks;
        }

        @Override
        public void render(Matrix4f matrix, VertexConsumer consumer, long currentTimeMillis) {
            float currentRadius = calculateLinearRadius(startRadius, endRadius, periodMillis,
                    currentTimeMillis - startTime, isExpanding);
            Matrix4f translated = new Matrix4f(matrix);
            translated.translate((float) center.x, (float) center.y, (float) center.z);

            SphereRenderer.draw(consumer, translated, currentRadius, slices, stacks, r, g, b, a);
        }
    }

    // =========================================================================
    //  メイン描画メソッド群
    // =========================================================================

    /**
     * 回転する正方形を描画します。
     *
     * @param matrix          現在の描画行列 (poseStack.last().pose())
     * @param consumer        描画用バッファ (RenderType.lines())
     * @param center          中心座標 (ワールド座標ではなく、現在のPoseStack基準の相対座標推奨)
     * @param size            中心から頂点までの距離（半分の幅のようなもの）
     * @param red             赤 (0.0 - 1.0)
     * @param green           緑 (0.0 - 1.0)
     * @param blue            青 (0.0 - 1.0)
     * @param alpha           透明度 (0.0 - 1.0)
     * @param elapsedMillis   アニメーション経過時間 (ミリ秒)
     * @param periodMillis    1回転にかかる時間 (ミリ秒)
     * @param offsetDegrees   初期回転角度 (度数法)
     * @param thicknessLayers 線の太さ（重ね描き回数）。1なら通常、2以上で太くなる。
     * @param thicknessOffset 太くする場合の線のずらし幅 (例: 0.06)
     */
    public static void drawRotatingSquare(
            Matrix4f matrix, VertexConsumer consumer,
            Vec3 center, double size,
            float red, float green, float blue, float alpha,
            long elapsedMillis, long periodMillis, float offsetDegrees,
            int thicknessLayers, double thicknessOffset
    ) {
        float angleRadians = calculateRotation(elapsedMillis, periodMillis, offsetDegrees);
        Matrix4f rotatedMatrix = applyRotation(matrix, center, angleRadians);

        // 正方形の頂点定義
        Vec3 vA = new Vec3(-size, 0, -size);
        Vec3 vB = new Vec3(size, 0, -size);
        Vec3 vC = new Vec3(size, 0, size);
        Vec3 vD = new Vec3(-size, 0, size);

        drawThickLineLoop(consumer, rotatedMatrix, thicknessLayers, thicknessOffset, red, green, blue, alpha, vA, vB, vC, vD);
    }

    /**
     * 回転する正三角形を描画します。
     *
     * @param matrix          現在の描画行列
     * @param consumer        描画用バッファ
     * @param center          中心座標
     * @param radius          外接円の半径
     * @param red             赤
     * @param green           緑
     * @param blue            青
     * @param alpha           透明度
     * @param elapsedMillis   経過時間
     * @param periodMillis    周期
     * @param offsetDegrees   初期角度
     * @param thicknessLayers 太さレイヤー数
     * @param thicknessOffset 太さオフセット
     */
    public static void drawRotatingTriangle(
            Matrix4f matrix, VertexConsumer consumer,
            Vec3 center, double radius,
            float red, float green, float blue, float alpha,
            long elapsedMillis, long periodMillis, float offsetDegrees,
            int thicknessLayers, double thicknessOffset
    ) {
        float angleRadians = calculateRotation(elapsedMillis, periodMillis, offsetDegrees);
        Matrix4f rotatedMatrix = applyRotation(matrix, center, angleRadians);

        // 三角形の頂点計算 (0度, 120度, 240度)
        // 図形自体の形状定義にも offsetDegrees を使うロジックが元コードにあったため考慮
        double offRad = Math.toRadians(offsetDegrees);
        Vec3 vA = getCirclePoint(radius, Math.toRadians(0) + offRad);
        Vec3 vB = getCirclePoint(radius, Math.toRadians(120) + offRad);
        Vec3 vC = getCirclePoint(radius, Math.toRadians(240) + offRad);

        drawThickLineLoop(consumer, rotatedMatrix, thicknessLayers, thicknessOffset, red, green, blue, alpha, vA, vB, vC);
    }

    /**
     * 回転する多角形（N角形）を描画します。元コードのdraw32gonに相当します。
     *
     * @param verticesCount   頂点数 (例: 32)
     * @param matrix          現在の描画行列
     * @param consumer        描画用バッファ
     * @param center          中心座標
     * @param radius          半径
     * @param red             赤
     * @param green           緑
     * @param blue            青
     * @param alpha           透明度
     * @param elapsedMillis   経過時間
     * @param periodMillis    周期
     * @param offsetDegrees   初期角度
     * @param thicknessLayers 太さレイヤー数
     * @param thicknessOffset 太さオフセット
     */
    public static void drawRotatingPolygon(
            int verticesCount,
            Matrix4f matrix, VertexConsumer consumer,
            Vec3 center, double radius,
            float red, float green, float blue, float alpha,
            long elapsedMillis, long periodMillis, float offsetDegrees,
            int thicknessLayers, double thicknessOffset
    ) {
        float angleRadians = calculateRotation(elapsedMillis, periodMillis, offsetDegrees);
        Matrix4f rotatedMatrix = applyRotation(matrix, center, angleRadians);

        Vec3[] vertices = new Vec3[verticesCount];
        double step = 2.0 * Math.PI / verticesCount;
        double shapeOffset = Math.toRadians(offsetDegrees); // 形状自体のオフセット

        for (int i = 0; i < verticesCount; i++) {
            vertices[i] = getCirclePoint(radius, i * step + shapeOffset);
        }

        drawThickLineLoop(consumer, rotatedMatrix, thicknessLayers, thicknessOffset, red, green, blue, alpha, vertices);
    }

    /**
     * XYZ 3軸回転する立方体のような矩形を描画します (drawSquareXYZ相当)
     */
    public static void draw3DAxisRotatingSquare(
            Matrix4f matrix, VertexConsumer consumer,
            Vec3 center, double size,
            float red, float green, float blue, float alpha,
            long elapsedMillis, float offsetDegrees
    ) {
        // 回転計算
        float xAngle = (float) ((elapsedMillis % 1000) / 1000.0 * 360.0f);
        float yAngle = (float) ((elapsedMillis % 3000) / 3000.0 * 360.0f + offsetDegrees) % 360.0f;
        float zAngle = (float) ((elapsedMillis % 5000) / 5000.0 * 360.0f);

        Quaternionf rot = new Quaternionf()
                .rotateZ((float) Math.toRadians(zAngle))
                .rotateY((float) Math.toRadians(yAngle))
                .rotateX((float) Math.toRadians(xAngle));

        Matrix4f copyMat = new Matrix4f(matrix);
        copyMat.translate((float) center.x, (float) center.y, (float) center.z);
        copyMat.rotate(rot);

        Vec3 vA = new Vec3(-size, 0, -size);
        Vec3 vB = new Vec3(size, 0, -size);
        Vec3 vC = new Vec3(size, 0, size);
        Vec3 vD = new Vec3(-size, 0, size);

        // シンプルに1回描画
        drawLine(consumer, copyMat, vA, vB, red, green, blue, alpha);
        drawLine(consumer, copyMat, vB, vC, red, green, blue, alpha);
        drawLine(consumer, copyMat, vC, vD, red, green, blue, alpha);
        drawLine(consumer, copyMat, vD, vA, red, green, blue, alpha);
    }

    /**
     * ワイヤーフレーム球体を描画します。
     *
     * @param consumer 描画用バッファ
     * @param matrix   描画行列 (位置合わせ済みであること)
     * @param radius   半径
     * @param slices   経度方向の分割数 (横方向)
     * @param stacks   緯度方向の分割数 (縦方向)
     * @param red      赤
     * @param green    緑
     * @param blue     青
     * @param alpha    透明度
     */
    public static void drawSphereWireframe(
            VertexConsumer consumer, Matrix4f matrix,
            float radius, int slices, int stacks,
            float red, float green, float blue, float alpha
    ) {
        SphereRenderer.draw(consumer, matrix, radius, slices, stacks, red, green, blue, alpha);
    }

    /**
     * 半径が時間とともに拡大または縮小するワイヤーフレーム球体を描画します。
     *
     * @param consumer      描画用バッファ * @param matrix 描画行列 (位置合わせ済みであること)
     * @param periodMillis  アニメーションが完了するまでの時間 (ミリ秒)
     * @param elapsedMillis アニメーション経過時間 (ミリ秒)
     * @param isExpanding   trueの場合、始点から終点へ向かい、falseの場合、終点から始点へ向かう線形アニメーションを行います。
     * @param slices        経度方向の分割数
     * @param stacks        緯度方向の分割数
     * @param red           赤
     * @param green         緑
     * @param blue          青
     * @param alpha         透明度
     * @param startRadius アニメーションの始点半径 * @param endRadius アニメーションの終点半径
     */
    public static void drawLinearAnimatingSphereWireframe( // メソッド名を変更
                                                           VertexConsumer consumer, Matrix4f matrix, Vec3 center,
                                                           float startRadius, float endRadius, long periodMillis, long elapsedMillis,
                                                           boolean isExpanding,
                                                           int slices, int stacks,
                                                           float red, float green, float blue, float alpha
    ) {
        float currentRadius = calculateLinearRadius(startRadius, endRadius, periodMillis, elapsedMillis, isExpanding); // 既存の描画メソッドを呼び出す
        Matrix4f translated = new Matrix4f(matrix);
        translated.translate((float) center.x, (float) center.y, (float) center.z);
        drawSphereWireframe(consumer, translated, currentRadius, slices, stacks, red, green, blue, alpha);
    }

    // =========================================================================
    //  内部ヘルパーメソッド
    // =========================================================================

    private static float calculateRotation(long elapsed, long period, float offsetDegrees) {
        if (period == 0) return (float) Math.toRadians(offsetDegrees);
        float baseAngleDegrees = (float) ((elapsed % period) / (float) period * 360.0);
        return (float) Math.toRadians((baseAngleDegrees + offsetDegrees) % 360.0f);
    }

    private static Matrix4f applyRotation(Matrix4f original, Vec3 center, float angleRadians) {
        Matrix4f mat = new Matrix4f(original);
        mat.translate((float) center.x, (float) center.y, (float) center.z);  // 中心へ
        mat.rotateAround(new Quaternionf().rotationY(angleRadians), 0, 0, 0); // 原点周りに回転
        return mat;
    }

    private static Vec3 getCirclePoint(double radius, double radian) {
        return new Vec3(radius * Math.cos(radian), 0, radius * Math.sin(radian));
    }

    // 太さ対応のループ描画 (A->B->C->...->A)
    private static void drawThickLineLoop(
            VertexConsumer consumer, Matrix4f matrix,
            int layers, double thicknessOffset,
            float r, float g, float b, float a,
            Vec3... vertices
    ) {
        int count = vertices.length;
        // layers が 1以下なら中央の1本だけ、2以上ならオフセットをつけて複数回描画
        int minPass = (layers <= 1) ? 0 : -(layers / 2);
        int maxPass = (layers <= 1) ? 0 : (layers / 2);

        for (int pass = minPass; pass <= maxPass; pass++) {
            double offset = pass * thicknessOffset;
            Vec3 offsetVec = new Vec3(offset, 0, offset);

            for (int i = 0; i < count; i++) {
                Vec3 start = vertices[i].add(offsetVec);
                Vec3 end = vertices[(i + 1) % count].add(offsetVec);
                drawLine(consumer, matrix, start, end, r, g, b, a);
            }
        }
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, float red, float green, float blue, float alpha) {
        // 色のクランプ
        int ri = (int) (Mth.clamp(red, 0f, 1f) * 255);
        int gi = (int) (Mth.clamp(green, 0f, 1f) * 255);
        int bi = (int) (Mth.clamp(blue, 0f, 1f) * 255);
        int ai = (int) (Mth.clamp(alpha, 0f, 1f) * 255);

        // 法線はYアップ固定
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color(ri, gi, bi, ai)
                .normal(0, 1, 0)
                .uv2(LightTexture.FULL_BRIGHT) // 常に明るく
                .endVertex();

        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color(ri, gi, bi, ai)
                .normal(0, 1, 0)
                .uv2(LightTexture.FULL_BRIGHT)
                .endVertex();
    }

    private static float calculateLinearRadius(float startRadius, float endRadius, long periodMillis, long elapsedMillis, boolean isExpanding) {
        if (periodMillis <= 0) {
            return isExpanding ? startRadius : endRadius; // 周期がない場合は初期値を返す
        }
        float rawProgress = (float) (elapsedMillis % periodMillis) / (float) periodMillis;
        float clampedProgress = Mth.clamp(rawProgress, 0.0f, 1.0f);
        float progress;
        if (isExpanding) {
            progress = clampedProgress;
        } else {
            progress = 1.0f - clampedProgress;
        }
        return startRadius + (endRadius - startRadius) * progress;
    }

    // =========================================================================
    //  高速球体レンダラー (内部クラス)
    // =========================================================================
    private static final class SphereRenderer {
        private static final int MAX_SLICES = 512;
        private static final int MAX_STACKS = 512;

        public static void draw(
                VertexConsumer consumer, Matrix4f pose,
                float radius, int slices, int stacks,
                float red, float green, float blue, float alpha
        ) {
            if (!Float.isFinite(radius) || radius <= 0f) return;
            slices = Mth.clamp(slices, 3, MAX_SLICES);
            stacks = Mth.clamp(stacks, 3, MAX_STACKS);
            int ri = (int) (Mth.clamp(red, 0f, 1f) * 255);
            int gi = (int) (Mth.clamp(green, 0f, 1f) * 255);
            int bi = (int) (Mth.clamp(blue, 0f, 1f) * 255);
            int ai = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
            int packedLight = LightTexture.FULL_BRIGHT;

            // 緯度線（横の輪）
            for (int i = 0; i <= slices; i++) {
                float phi = (float) Math.PI * i / slices - (float) Math.PI / 2.0f;
                float y = radius * Mth.sin(phi);
                float r = radius * Mth.cos(phi);
                for (int j = 0; j < stacks; j++) {
                    float theta0 = (float) (j * Math.PI * 2.0f / stacks);
                    float theta1 = (float) ((j + 1) * Math.PI * 2.0f / stacks);

                    vertex(consumer, pose, r * Mth.cos(theta0), y, r * Mth.sin(theta0), ri, gi, bi, ai, packedLight);
                    vertex(consumer, pose, r * Mth.cos(theta1), y, r * Mth.sin(theta1), ri, gi, bi, ai, packedLight);
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

                    float r0 = radius * Mth.cos(phi0); float y0 = radius * Mth.sin(phi0);
                    float r1 = radius * Mth.cos(phi1); float y1 = radius * Mth.sin(phi1);

                    vertex(consumer, pose, r0 * cosTheta, y0, r0 * sinTheta, ri, gi, bi, ai, packedLight);
                    vertex(consumer, pose, r1 * cosTheta, y1, r1 * sinTheta, ri, gi, bi, ai, packedLight);
                }
            }
        }

        private static void vertex(VertexConsumer c, Matrix4f m, float x, float y, float z, int r, int g, int b, int a, int light) {
                    c.vertex(m, x, y, z).color(r, g, b, a).normal(0, 1, 0).uv2(light).endVertex();
        }
    }
}