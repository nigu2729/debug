package com.debug;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 既存のBakedModelにIQuadTransformerを適用するラッパークラス
 */
public class TBM extends BakedModelWrapper<BakedModel> {
    private final IQuadTransformer transformer;

    public TBM(BakedModel originalModel, IQuadTransformer transformer) {
        super(originalModel);
        this.transformer = transformer;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> originalQuads = super.originalModel.getQuads(state, side, rand, data, renderType);
        List<BakedQuad> transformedQuads = new ArrayList<>();
        for (BakedQuad quad : originalQuads) {
            transformedQuads.add(transformer.process(quad));
        }
        return transformedQuads;
    }
}