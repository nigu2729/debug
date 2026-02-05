package com.debug.client;

// 1. まず、イベントハンドラークラスを作成（例: RightClickHandler.java）
import com.debug.Debug;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ARV2H {
    private long TickCounter = 0;
    @Override
    public void tick(){
        super.tick();
        TickCounter++;
        if(TickCounter % 600 == 0){

        }
    }
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        var level = event.getLevel();
        InteractionHand hand = event.getHand();
        if (player.getItemInHand(hand).is(Items.STICK)) {
            if (level.isClientSide) {
                double ax = player.getX();
                double ay = player.getY();
                double az = player.getZ();
                Vec3 pos = new Vec3(ax, ay, az);
                new ARV2.Builder(new ResourceLocation(Debug.MOD_ID, "models/block/sphere.obj"), new ResourceLocation(Debug.MOD_ID, "block/whi"), pos).setSizeAnim(10,0,0,10).setMaxLife(10).setRenderType(RenderType.translucent()).build().spawn();
                new ARV2.Builder(new ResourceLocation(Debug.MOD_ID, "models/block/sphere.obj"), new ResourceLocation(Debug.MOD_ID, "block/sphere"), pos).setSizeAnim(0,100,10,100).setMaxLife(100).setRenderType(RenderType.translucent()).build().spawn();

            }
        }
    }
}