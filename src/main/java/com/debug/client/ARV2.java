package com.debug.client; // あなたのパッケージに合わせてください

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = "debug", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ARV2 {
    // レンダリングリスト
    private static final List<ARV2> RENDER_LIST = new CopyOnWriteArrayList<>();

    // モデルデータ
    private final List<BakedQuad> cachedQuads;
    private final RenderType renderType;

    // 自動計算されたモデルのサイズ（当たり判定・ビーム連結用）
    private final float modelRadius;
    private final float modelLength;

    // 寿命管理
    private final int maxLife;
    private int ticksExisted = 0;

    // 座標アニメーション
    private final Vec3 startPos, endPos;
    private final int moveStartTick, moveEndTick;

    // 回転アニメーション
    private final float startPitch, startYaw, startRoll;
    private final float endPitch, endYaw, endRoll;
    private final int rotStartTick, rotEndTick;

    // サイズアニメーション
    private final double startScale, endScale;
    private final int scaleStartTick, scaleEndTick;

    // 色・アルファアニメーション（新機能）
    private final ColorAnim colorAnim;
    private final AlphaAnim alphaAnim;

    // ビーム制御（新機能）
    private final boolean isBeamMode;
    private final float beamSpeed;      // 1tickあたりの伸びる速度(ブロック)
    private final float maxBeamLength;  // 最大射程
    private final float beamInterval;   // モデルを繰り返す間隔(0なら自動)

    // コンストラクタ
    private ARV2(List<BakedQuad> cachedQuads, RenderType renderType, float modelRadius, float modelLength,
                 int maxLife, Vec3 startPos, Vec3 endPos, int moveStartTick, int moveEndTick,
                 float sP, float sY, float sR, float eP, float eY, float eR, int rST, int rET,
                 double sS, double eS, int sST, int sET,
                 ColorAnim colorAnim, AlphaAnim alphaAnim,
                 boolean isBeamMode, float beamSpeed, float maxBeamLength, float beamInterval) {
        this.cachedQuads = cachedQuads;
        this.renderType = renderType;
        this.modelRadius = modelRadius;
        this.modelLength = modelLength;
        this.maxLife = maxLife;
        this.startPos = startPos;
        this.endPos = endPos;
        this.moveStartTick = moveStartTick;
        this.moveEndTick = moveEndTick;
        this.startPitch = sP; this.startYaw = sY; this.startRoll = sR;
        this.endPitch = eP; this.endYaw = eY; this.endRoll = eR;
        this.rotStartTick = rST; this.rotEndTick = rET;
        this.startScale = sS; this.endScale = eS;
        this.scaleStartTick = sST; this.scaleEndTick = sET;
        this.colorAnim = colorAnim;
        this.alphaAnim = alphaAnim;
        this.isBeamMode = isBeamMode;
        this.beamSpeed = beamSpeed;
        this.maxBeamLength = maxBeamLength;
        this.beamInterval = beamInterval > 0 ? beamInterval : Math.max(1.0f, modelLength); // 0ならモデルの長さを使用
    }

    // --- イベントハンドラ ---

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        RENDER_LIST.removeIf(renderer -> {
            renderer.ticksExisted++;
            return renderer.ticksExisted >= renderer.maxLife;
        });
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick();

        for (ARV2 renderer : RENDER_LIST) {
            renderer.render(ps, buffer, cameraPos, partialTick);
        }
    }

    // --- レンダリング処理 ---

    private void render(PoseStack ps, MultiBufferSource buffer, Vec3 cameraPos, float partialTick) {
        float currentTick = (float) this.ticksExisted + partialTick;

        // 座標・回転・サイズの補間計算
        Vec3 cPos = interpolatePos(startPos, endPos, moveStartTick, moveEndTick, currentTick);
        float cPitch = interpolate(startPitch, endPitch, rotStartTick, rotEndTick, currentTick);
        float cYaw = interpolate(startYaw, endYaw, rotStartTick, rotEndTick, currentTick);
        float cRoll = interpolate(startRoll, endRoll, rotStartTick, rotEndTick, currentTick);
        float cScale = (float) interpolate((float) startScale, (float) endScale, scaleStartTick, scaleEndTick, currentTick);
        // 色とアルファの計算
        float[] rgb = colorAnim.getRGB(currentTick);
        float alpha = alphaAnim.getAlpha(currentTick);
        // 完全に透明なら描画しない
        if (alpha <= 0.001f) return;
        ps.pushPose();
        // カメラ相対座標へ移動
        ps.translate(cPos.x - cameraPos.x, cPos.y - cameraPos.y, cPos.z - cameraPos.z);
        // 回転適用 (Y -> X -> Z の順序)
        ps.mulPose(Axis.YP.rotationDegrees(cYaw));
        ps.mulPose(Axis.XP.rotationDegrees(cPitch));
        ps.mulPose(Axis.ZP.rotationDegrees(cRoll));
        // サイズ適用
        ps.scale(cScale, cScale, cScale);
        // レンダリング設定
        // 半透明対応のため、translucentなど適切なRenderTypeをBuilderで指定すること
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        VertexConsumer vertexConsumer = buffer.getBuffer(this.renderType);
        if (isBeamMode) {
            renderBeamLoop(ps, vertexConsumer, currentTick, rgb, alpha);
        } else {
            renderSingleModel(ps, vertexConsumer, rgb, alpha);
        }
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        ps.popPose();
    }

    private void renderSingleModel(PoseStack ps, VertexConsumer consumer, float[] rgb, float alpha) {
        if (this.cachedQuads == null) return;
        PoseStack.Pose entry = ps.last();

        // 定義に合わせて4頂点分の配列を用意
        float[] afloat = new float[]{1.0F, 1.0F, 1.0F, 1.0F}; // p_85998_
        int[] aint = new int[]{15728880, 15728880, 15728880, 15728880}; // p_86002_ (ライトマップ)

        for (BakedQuad quad : this.cachedQuads) {
            consumer.putBulkData(
                    entry,            // p_85996_
                    quad,             // p_85997_
                    afloat,           // p_85998_ (頂点ごとのカラー倍率)
                    rgb[0],           // p_85999_ (R)
                    rgb[1],           // p_86000_ (G)
                    rgb[2],           // p_86001_ (B)
                    alpha,            // alpha
                    aint,             // p_86002_ (ライトマップの配列)
                    OverlayTexture.NO_OVERLAY, // p_86003_
                    true              // p_86004_ (既存の頂点カラーを読み込む)
            );
        }
    }

    // ビーム（繰り返し）描画ロジック
    private void renderBeamLoop(PoseStack ps, VertexConsumer consumer, float currentTick, float[] rgb, float alpha) {
        if (this.cachedQuads == null) return;

        // 現在の長さを計算 (速度 * 時間)
        float currentLen = Math.min(maxBeamLength, beamSpeed * currentTick);

        // 必要な繰り返し回数
        int count = (int) Math.ceil(currentLen / beamInterval);

        for (int i = 0; i < count; i++) {
            ps.pushPose();
            // Z軸方向（奥）へずらしていく
            ps.translate(0, 0, i * beamInterval);

            // 最後のピースだけ、長さを調整する場合のロジックを入れるならここ
            // 今回は単純ループ
            renderSingleModel(ps, consumer, rgb, alpha);
            ps.popPose();
        }
    }

    // --- 補間メソッド ---

    private float interpolate(float start, float end, int startTick, int endTick, float currentTick) {
        if (currentTick <= startTick) return start;
        if (currentTick >= endTick) return end;
        if (startTick == endTick) return end;
        float progress = (currentTick - startTick) / (float) (endTick - startTick);
        return start + (end - start) * progress;
    }

    private Vec3 interpolatePos(Vec3 start, Vec3 end, int startTick, int endTick, float currentTick) {
        if (currentTick <= startTick) return start;
        if (currentTick >= endTick) return end;
        if (startTick == endTick) return end;
        float progress = (currentTick - startTick) / (float) (endTick - startTick);
        return start.add(end.subtract(start).scale(progress));
    }

    // --- 当たり判定用ロジック（点と線分の距離） ---

    // 敵エンティティのリストを渡して、当たっているかをチェックする
    public boolean checkCollision(Entity target) {
        // 現在のビーム状態を取得
        float currentTick = (float)this.ticksExisted; // 部分ティックなしで簡易判定
        float currentLen = isBeamMode ? Math.min(maxBeamLength, beamSpeed * currentTick) : modelLength;
        float currentScale = (float)interpolate((float)startScale, (float)endScale, scaleStartTick, scaleEndTick, currentTick);
        float actualRadius = modelRadius * currentScale;

        // ビームの始点と終点（ワールド座標）を計算
        // 注意: 厳密な回転行列計算が必要だが、簡易的にベクトルで計算
        Vec3 start = interpolatePos(startPos, endPos, moveStartTick, moveEndTick, currentTick);

        // 方向ベクトルを回転角から算出 (Yaw, Pitch)
        float radYaw = (float)Math.toRadians(-interpolate(startYaw, endYaw, rotStartTick, rotEndTick, currentTick));
        float radPitch = (float)Math.toRadians(interpolate(startPitch, endPitch, rotStartTick, rotEndTick, currentTick));

        double dirX = Math.sin(radYaw) * Math.cos(radPitch);
        double dirY = -Math.sin(radPitch);
        double dirZ = Math.cos(radYaw) * Math.cos(radPitch);
        Vec3 direction = new Vec3(dirX, dirY, dirZ).normalize();

        Vec3 end = start.add(direction.scale(currentLen));

        // 点と線分の距離チェック
        return distancePointToSegment(target.position().add(0, target.getBbHeight()/2, 0), start, end) < (actualRadius + target.getBbWidth());
    }

    private double distancePointToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        Vec3 ap = point.subtract(a);
        double t = ap.dot(ab) / ab.dot(ab);
        if (t < 0) return point.distanceTo(a);
        if (t > 1) return point.distanceTo(b);
        Vec3 closest = a.add(ab.scale(t));
        return point.distanceTo(closest);
    }

    public void spawn() {
        RENDER_LIST.add(this);
    }

    // --- OBJローダー (自動サイズ計算付き) ---

    public static ObjLoadResult loadCustomObj(ResourceLocation modelLoc, TextureAtlasSprite sprite) {
        List<BakedQuad> quads = new ArrayList<>();
        List<Vector3f> vertices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();

        // サイズ自動計算用
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        float maxRadiusSq = 0;

        System.out.println("Starting to load OBJ: " + modelLoc);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Minecraft.getInstance().getResourceManager().getResource(modelLoc).get().open()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        vertices.add(new Vector3f(x, y, z));

                        // バウンディングボックス計算
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                        // 半径計算（XY平面）
                        maxRadiusSq = Math.max(maxRadiusSq, x*x + y*y);
                    }
                    case "vt" -> uvs.add(new float[]{Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                    case "vn" -> normals.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])));
                    case "f" -> quads.add(createQuad(parts, vertices, uvs, normals, sprite));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        float radius = (float)Math.sqrt(maxRadiusSq);
        float length = maxZ - minZ; // Z軸に伸びていると仮定
        return new ObjLoadResult(quads, radius, Math.max(1.0f, length)); // 長さが0にならないよう保護
    }

    private static BakedQuad createQuad(String[] parts, List<Vector3f> v, List<float[]> vt, List<Vector3f> vn, TextureAtlasSprite sprite) {
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            int partIndex = Math.min(i + 1, parts.length - 1);
            String[] indices = parts[partIndex].split("/");
            Vector3f pos = v.get(Integer.parseInt(indices[0]) - 1);
            float[] uv = (indices.length > 1 && !indices[1].isEmpty()) ? vt.get(Integer.parseInt(indices[1]) - 1) : new float[]{0, 0};
            Vector3f normal = (indices.length > 2 && !indices[2].isEmpty()) ? vn.get(Integer.parseInt(indices[2]) - 1) : new Vector3f(0, 1, 0);

            int offset = i * 8;
            data[offset] = Float.floatToRawIntBits(pos.x());
            data[offset + 1] = Float.floatToRawIntBits(pos.y());
            data[offset + 2] = Float.floatToRawIntBits(pos.z());
            data[offset + 3] = -1; // Color (Packed but overwritten by bulk data)
            data[offset + 4] = Float.floatToRawIntBits(sprite.getU(uv[0] * 16));
            data[offset + 5] = Float.floatToRawIntBits(sprite.getV((1 - uv[1]) * 16));
            data[offset + 6] = 0;
            data[offset + 7] = packNormal(normal.x(), normal.y(), normal.z());
        }
        return new BakedQuad(data, -1, Direction.UP, sprite, true);
    }

    private static int packNormal(float x, float y, float z) {
        return ((int) (x * 127.0F) & 0xFF) | (((int) (y * 127.0F) & 0xFF) << 8) | (((int) (z * 127.0F) & 0xFF) << 16);
    }

    // OBJ読み込み結果保持用レコード
    private record ObjLoadResult(List<BakedQuad> quads, float radius, float length) {}

    // --- 内部クラス: アニメーション管理 ---

    // 色制御 (Start RGB -> End RGB)
    private static class ColorAnim {
        float r1, g1, b1, r2, g2, b2;
        int startTick, endTick;

        ColorAnim(float r1, float g1, float b1, float r2, float g2, float b2, int start, int end) {
            this.r1 = r1; this.g1 = g1; this.b1 = b1;
            this.r2 = r2; this.g2 = g2; this.b2 = b2;
            this.startTick = start; this.endTick = end;
        }

        float[] getRGB(float currentTick) {
            if (currentTick <= startTick) return new float[]{r1, g1, b1};
            if (currentTick >= endTick) return new float[]{r2, g2, b2};
            float t = (currentTick - startTick) / (endTick - startTick);
            return new float[]{
                    r1 + (r2 - r1) * t,
                    g1 + (g2 - g1) * t,
                    b1 + (b2 - b1) * t
            };
        }
    }

    // アルファ制御 (Start -> Mid -> End) 3点制御
    private static class AlphaAnim {
        float a1, a2, a3;
        int t1, t2, t3; // Start, Mid, End ticks

        AlphaAnim(float a1, float a2, float a3, int t1, int t2, int t3) {
            this.a1 = a1; this.a2 = a2; this.a3 = a3;
            this.t1 = t1; this.t2 = t2; this.t3 = t3;
        }

        float getAlpha(float currentTick) {
            if (currentTick <= t1) return a1;
            if (currentTick >= t3) return a3;

            if (currentTick < t2) {
                // 前半: a1 -> a2
                float t = (currentTick - t1) / (t2 - t1);
                return a1 + (a2 - a1) * t;
            } else {
                // 後半: a2 -> a3
                float t = (currentTick - t2) / (t3 - t2);
                return a2 + (a3 - a2) * t;
            }
        }
    }

    // --- Builder Class ---
    public static class Builder {
        private final ResourceLocation modelLoc;
        private final ResourceLocation textureLoc;
        private RenderType renderType = RenderType.translucent(); // デフォルトを半透明に
        private int maxLife = 200;
        private Vec3 startPos, endPos;
        private int moveStart = 0, moveEnd = 0;
        private float sP=0, sY=0, sR=0, eP=0, eY=0, eR=0;
        private int rotStart=0, rotEnd=0;
        private double sS=1.0, eS=1.0;
        private int sizeStart=0, sizeEnd=0;

        // 色設定デフォルト (白, 不透明)
        private float r1=1, g1=1, b1=1, r2=1, g2=1, b2=1;
        private int colorStart=0, colorEnd=0;
        private float a1=1, a2=1, a3=1;
        private int alphaStart=0, alphaMid=0, alphaEnd=0;

        // ビーム設定
        private boolean isBeam = false;
        private float beamSpeed = 32.0f;
        private float maxBeamLen = 1024.0f;
        private float beamInterval = 0.0f; // 0なら自動

        public Builder(ResourceLocation modelLoc, ResourceLocation textureLoc, Vec3 startPos) {
            this.modelLoc = modelLoc;
            this.textureLoc = textureLoc;
            this.startPos = startPos;
            this.endPos = startPos;
        }

        public Builder setRenderType(RenderType t) { this.renderType = t; return this; }
        public Builder setMaxLife(int ml) { this.maxLife = ml; return this; }

        public Builder setMove(Vec3 endPos, int startTick, int endTick) {
            this.endPos = endPos; this.moveStart = startTick; this.moveEnd = endTick; return this;
        }

        public Builder setRotAnim(float sP, float sY, float sR, float eP, float eY, float eR, int start, int end) {
            this.sP=sP; this.sY=sY; this.sR=sR; this.eP=eP; this.eY=eY; this.eR=eR;
            this.rotStart=start; this.rotEnd=end; return this;
        }
        public Builder setRotation(float p, float y, float r) { return setRotAnim(p, y, r, p, y, r, 0, 0); }

        public Builder setSizeAnim(double s, double e, int start, int end) {
            this.sS=s; this.eS=e; this.sizeStart=start; this.sizeEnd=end; return this;
        }
        public Builder setSize(double s) { return setSizeAnim(s, s, 0, 0); }

        // --- 新機能セットアップ ---

        // RGBアニメーション
        public Builder setRGBAnim(float r1, float g1, float b1, float r2, float g2, float b2, int start, int end) {
            this.r1=r1; this.g1=g1; this.b1=b1; this.r2=r2; this.g2=g2; this.b2=b2;
            this.colorStart=start; this.colorEnd=end; return this;
        }

        // RGB固定
        public Builder setRGB(float r, float g, float b) {
            return setRGBAnim(r, g, b, r, g, b, 0, 0);
        }

        // アルファアニメーション (Start -> Mid -> End)
        public Builder setAlphaAnim(float start, float mid, float end, int tStart, int tMid, int tEnd) {
            this.a1=start; this.a2=mid; this.a3=end;
            this.alphaStart=tStart; this.alphaMid=tMid; this.alphaEnd=tEnd; return this;
        }

        // アルファ固定
        public Builder setAlpha(float a) {
            return setAlphaAnim(a, a, a, 0, 0, 0);
        }

        // ビームモード有効化
        public Builder asBeam(float speed, float maxLen, float interval) {
            this.isBeam = true;
            this.beamSpeed = speed;
            this.maxBeamLen = maxLen;
            this.beamInterval = interval;
            return this;
        }

        public ARV2 build() {
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(textureLoc);

            // OBJ読み込みとサイズ自動取得
            ObjLoadResult objData = loadCustomObj(modelLoc, sprite);

            ColorAnim cAnim = new ColorAnim(r1, g1, b1, r2, g2, b2, colorStart, colorEnd);
            AlphaAnim aAnim = new AlphaAnim(a1, a2, a3, alphaStart, alphaMid, alphaEnd);

            return new ARV2(objData.quads, renderType, objData.radius, objData.length,
                    maxLife, startPos, endPos, moveStart, moveEnd,
                    sP, sY, sR, eP, eY, eR, rotStart, rotEnd,
                    sS, eS, sizeStart, sizeEnd,
                    cAnim, aAnim,
                    isBeam, beamSpeed, maxBeamLen, beamInterval);
        }
    }
}