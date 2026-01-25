package com.debug;

import com.debug.client.ARenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.debug.client.ARenderer.*;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TNTExplosionHandler {

    // 固定で使いたい半径（ブロック単位）
    private static final int FIXED_RADIUS = 256;

    // startCarve のデフォルトパラメータ
    private static final int DEFAULT_SHELLS_PER_TICK = 1;
    // float 版の上限（例: 1024.0f）。試験的に最大にしたい場合は Float.MAX_VALUE を代入可能。
    private static final float DEFAULT_BLOCKS_PER_TICK_F = 281474976710656f;
    // ここを true にすると水/溶岩などの流体も削除する
    private static final boolean DEFAULT_REMOVE_FLUIDS = true;

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        try {
            Explosion explosion = event.getExplosion();
            if (explosion == null) return;

            Entity exploder = explosion.getExploder();
            if (exploder == null || exploder.getType() != EntityType.TNT) return;

            if (!(event.getLevel() instanceof ServerLevel)) return;
            ServerLevel serverLevel = (ServerLevel) event.getLevel();

            Vec3 pos = explosion.getPosition();

            // 推定は使わず、固定半径で段階削除をスケジュール（float 版を渡す）
            scheduleProgressiveCarve(serverLevel, pos, FIXED_RADIUS, DEFAULT_SHELLS_PER_TICK, DEFAULT_BLOCKS_PER_TICK_F, DEFAULT_REMOVE_FLUIDS);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void scheduleProgressiveCarve(ServerLevel serverLevel, Vec3 center, int maxRadius, int shellsPerTick, float blocksPerTickFloat, boolean removeFluids) {
        System.out.println("[TNTExplosionHandler] scheduleProgressiveCarve center=" + center + " maxRadius=" + maxRadius
                + " shellsPerTick=" + shellsPerTick + " blocksPerTickFloat=" + blocksPerTickFloat + " removeFluids=" + removeFluids);

        MinecraftServer server = serverLevel.getServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    // float 版 startCarve を呼ぶ
                    ProgressiveCarver.startCarve(serverLevel, center, maxRadius, shellsPerTick, blocksPerTickFloat, removeFluids);
                    new ARenderer.Builder(new ResourceLocation(Debug.MOD_ID, "block/sphere"),center).setSize(10).setRenderType(RenderType.cutout()).build().spawn();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        } else {
            ProgressiveCarver.startCarve(serverLevel, center, maxRadius, shellsPerTick, blocksPerTickFloat, removeFluids);
        }
    }
}