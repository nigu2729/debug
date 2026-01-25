package com.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID)
public class ProgressiveCarver {

    public static class CarveJob {
        private final ServerLevel level;
        private final Vec3 center;
        private final int maxRadius;
        private final int shellsPerTick;

        // float 指定の上限（<= 0 で無効、正の値を指定）
        private final float blocksPerTickFloat;
        // int 互換モード（blocksPerTickFloat が無効な場合に使用）
        private final int blocksPerTickInt;

        // float の端数繰越用（累積）
        private double blockCarry = 0.0;

        private final boolean removeFluids;
        private int currentRadius = 0;
        private final Deque<BlockPos> pendingShell = new ArrayDeque<>();
        private final Random rand = new Random();

        /**
         * float モード（推奨）
         */
        public CarveJob(ServerLevel level, Vec3 center, int maxRadius, int shellsPerTick, float blocksPerTickFloat, boolean removeFluids) {
            this.level = level;
            this.center = center;
            this.maxRadius = Math.max(0, maxRadius);
            this.shellsPerTick = Math.max(1, shellsPerTick);
            this.blocksPerTickFloat = blocksPerTickFloat;
            this.blocksPerTickInt = -1; // 無効
            this.removeFluids = removeFluids;
        }

        /**
         * int 互換モード（従来互換）
         */
        public CarveJob(ServerLevel level, Vec3 center, int maxRadius, int shellsPerTick, int blocksPerTickInt, boolean removeFluids) {
            this.level = level;
            this.center = center;
            this.maxRadius = Math.max(0, maxRadius);
            this.shellsPerTick = Math.max(1, shellsPerTick);
            this.blocksPerTickFloat = -1.0f; // 無効
            this.blocksPerTickInt = Math.max(1, blocksPerTickInt);
            this.removeFluids = removeFluids;
        }

        private void prepareNextShell() {
            while (currentRadius <= maxRadius && pendingShell.isEmpty()) {
                currentRadius++;
                if (currentRadius > maxRadius) break;
                collectShellPositions(currentRadius, pendingShell);

                if (!pendingShell.isEmpty()) {
                    List<BlockPos> tmp = new java.util.ArrayList<>(pendingShell);
                    java.util.Collections.shuffle(tmp, rand);
                    pendingShell.clear();
                    for (BlockPos p : tmp) pendingShell.addLast(p);
                }
            }
        }

        private void collectShellPositions(int r, Deque<BlockPos> out) {
            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);

            int minX = cx - r;
            int maxX = cx + r;
            int minY = Math.max(level.getMinBuildHeight(), cy - r);
            int maxY = Math.min(level.getMaxBuildHeight(), cy + r);
            int minZ = cz - r;
            int maxZ = cz + r;

            double rSqLo = (r - 0.5) * (r - 0.5);
            double rSqHi = (r + 0.5) * (r + 0.5);

            for (int x = minX; x <= maxX; x++) {
                int dx = x - cx;
                int dxSq = dx * dx;
                for (int y = minY; y <= maxY; y++) {
                    int dy = y - cy;
                    int dySq = dy * dy;
                    for (int z = minZ; z <= maxZ; z++) {
                        int dz = z - cz;
                        int distSq = dxSq + dySq + dz * dz;
                        if (distSq >= rSqLo && distSq <= rSqHi) {
                            out.addLast(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }

        /**
         * 1 tick 分の処理を実行する。
         * @return 完了したら true を返す
         */
        public boolean tickOnce() {
            for (int i = 0; i < shellsPerTick; i++) prepareNextShell();

            int removed = 0;

            // モード判定: float モードが有効なら優先、そうでなければ int モード
            final boolean useFloatMode = blocksPerTickFloat > 0.0f;
            final boolean useIntMode = !useFloatMode && blocksPerTickInt > 0;

            int allowedThisTick;
            if (useFloatMode) {
                // float を累積して整数分だけ処理。Float.MAX_VALUE に近い値でも動作するが危険
                blockCarry += (double) blocksPerTickFloat;
                // floor を使う。上限は Integer.MAX_VALUE に収める
                long allowedLong = (long) Math.floor(blockCarry);
                if (allowedLong > 0) {
                    blockCarry -= allowedLong;
                }
                if (allowedLong >= Integer.MAX_VALUE) {
                    allowedThisTick = Integer.MAX_VALUE;
                } else {
                    allowedThisTick = (int) Math.max(0L, allowedLong);
                }
            } else if (useIntMode) {
                allowedThisTick = blocksPerTickInt;
            } else {
                // 両方無効なら無制限扱い（ここでも Integer.MAX_VALUE に制限している）
                allowedThisTick = Integer.MAX_VALUE;
            }

            while (removed < allowedThisTick && !pendingShell.isEmpty()) {
                BlockPos pos = pendingShell.pollFirst();
                if (pos == null) break;

                // チャンクがロードされていないならスキップ
                if (!level.isLoaded(pos)) continue;

                // 流体処理: removeFluids が true の場合は FluidState をチェックして消す
                if (removeFluids) {
                    FluidState fs = level.getFluidState(pos);
                    if (!fs.isEmpty()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                        continue;
                    }
                }

                // ブロックが既に空気ならスキップ
                if (level.getBlockState(pos).isAir()) continue;

                // 通常のブロック削除
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }

            if (!pendingShell.isEmpty()) return false;
            return currentRadius >= maxRadius;
        }
    }

    private static final Queue<CarveJob> JOBS = new LinkedList<>();

    /**
     * float 指定で startCarve する API（例: 0.5f, 10.75f, Float.MAX_VALUE）
     */
    public static void startCarve(ServerLevel level, Vec3 center, int maxRadius, int shellsPerTick, float blocksPerTickFloat, boolean removeFluids) {
        CarveJob job = new CarveJob(level, center, maxRadius, shellsPerTick, blocksPerTickFloat, removeFluids);
        synchronized (JOBS) { JOBS.add(job); }
        System.out.println("[ProgressiveCarver] startCarve(float) center=" + center + " maxRadius=" + maxRadius
                + " shellsPerTick=" + shellsPerTick + " blocksPerTickFloat=" + blocksPerTickFloat + " removeFluids=" + removeFluids);
    }

    /**
     * 既存互換: int 指定で startCarve する API
     */
    public static void startCarve(ServerLevel level, Vec3 center, int maxRadius, int shellsPerTick, int blocksPerTick, boolean removeFluids) {
        CarveJob job = new CarveJob(level, center, maxRadius, shellsPerTick, blocksPerTick, removeFluids);
        synchronized (JOBS) { JOBS.add(job); }
        System.out.println("[ProgressiveCarver] startCarve(int) center=" + center + " maxRadius=" + maxRadius
                + " shellsPerTick=" + shellsPerTick + " blocksPerTick=" + blocksPerTick + " removeFluids=" + removeFluids);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (JOBS.isEmpty()) return;

        synchronized (JOBS) {
            Iterator<CarveJob> it = JOBS.iterator();
            while (it.hasNext()) {
                CarveJob job = it.next();
                try {
                    boolean done = job.tickOnce();
                    if (done) {
                        it.remove();
                        // 完了時のパーティクルや音をここに追加可能
                    }
                } catch (Throwable t) {
                    it.remove();
                    t.printStackTrace();
                }
            }
        }
    }
}