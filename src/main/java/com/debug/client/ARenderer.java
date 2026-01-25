package com.debug.client;

import com.debug.Debug;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
// new ARenderer.Builder(new ResourceLocation(), Beforepos).build().spawn();
public class ARenderer {
    private static final List<ARenderer> RENDER_LIST = new CopyOnWriteArrayList<>();
    private final ResourceLocation modelLoc;
    private final RenderType renderType;
    private final int ML; // MaxLife (寿命)
    private int ticksExisted = 0;
    private final Vec3 BPo; // Before Position
    private final Vec3 APo; // After Position
    private final int MST;  // Move Start Tick
    private final int MET;  // Move End Tick
    private final float BPi, BYa, BRo; // Before Pitch, Yaw, Roll
    private final float APi, AYa, ARo; // After Pitch, Yaw, Roll
    private final int RST;  // Rotation Start Tick
    private final int RET;  // Rotation End Tick
    private final double BSi; // Before Size
    private final double ASi; // After Size
    private final int SST;    // Size Start Tick
    private final int SET;    // Size End Tick
    private ARenderer(ResourceLocation modelLoc, RenderType renderType, int ML, Vec3 BPo, Vec3 APo, int MST, int MET, float BPi, float BYa, float BRo, float APi, float AYa, float ARo, int RST, int RET, double BSi, double ASi, int SST, int SET) {
        this.modelLoc = modelLoc;
        this.renderType = renderType;
        this.ML = ML;
        this.BPo = BPo;
        this.APo = APo;
        this.MST = MST;
        this.MET = MET;
        this.BPi = BPi;
        this.BYa = BYa;
        this.BRo = BRo;
        this.APi = APi;
        this.AYa = AYa;
        this.ARo = ARo;
        this.RST = RST;
        this.RET = RET;
        this.BSi = BSi;
        this.ASi = ASi;
        this.SST = SST;
        this.SET = SET;
    }
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(new ResourceLocation(Debug.MOD_ID, "block/sphere"));
    }
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        RENDER_LIST.removeIf(renderer -> {
            renderer.ticksExisted++;
            return renderer.ticksExisted >= renderer.ML;
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
        int renderDistChunks = minecraft.options.renderDistance().get();
        double maxDist = renderDistChunks * 16.0;
        double maxDistSqr = maxDist * maxDist;
        for (ARenderer renderer : RENDER_LIST) {
            if (renderer.BPo.distanceToSqr(cameraPos) < maxDistSqr || renderer.APo.distanceToSqr(cameraPos) < maxDistSqr) {
                renderer.render(ps, buffer, cameraPos, partialTick);
            }
        }
        buffer.endBatch();
    }
    private void render(PoseStack ps, MultiBufferSource buffer, Vec3 cameraPos, float partialTick) {
        float currentTick = (float)this.ticksExisted + partialTick;
        Vec3 cPos = interpolatePos(BPo, APo, MST, MET, currentTick);
        float cPitch = interpolate(BPi, APi, RST, RET, currentTick);
        float cYaw   = interpolate(BYa, AYa, RST, RET, currentTick);
        float cRoll  = interpolate(BRo, ARo, RST, RET, currentTick);
        float cSize = interpolate((float)BSi, (float)ASi, SST, SET, currentTick);
        ps.pushPose();
        ps.translate(cPos.x - cameraPos.x, cPos.y - cameraPos.y, cPos.z - cameraPos.z);
        ps.mulPose(Axis.YP.rotationDegrees(cYaw));
        ps.mulPose(Axis.XP.rotationDegrees(cPitch));
        ps.mulPose(Axis.ZP.rotationDegrees(cRoll));
        ps.scale(cSize, cSize, cSize);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLoc);
        if (model == null) return;
        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                ps.last(), vertexConsumer, null, model, 1.0f, 1.0f, 1.0f, 15728880, OverlayTexture.NO_OVERLAY
        );
        ps.popPose();
    }
    private float interpolate(float start, float end, int startTick, int endTick, float currentTick) {
        if (currentTick <= startTick) return start;
        if (currentTick >= endTick) return end;
        if (startTick == endTick) return end;
        float progress = (currentTick - startTick) / (float)(endTick - startTick);
        return start + (end - start) * progress;
    }
    private Vec3 interpolatePos(Vec3 start, Vec3 end, int startTick, int endTick, float currentTick) {
        if (currentTick <= startTick) return start;
        if (currentTick >= endTick) return end;
        if (startTick == endTick) return end;
        float progress = (currentTick - startTick) / (float)(endTick - startTick);
        return start.add(end.subtract(start).scale(progress));
    }
    public void spawn() {
        RENDER_LIST.add(this);
    }
    // --- Builder Class ---
    public static class Builder {
        private final ResourceLocation modelLoc;
        private RenderType renderType = RenderType.solid();
        private int ML = 200;
        private Vec3 BPo;
        private Vec3 APo;
        private int MST = 0;
        private int MET = 0;
        private float BPi = 0;
        private float BYa = 0;
        private float BRo = 0;
        private float APi = 0;
        private float AYa = 0;
        private float ARo = 0;
        private int RST = 0;
        private int RET = 0;
        private double BSi = 1.0;
        private double ASi = 1.0;
        private int SST = 0;
        private int SET = 0;
        public Builder(ResourceLocation modelLoc, Vec3 startPos) {
            this.modelLoc = modelLoc;
            this.BPo = startPos;
            this.APo = startPos;
        }
        public Builder setRenderType(RenderType t) { this.renderType = t; return this; }
        public Builder setMaxLife(int ml) { this.ML = ml; return this; }
        public Builder setMove(Vec3 endPos, int startTick, int endTick) {
            this.APo = endPos;
            this.MST = startTick;
            this.MET = endTick;
            return this;
        }
        public Builder setRotAnim(float startP, float startY, float startR, float endP, float endY, float endR, int startTick, int endTick) {
            this.BPi = startP;
            this.BYa = startY;
            this.BRo = startR;
            this.APi = endP;
            this.AYa = endY;
            this.ARo = endR;
            this.RST = startTick;
            this.RET = endTick;
            return this;
        }
        public Builder setRotation(float p, float y, float r) {
            return setRotAnim(p, y, r, p, y, r, 0, 0);
        }
        public Builder setSizeAnim(double startSize, double endSize, int startTick, int endTick) {
            this.BSi = startSize;
            this.ASi = endSize;
            this.SST = startTick;
            this.SET = endTick;
            return this;
        }
        public Builder setSize(double size) {
            return setSizeAnim(size, size, 0, 0);
        }
        public ARenderer build() {
            return new ARenderer(modelLoc, renderType, ML, BPo, APo, MST, MET, BPi, BYa, BRo, APi, AYa, ARo, RST, RET, BSi, ASi, SST, SET);
        }
    }
}