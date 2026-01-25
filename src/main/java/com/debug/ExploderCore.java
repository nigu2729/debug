package com.debug;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = Debug.MOD_ID)
public class ExploderCore {

    private static final String PROTOCOL = "1";
    private static final ResourceLocation CHANNEL_NAME = new ResourceLocation(Debug.MOD_ID, "exploder_channel");
    private static final SimpleChannel EXPLODER_CHANNEL = NetworkRegistry.ChannelBuilder
            .named(CHANNEL_NAME)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .networkProtocolVersion(() -> PROTOCOL)
            .simpleChannel();

    private static final AtomicInteger PACKET_ID = new AtomicInteger(0);

    private static <T> void registerPacket(Class<T> clazz,
                                           java.util.function.BiConsumer<T, FriendlyByteBuf> encoder,
                                           java.util.function.Function<FriendlyByteBuf, T> decoder,
                                           java.util.function.BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                                           NetworkDirection direction) {
        int id = PACKET_ID.getAndIncrement();
        EXPLODER_CHANNEL.registerMessage(id, clazz, encoder::accept, decoder::apply, (msg, ctxSupplier) -> {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> handler.accept(msg, ctxSupplier));
            ctx.setPacketHandled(true);
        }, Optional.of(direction));
    }

    static {
        registerPacket(ExploderCarvePacket.class, ExploderCarvePacket::encode, ExploderCarvePacket::decode, ExploderCarvePacket::handle, NetworkDirection.PLAY_TO_SERVER);
    }

    public static final KeyMapping KEY_TRIGGER_EXPLOSION = new KeyMapping(
            "key.exploder.trigger",
            GLFW.GLFW_KEY_G,
            "key.categories.exploder"
    );

    @Mod.EventBusSubscriber(modid = Debug.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ExploderKeyRegistration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY_TRIGGER_EXPLOSION);
        }
    }

    @Mod.EventBusSubscriber(modid = Debug.MOD_ID, value = Dist.CLIENT)
    public static class ExploderKeyHandler {
        private static final int DEFAULT_RADIUS = 64;
        private static final int DEFAULT_SHELLS_PER_TICK = 1;
        private static final float DEFAULT_BLOCKS_PER_TICK = 1099511627776.0f;
        private static final boolean DEFAULT_REMOVE_FLUIDS = true;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (KEY_TRIGGER_EXPLOSION.consumeClick()) {
                LocalPlayer player = mc.player;
                Vec3 pos = player.position();

                ExploderCarvePacket packet = new ExploderCarvePacket(
                        pos.x, pos.y, pos.z,
                        DEFAULT_RADIUS,
                        DEFAULT_SHELLS_PER_TICK,
                        DEFAULT_BLOCKS_PER_TICK,
                        DEFAULT_REMOVE_FLUIDS
                );
                EXPLODER_CHANNEL.sendToServer(packet);
            }
        }
    }

    public static class ExploderCarvePacket {
        public final double x, y, z;
        public final int radius;
        public final int shellsPerTick;
        public final float blocksPerTick;
        public final boolean removeFluids;

        public ExploderCarvePacket(double x, double y, double z, int radius, int shellsPerTick, float blocksPerTick, boolean removeFluids) {
            this.x = x; this.y = y; this.z = z;
            this.radius = radius;
            this.shellsPerTick = shellsPerTick;
            this.blocksPerTick = blocksPerTick;
            this.removeFluids = removeFluids;
        }

        public static void encode(ExploderCarvePacket msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeInt(msg.radius);
            buf.writeInt(msg.shellsPerTick);
            buf.writeFloat(msg.blocksPerTick);
            buf.writeBoolean(msg.removeFluids);
        }
        public static ExploderCarvePacket decode(FriendlyByteBuf buf) {
            return new ExploderCarvePacket(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readBoolean()
            );
        }

        public static void handle(ExploderCarvePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;

                // Forge 1.20.1: Entity.level is private; attempt common getters then fall back to reflection
                Level level = null;
                try {
                    Method m = player.getClass().getMethod("getLevel");
                    Object lv = m.invoke(player);
                    if (lv instanceof Level) level = (Level) lv;
                } catch (Throwable ignored) {}
                if (level == null) {
                    try {
                        Method m2 = player.getClass().getMethod("level");
                        Object lv2 = m2.invoke(player);
                        if (lv2 instanceof Level) level = (Level) lv2;
                    } catch (Throwable ignored) {}
                }
                if (level == null) {
                    try {
                        Field f = Entity.class.getDeclaredField("level");
                        f.setAccessible(true);
                        Object lv = f.get(player);
                        if (lv instanceof Level) level = (Level) lv;
                    } catch (Throwable ignored) {
                        // Try obf fallback
                        try {
                            Field f2 = Entity.class.getDeclaredField("field_70170_p");
                            f2.setAccessible(true);
                            Object lv2 = f2.get(player);
                            if (lv2 instanceof Level) level = (Level) lv2;
                        } catch (Throwable ignored2) {
                            return;
                        }
                    }
                }

                if (!(level instanceof ServerLevel)) return;
                ServerLevel serverLevel = (ServerLevel) level;

                Vec3 center = new Vec3(msg.x, msg.y, msg.z);

                ExploderCarveScheduler.scheduleCarveTask(serverLevel, center, msg.radius, msg.shellsPerTick, msg.blocksPerTick, msg.removeFluids);
            });
            ctx.setPacketHandled(true);
        }
    }
    // -----------------------
    // ExploderCarveScheduler
    // -----------------------
    @Mod.EventBusSubscriber(modid = Debug.MOD_ID)
    public static class ExploderCarveScheduler {

        private static final Queue<ExploderCarveTask> ACTIVE_CARVE_TASKS = new LinkedList<>();

        public static void scheduleCarveTask(ServerLevel level, Vec3 center, int maxRadius, int shellsPerTick, float blocksPerTickFloat, boolean removeFluids) {
            ExploderCarveTask task = new ExploderCarveTask(level, center, maxRadius, shellsPerTick, blocksPerTickFloat, removeFluids);
            synchronized (ACTIVE_CARVE_TASKS) {
                ACTIVE_CARVE_TASKS.add(task);
            }
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ACTIVE_CARVE_TASKS.isEmpty()) return;
            synchronized (ACTIVE_CARVE_TASKS) {
                Iterator<ExploderCarveTask> it = ACTIVE_CARVE_TASKS.iterator();
                while (it.hasNext()) {
                    ExploderCarveTask task = it.next();
                    try {
                        if (task.tickOnce()) it.remove();
                    } catch (Throwable t) {
                        it.remove();
                        t.printStackTrace();
                    }
                }
            }
        }
    }

    public static class ExploderCarveTask {
        private final ServerLevel level;
        private final Vec3 center;
        private final int maxRadius;
        private final int shellsPerTick;
        private final float blocksPerTickFloat;
        private double blockCarry = 0.0;
        private final boolean removeFluids;
        private int currentRadius = 0;
        private final Deque<BlockPos> pendingShell = new ArrayDeque<>();
        private final Random rand = new Random();

        public ExploderCarveTask(ServerLevel level, Vec3 center, int maxRadius, int shellsPerTick, float blocksPerTickFloat, boolean removeFluids) {
            this.level = level;
            this.center = center;
            this.maxRadius = Math.max(0, maxRadius);
            this.shellsPerTick = Math.max(1, shellsPerTick);
            this.blocksPerTickFloat = blocksPerTickFloat;
            this.removeFluids = removeFluids;
        }

        private void prepareNextShell() {
            while (currentRadius <= maxRadius && pendingShell.isEmpty()) {
                currentRadius++;
                if (currentRadius > maxRadius) break;
                collectShellPositions(currentRadius, pendingShell);
                if (!pendingShell.isEmpty()) {
                    List<BlockPos> tmp = new ArrayList<>(pendingShell);
                    Collections.shuffle(tmp, rand);
                    pendingShell.clear();
                    pendingShell.addAll(tmp);
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

        public boolean tickOnce() {
            for (int i = 0; i < shellsPerTick; i++) prepareNextShell();

            int removed = 0;
            int allowedThisTick = Math.min(Integer.MAX_VALUE, (int) Math.floor(blockCarry += blocksPerTickFloat));
            blockCarry -= allowedThisTick;

            while (removed < allowedThisTick && !pendingShell.isEmpty()) {
                BlockPos pos = pendingShell.pollFirst();
                if (pos == null) continue;
                if (!level.isAreaLoaded(pos, 0)) continue;

                // --- 置き換え開始 ---
                try {
                    BlockState state = level.getBlockState(pos);

                    if (removeFluids && !level.getFluidState(pos).isEmpty()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                        continue;
                    }

                    if (state.isAir()) continue;

                    // チェスト等のコンテナなら中身をばらまく
                    try {
                        if (level.getBlockEntity(pos) instanceof Container) {
                            Container cont = (Container) level.getBlockEntity(pos);
                            int size = cont.getContainerSize();
                            for (int slot = 0; slot < size; slot++) {
                                ItemStack stack = cont.getItem(slot);
                                if (stack == null || stack.isEmpty()) continue;

                                Item item = stack.getItem();
                                int remaining = stack.getCount();
                                int maxStack = stack.getMaxStackSize();
                                while (remaining > 0) {
                                    int take = Math.min(remaining, maxStack);
                                    ItemStack spawnStack = new ItemStack(item, take);

                                    double dx = pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.8;
                                    double dy = pos.getY() + 0.5 + rand.nextDouble() * 0.6;
                                    double dz = pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.8;

                                    ItemEntity ie = new ItemEntity(level, dx, dy, dz, spawnStack);
                                    ie.setDefaultPickUpDelay();
                                    if (level instanceof ServerLevel) {
                                        ((ServerLevel) level).addFreshEntity(ie);
                                    } else {
                                        level.addFreshEntity(ie);
                                    }
                                    remaining -= take;
                                }

                                // スロットをクリアして二重ドロップを防ぐ
                                cont.setItem(slot, ItemStack.EMPTY);
                            }

                            // ブロックエンティティを明示的に削除してからブロックを置き換える
                            level.removeBlockEntity(pos);
                        } else {
                            // 非コンテナ: ドロップを完全に抑止する（副作用も不要なら何もしない）
                            // もしXPなどの副作用だけ残したい場合は以下を使う（通常は不要）
                            // try { state.getBlock().dropResources(state, level, pos, null, null, ItemStack.EMPTY); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    // ブロック本体を削除（クライアントに更新を通知）
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    level.sendBlockUpdated(pos, state, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                } catch (Throwable t) {
                    t.printStackTrace();
                }
// --- 置き換え終了 ---
            }

            boolean finished = pendingShell.isEmpty() && currentRadius >= maxRadius;
            return finished;
        }
    }
}