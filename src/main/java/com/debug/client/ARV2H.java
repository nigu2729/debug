package com.debug.client;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ARV2H {
    private static long TC = 0;
    private static final List<DelayedTask> PENDING_TASKS = new ArrayList<>();
    private record DelayedTask(long executeAt, Vec3 pos) {}
    @SubscribeEvent
    public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            TC++;
            Iterator<DelayedTask> iterator = PENDING_TASKS.iterator();
            while (iterator.hasNext()) {
                DelayedTask task = iterator.next();
                if (TC >= task.executeAt) {
                    spawnEffects(task.pos);
                    iterator.remove();
                }
            }
        }
    }
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) {
            Player player = event.getEntity();
            if (player.getItemInHand(event.getHand()).is(Items.STICK)) {
                PENDING_TASKS.add(new DelayedTask(TC + 200, player.position()));
            }
        }
    }
    private static void spawnEffects(Vec3 pos) {
        new ARV2.Builder(new ResourceLocation(Debug.MOD_ID, "models/block/sphere.obj"), new ResourceLocation(Debug.MOD_ID, "block/whi"), pos).setSizeAnim(10, 0, 0, 5).setMaxLife(5).setRenderType(RenderType.translucent()).build().spawn();
        new ARV2.Builder(new ResourceLocation(Debug.MOD_ID, "models/block/sphere.obj"), new ResourceLocation(Debug.MOD_ID, "block/bla"), pos).setSizeAnim(0, 100, 5, 100).setMaxLife(100).setRenderType(RenderType.translucent()).build().spawn();
    }
}