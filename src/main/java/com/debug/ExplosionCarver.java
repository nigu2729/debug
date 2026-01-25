package com.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class ExplosionCarver {

    /**
     * 指定した中心(center)から半径(radius)の球状領域を「空気」に置き換える。
     * @param level ServerLevel で呼ぶこと。クライアントで実行してはいけない。
     * @param center 中心座標（実数座標でも可）
     * @param radius 半径（ブロック単位）
     * @param dropBlocks true ならブロックドロップを発生させる（葉や作物などの処理を期待する場合）
     */
    public static void carveSphere(ServerLevel level, Vec3 center, int radius, boolean dropBlocks) {
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);

        int rSq = radius * radius;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        int minX = cx - radius;
        int maxX = cx + radius;
        int minY = Math.max(level.getMinBuildHeight(), cy - radius);
        int maxY = Math.min(level.getMaxBuildHeight(), cy + radius);
        int minZ = cz - radius;
        int maxZ = cz + radius;

        for (int x = minX; x <= maxX; x++) {
            int dx = x - cx;
            int dxSq = dx * dx;
            for (int y = minY; y <= maxY; y++) {
                int dy = y - cy;
                int dySq = dy * dy;
                for (int z = minZ; z <= maxZ; z++) {
                    int dz = z - cz;
                    int distSq = dxSq + dySq + dz * dz;
                    if (distSq <= rSq) {
                        mpos.set(x, y, z);
                        BlockState state = level.getBlockState(mpos);

                        if (state.isAir()) continue;

                        // 必要なら爆発のルールに従ってドロップ処理する
                        if (dropBlocks) {
                            Block.dropResources(state, level, mpos, (BlockEntity) null);
                        }

                        // TileEntity がある場合は破棄処理（必要に応じて）
                        if (state.hasBlockEntity()) {
                            BlockEntity be = level.getBlockEntity(mpos);
                            if (be != null) level.removeBlockEntity(mpos);
                        }

                        // 空気に置き換え（フラグ 3: 更新を通知して隣接ブロックを再計算）
                        level.setBlock(mpos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 視覚効果やサウンドを出したい場合はここで level.levelEvent / level.playSound などを呼ぶ
    }
}