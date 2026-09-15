package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class OmniBatteryBlockEntity extends BlockEntity implements MenuProvider {
    private final BatteryTier tier;
    private long energy = 0L;
    private BatteryMode mode = BatteryMode.BOTH;
    private int rateIndex = 0;
    private int range = 16;
    private BatteryAccess access = BatteryAccess.PRIVATE;
    /** 是否给玩家物品栏（含快捷栏/护甲/副手）内的物品供电（默认关闭）。 */
    private boolean chargeInventory = false;
    /** 是否给玩家饰品栏（Curios）内的物品供电（默认关闭）。 */
    private boolean chargeCurios = false;
    private UUID ownerUuid = null;
    private String ownerName = "";
    private final Map<UUID, String> trustedPlayers = new LinkedHashMap<>();

    private final IEnergyStorage energyStorage;
    private final LazyOptional<IEnergyStorage> energyCap;

    private static final int SCAN_INTERVAL = 1;
    private static final int MAX_BLOCKS_PER_SCAN = 512;
    private static final int MAX_PLAYER_ITEMS_PER_SCAN = 128;
    private static final int ULTIMATE_EXTRA_CHUNK_RADIUS = 2;
    private static final int ULTIMATE_BATTERY_FALLBACK_CHUNK_RADIUS = 32;
    private static final int MAX_TRANSFER_LOOPS_PER_SIDE = 32768;
    /** 单个目标每 tick 的最大重复传输次数（无限档突破单次 int 上限 21 亿）。 */
    private static final int MAX_LOOPS_PER_TARGET = 256;
    private int tickCount = 0;

    // ---- 实时速率统计（每 20 tick = 1 秒冻结一次，供 GUI 显示）----
    private long absorbedThisSecond;
    private long suppliedThisSecond;
    private long lastAbsorbed;
    private long lastSupplied;
    private int secondTick;

    // ---- 趋势图历史（每秒采样，环形缓冲 60 点 = 最近 60 秒）----
    public static final int HISTORY_SIZE = 60;
    private final long[] absorbHistory = new long[HISTORY_SIZE];
    private final long[] supplyHistory = new long[HISTORY_SIZE];
    private int historyIndex;

    public OmniBatteryBlockEntity(BlockPos pos, BlockState state, BatteryTier tier) {
        super(ModBlockEntities.OMNI_BATTERY.get(), pos, state);
        this.tier = tier;
        this.range = tier.defaultRange();
        this.energyStorage = new IEnergyStorage() {
            @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                if (maxReceive <= 0) return 0;
                long space = tier.capacity() - energy;
                int received = BatteryData.clampToForgeInt(Math.min(space, (long) maxReceive));
                if (!simulate && received > 0) { energy += received; setChanged(); }
                return received;
            }
            @Override public int extractEnergy(int maxExtract, boolean simulate) {
                if (maxExtract <= 0) return 0;
                int extracted = BatteryData.clampToForgeInt(Math.min(energy, (long) maxExtract));
                if (!simulate && extracted > 0) { energy -= extracted; setChanged(); }
                return extracted;
            }
            @Override public int getEnergyStored() { return BatteryData.clampToForgeInt(energy); }
            @Override public int getMaxEnergyStored() { return tier.forgeCapacity(); }
            @Override public boolean canExtract() { return true; }
            @Override public boolean canReceive() { return true; }
        };
        this.energyCap = LazyOptional.of(() -> energyStorage);
    }

    public OmniBatteryBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof OmniBatteryBlock block ? block.getTier() : BatteryTier.LOW);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OmniBatteryBlockEntity be) {
        if (level.isClientSide) return;
        be.tickCount++;
        // 每秒（20 tick）冻结一次实时速率，并记录趋势图采样点
        be.secondTick++;
        if (be.secondTick >= 20) {
            be.lastAbsorbed = be.absorbedThisSecond;
            be.lastSupplied = be.suppliedThisSecond;
            be.recordHistory(be.lastAbsorbed, be.lastSupplied);
            be.absorbedThisSecond = 0;
            be.suppliedThisSecond = 0;
            be.secondTick = 0;
        }
        if (be.tickCount % SCAN_INTERVAL != 0) return;
        if (be.mode == BatteryMode.OFF) return;

        ServerLevel serverLevel = (ServerLevel) level;
        // 速率可能是 long（ULTIMATE 无限档 = Long.MAX_VALUE，突破 int 上限 21 亿）
        long rate = be.tier.rate(be.rateIndex);
        if (be.mode.canAbsorb()) {
            be.absorbedThisSecond += be.absorbFromLoadedDimensions(serverLevel, rate);
        }
        if (be.mode.canCharge()) {
            // 机器供电与玩家物品充电共享 rate 预算
            long remaining = rate;
            long machineSupplied = be.supplyToLoadedDimensions(serverLevel, remaining);
            remaining -= machineSupplied;
            long playerSupplied = 0;
            if (remaining > 0) playerSupplied = be.chargePlayersInLoadedDimensions(serverLevel, remaining);
            be.suppliedThisSecond += machineSupplied + playerSupplied;
        }
    }

    private long absorbFromLoadedDimensions(ServerLevel originLevel, long budget) {
        long space = tier.capacity() - energy;
        long remaining = Math.min(budget, space);
        if (remaining <= 0) return 0;

        long start = remaining;
        for (ServerLevel level : originLevel.getServer().getAllLevels()) {
            if (range >= 0 && level != originLevel) continue;
            StickerSavedData stickerData = StickerSavedData.get(level);
            for (BlockPos targetPos : getLoadedBlockEntityPositions(level)) {
                if (remaining <= 0) break;
                if (level == originLevel && targetPos.equals(worldPosition)) continue;
                StickerSavedData.StickerEntry stickerEntry = stickerData.getEntry(targetPos);
                StickerMode sticker = stickerEntry == null ? null : stickerEntry.mode();
                if (sticker == null || !sticker.isActiveTransferMode()) continue;
                // 吸电不受"公开"放开：只认主人/队友，避免公开模式下吸别人机器的电
                if (!canAbsorbSticker(stickerEntry)) continue;
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be == null || be.isRemoved()) { stickerData.removeSticker(targetPos); continue; }
                if (be instanceof OmniBatteryBlockEntity) { stickerData.removeSticker(targetPos); continue; }
                if (!hasAnyEnergyCapability(be)) { stickerData.removeSticker(targetPos); continue; }
                // Sticker-only whitelist: absorb only from machines explicitly marked as ABSORB or OVERLOAD.
                if (sticker != StickerMode.ABSORB && sticker != StickerMode.OVERLOAD) continue;

                if (sticker == StickerMode.ABSORB) {
                    // 无限档突破：对同一目标重复传输，直到预算用尽或对方无能量
                    int loops = 0;
                    while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                        int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                        int moved = tryAbsorbFromFirstSide(be, c, sticker);
                        if (moved <= 0) break;
                        remaining -= moved;
                    }
                } else {
                    for (Direction dir : capabilitySides()) {
                        if (remaining <= 0) break;
                        int loops = 0;
                        while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                            int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                            int moved = tryAbsorbFrom(be, dir, c, sticker);
                            if (moved <= 0) break;
                            remaining -= moved;
                        }
                    }
                }
            }
            if (remaining <= 0) break;
        }
        if (remaining < start) setChanged();
        return start - remaining;
    }

    private long supplyToLoadedDimensions(ServerLevel originLevel, long budget) {
        long remaining = Math.min(budget, energy);
        if (remaining <= 0) return 0;

        long start = remaining;
        for (ServerLevel level : originLevel.getServer().getAllLevels()) {
            if (range >= 0 && level != originLevel) continue;
            StickerSavedData stickerData = StickerSavedData.get(level);
            for (BlockPos targetPos : getLoadedBlockEntityPositions(level)) {
                if (remaining <= 0) break;
                if (level == originLevel && targetPos.equals(worldPosition)) continue;
                StickerSavedData.StickerEntry stickerEntry = stickerData.getEntry(targetPos);
                StickerMode sticker = stickerEntry == null ? null : stickerEntry.mode();
                if (sticker == null || !sticker.isActiveTransferMode()) continue;
                if (!canUseSticker(stickerEntry)) continue;
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be == null || be.isRemoved()) { stickerData.removeSticker(targetPos); continue; }
                if (be instanceof OmniBatteryBlockEntity) { stickerData.removeSticker(targetPos); continue; }
                if (!hasAnyEnergyCapability(be)) { stickerData.removeSticker(targetPos); continue; }
                // Sticker-only whitelist: supply only to machines explicitly marked as SUPPLY or OVERLOAD.
                if (sticker != StickerMode.SUPPLY && sticker != StickerMode.OVERLOAD) continue;

                if (sticker == StickerMode.SUPPLY) {
                    // 无限档突破：对同一目标重复传输，直到预算用尽或对方已满
                    int loops = 0;
                    while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                        int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                        int moved = trySupplyToFirstSide(be, c, sticker);
                        if (moved <= 0) break;
                        remaining -= moved;
                    }
                } else {
                    for (Direction dir : capabilitySides()) {
                        if (remaining <= 0) break;
                        int loops = 0;
                        while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                            int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                            int moved = trySupplyTo(be, dir, c, sticker);
                            if (moved <= 0) break;
                            remaining -= moved;
                        }
                    }
                }
            }
            if (remaining <= 0) break;
        }
        if (remaining < start) setChanged();
        return start - remaining;
    }


    private boolean canUseSticker(StickerSavedData.StickerEntry entry) {
        if (access == BatteryAccess.PUBLIC) return true;
        if (entry == null) return false;
        // 贴纸必须明确归属（无归属的旧贴纸在私人/队伍模式下不放行）
        return entry.owner() != null && canUseUuid(entry.owner(), entry.ownerName());
    }

    /**
     * 是否允许本电池从这张贴纸标记的机器**吸电**。
     * 公开权限**不**放开吸电，否则任何人都能借"公开"把电池的吸电口对准别人的机器。
     */
    private boolean canAbsorbSticker(StickerSavedData.StickerEntry entry) {
        if (entry == null || entry.owner() == null) return false;
        if (ownerUuid == null) return false;
        if (isOwner(entry.owner()) || trustedPlayers.containsKey(entry.owner())) return true;
        if (access == BatteryAccess.TEAM) return sameTeam(ownerUuid, ownerName, entry.owner(), entry.ownerName());
        return false;
    }

    /** 某玩家（UUID）是否可用本电池传电：私人 / 队伍 / 公开。 */
    private boolean canUseUuid(UUID uuid) {
        return canUseUuid(uuid, null);
    }

    /** 同上；knownName 为已知玩家名（离线队友也能可靠判定队伍）。 */
    private boolean canUseUuid(UUID uuid, String knownName) {
        if (access == BatteryAccess.PUBLIC) return true;
        if (uuid == null) return false;
        // 未认领的电池不放行任何人
        if (ownerUuid == null) return false;
        if (isOwner(uuid) || trustedPlayers.containsKey(uuid)) return true;
        if (access == BatteryAccess.TEAM) return sameTeam(ownerUuid, ownerName, uuid, knownName);
        return false;
    }

    /**
     * 两名玩家是否处于同一队伍：原版记分板队伍（/team），或 FTB Teams（若已安装）。
     * 判定失败时给出限流诊断（30 秒最多一条）。
     */
    private boolean sameTeam(UUID a, String nameA, UUID b, String nameB) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        // FTB 优先：按 UUID 判定，双方离线同样有效
        if (sameFtbTeam(a, b)) return true;
        if (sameVanillaTeam(a, nameA, b, nameB)) return true;
        diagnoseTeamFailure(a, b);
        return false;
    }

    private static long lastTeamDiag = 0L;

    /** 队伍判定失败时的诊断输出（限流 30 秒）。 */
    private void diagnoseTeamFailure(UUID a, UUID b) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = System.currentTimeMillis();
        if (now - lastTeamDiag < 30000L) return;
        lastTeamDiag = now;
        net.minecraft.server.MinecraftServer server = sl.getServer();
        String na = playerNameOf(server, a);
        String nb = playerNameOf(server, b);
        String teamA = "(未知)";
        String teamB = "(未知)";
        if (na != null) {
            net.minecraft.world.scores.PlayerTeam t = server.getScoreboard().getPlayersTeam(na);
            teamA = t == null ? "(无队伍)" : t.getName();
        }
        if (nb != null) {
            net.minecraft.world.scores.PlayerTeam t = server.getScoreboard().getPlayersTeam(nb);
            teamB = t == null ? "(无队伍)" : t.getName();
        }
        boolean ftbAvailable;
        try {
            ftbAvailable = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI") != null;
        } catch (Throwable t) {
            ftbAvailable = false;
        }
        server.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[万能电池·队伍诊断] 电池主人=" + (ownerUuid == null ? "未认领!" : ownerName)
                        + " 权限档=" + access.display()
                        + " ｜ 主人=" + (na == null ? String.valueOf(a) : na) + " 原版队伍=" + teamA
                        + " ｜ 对方=" + (nb == null ? String.valueOf(b) : nb) + " 原版队伍=" + teamB
                        + " ｜ FTB已装=" + ftbAvailable + " FTB同队=" + sameFtbTeam(a, b)));
    }

    /** 原版 /team 记分板队伍（按玩家名查，主人离线也可判定）。 */
    private boolean sameVanillaTeam(UUID a, String knownA, UUID b, String knownB) {
        if (!(level instanceof ServerLevel sl)) return false;
        net.minecraft.server.MinecraftServer server = sl.getServer();
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
        // 优先使用调用方记录的玩家名，离线玩家也能查到记分板队伍
        String nameA = knownA != null && !knownA.isEmpty() ? knownA : playerNameOf(server, a);
        String nameB = knownB != null && !knownB.isEmpty() ? knownB : playerNameOf(server, b);
        if (nameA == null || nameB == null) return false;
        net.minecraft.world.scores.PlayerTeam ta = scoreboard.getPlayersTeam(nameA);
        return ta != null && ta == scoreboard.getPlayersTeam(nameB);
    }

    /**
     * FTB Teams 兼容（软依赖：纯反射，未安装时返回 false，不产生编译期依赖）。
     * 关键点：必须通过**公开接口类**（FTBTeamsAPI$API / TeamManager / Team）查找方法，
     * 实现类是包私有的，经实现类反射调用会被拒绝。
     * 判定基于队伍成员集合，因此**离线玩家同样有效**。
     */
    private static boolean sameFtbTeam(java.util.UUID a, java.util.UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        try {
            Object api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI")
                    .getMethod("api").invoke(null);
            if (api == null) return false;
            Class<?> apiIface = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            Object loaded = apiIface.getMethod("isManagerLoaded").invoke(api);
            if (!(loaded instanceof Boolean lb) || !lb) return false;
            Object manager = apiIface.getMethod("getManager").invoke(api);
            if (manager == null) return false;
            Class<?> mgrIface = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");

            // 1) 直接由 manager 回答
            Object direct = mgrIface
                    .getMethod("arePlayersInSameTeam", java.util.UUID.class, java.util.UUID.class)
                    .invoke(manager, a, b);
            if (direct instanceof Boolean db && db) return true;

            // 2) 比较双方队伍：ID 相同，或 b 属于 a 的队伍成员（离线玩家也在 members 中）
            Class<?> teamIface = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            java.lang.reflect.Method getTeam = mgrIface.getMethod("getPlayerTeamForPlayerID", java.util.UUID.class);
            Object oa = getTeam.invoke(manager, a);
            Object ob = getTeam.invoke(manager, b);
            if (!(oa instanceof java.util.Optional<?> pa) || !(ob instanceof java.util.Optional<?> pb)) return false;
            if (pa.isEmpty() || pb.isEmpty()) return false;
            Object ta = pa.get();
            Object tb = pb.get();
            Object ida = teamIface.getMethod("getId").invoke(ta);
            Object idb = teamIface.getMethod("getId").invoke(tb);
            if (ida != null && ida.equals(idb)) return true;
            Object members = teamIface.getMethod("getMembers").invoke(ta);
            return members instanceof java.util.Set<?> ms && ms.contains(b);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object optionalOrNull(Object o) {
        if (o instanceof java.util.Optional<?> opt) return opt.orElse(null);
        return o;
    }


    /** UUID -> 玩家名：优先在线玩家，离线则查 usercache。 */
    private static String playerNameOf(net.minecraft.server.MinecraftServer server, UUID uuid) {
        if (uuid == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() == null) return null;
        return server.getProfileCache().get(uuid)
                .map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }

    private boolean isOwner(UUID uuid) {
        return uuid != null && ownerUuid != null && ownerUuid.equals(uuid);
    }


    private boolean hasAnyEnergyCapability(BlockEntity be) {
        if (be == null) return false;
        for (Direction dir : capabilitySides()) {
            if (be.getCapability(ForgeCapabilities.ENERGY, dir).isPresent()) return true;
        }
        return false;
    }


    private int tryAbsorbFromFirstSide(BlockEntity be, int request, StickerMode sticker) {
        // Normal mode must respect machine side IO limits. Do not use the null/internal capability here.
        for (Direction dir : directionalCapabilitySides()) {
            int moved = tryAbsorbFrom(be, dir, request, sticker);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private int trySupplyToFirstSide(BlockEntity be, int request, StickerMode sticker) {
        // Normal mode must respect machine side IO limits. Do not use the null/internal capability here.
        for (Direction dir : directionalCapabilitySides()) {
            int moved = trySupplyTo(be, dir, request, sticker);
            if (moved > 0) return moved;
        }
        return 0;
    }


    /** 过载探测：记录各目标机器上次的能量，用于判断它是否"正在运行"（能量在下降）。 */
    private final java.util.HashMap<Long, Long> overloadLastEnergy = new java.util.HashMap<>();

    /**
     * 目标机器是否"正在运行"：与上次探测相比，它的能量在下降（说明正在消耗）。
     * 只有运行中的机器才允许过载强灌——待机机器保持它自己本来的 NBT，
     * 不会被电池持续喂电，电池也就不会被抽干。
     */
    private boolean isTargetRunning(net.minecraft.core.BlockPos pos, IEnergyStorage storage) {
        long now;
        try {
            now = storage.getEnergyStored();
            if (now <= 0L) now = readEnergyReflective(storage);
        } catch (Throwable t) {
            now = readEnergyReflective(storage);
        }
        if (now < 0L) return false;
        Long prev = overloadLastEnergy.put(pos.asLong(), now);
        return prev != null && now < prev;
    }


    /** 硬灌无效（电被机器丢弃/存不住）的目标；拉黑后不再对它硬灌，避免变成无底洞。 */
    private final java.util.HashSet<Long> overloadVoidTargets = new java.util.HashSet<>();

    /** 读取机器当前能量：反射优先，其次标准接口；都读不到返回 -1。 */
    private long probeEnergy(IEnergyStorage storage) {
        long v = readEnergyReflective(storage);
        if (v >= 0L) return v;
        try {
            return storage.getEnergyStored();
        } catch (Throwable t) {
            return -1L;
        }
    }

    private int tryAbsorbFrom(BlockEntity be, @Nullable Direction dir, int request, StickerMode sticker) {
        LazyOptional<IEnergyStorage> opt = be.getCapability(ForgeCapabilities.ENERGY, dir);
        return opt.map(storage -> {
            if (isOwnOrOmniBatteryStorage(be, storage)) return 0;
            if (sticker == StickerMode.ABSORB) {
                return transferExtractOnce(storage, request);
            }
            if (sticker == StickerMode.OVERLOAD) {
                if (!canActuallyExtract(storage, request) && readEnergyReflective(storage) <= 0) return 0;
                int moved = transferExtractLoop(storage, request);
                if (moved < request) moved += drainEnergyReflective(storage, request - moved);
                if (moved < request) moved += drainEnergyNbt(be, request - moved);
                return moved;
            }
            return 0;
        }).orElse(0);
    }

    private int trySupplyTo(BlockEntity be, @Nullable Direction dir, int request, StickerMode sticker) {
        LazyOptional<IEnergyStorage> opt = be.getCapability(ForgeCapabilities.ENERGY, dir);
        return opt.map(storage -> {
            if (isOwnOrOmniBatteryStorage(be, storage)) return 0;
            if (sticker == StickerMode.SUPPLY) {
                return transferReceiveOnce(storage, request);
            }
            if (sticker == StickerMode.OVERLOAD) {
                int moved = transferReceiveLoop(storage, request);
                long key = be.getBlockPos().asLong();
                // 硬灌前记下机器能量，灌完再验：机器能量没涨 = 这些电被丢弃（无底洞）。
                // 此时把白送出去的电退回电池，并把该目标拉黑，不再对它硬灌。
                if (moved < request && !overloadVoidTargets.contains(key)) {
                    long before = probeEnergy(storage);
                    int extra = fillEnergyReflective(storage, request - moved);
                    if (extra <= 0) {
                        extra = fillEnergyNbt(be, request - moved);
                    }
                    if (extra > 0) {
                        long after = probeEnergy(storage);
                        if (before >= 0L && after >= 0L && after <= before) {
                            energyStorage.receiveEnergy(extra, false);   // 退回电池
                            overloadVoidTargets.add(key);                // 拉黑：不再白送
                        } else {
                            moved += extra;
                        }
                    }
                }
                return moved;
            }
            return 0;
        }).orElse(0);
    }


    private int transferExtractOnce(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        int can = storage.extractEnergy(request, true);
        if (can <= 0) return 0;
        int accepted = energyStorage.receiveEnergy(can, true);
        if (accepted <= 0) return 0;
        int extracted = storage.extractEnergy(accepted, false);
        if (extracted <= 0) return 0;
        energyStorage.receiveEnergy(extracted, false);
        return extracted;
    }

    private int transferReceiveOnce(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        int can = storage.receiveEnergy(request, true);
        if (can <= 0) return 0;
        int extracted = energyStorage.extractEnergy(can, true);
        if (extracted <= 0) return 0;
        int accepted = storage.receiveEnergy(extracted, false);
        if (accepted <= 0) return 0;
        energyStorage.extractEnergy(accepted, false);
        return accepted;
    }

    private int transferExtractLoop(IEnergyStorage storage, int request) {
        int moved = 0;
        int loops = 0;
        while (moved < request && loops++ < MAX_TRANSFER_LOOPS_PER_SIDE) {
            int want = request - moved;
            int can = storage.extractEnergy(want, true);
            if (can <= 0) break;
            int accepted = energyStorage.receiveEnergy(can, true);
            if (accepted <= 0) break;
            int extracted = storage.extractEnergy(accepted, false);
            if (extracted <= 0) break;
            energyStorage.receiveEnergy(extracted, false);
            moved += extracted;
        }
        return moved;
    }

    private int transferReceiveLoop(IEnergyStorage storage, int request) {
        int moved = 0;
        int loops = 0;
        while (moved < request && loops++ < MAX_TRANSFER_LOOPS_PER_SIDE) {
            int want = request - moved;
            int can = storage.receiveEnergy(want, true);
            if (can <= 0) break;
            int extracted = energyStorage.extractEnergy(can, true);
            if (extracted <= 0) break;
            int accepted = storage.receiveEnergy(extracted, false);
            if (accepted <= 0) break;
            energyStorage.extractEnergy(accepted, false);
            moved += accepted;
        }
        return moved;
    }


    private boolean isOwnOrOmniBatteryStorage(BlockEntity be, IEnergyStorage storage) {
        if (be == this || be instanceof OmniBatteryBlockEntity) return true;
        if (storage == this.energyStorage) return true;
        return false;
    }

    private int drainEnergyNbt(BlockEntity be, int request) {
        if (request <= 0 || be == null || be instanceof OmniBatteryBlockEntity) return 0;
        try {
            CompoundTag tag = be.saveWithFullMetadata();
            NbtEnergyEdit edit = new NbtEnergyEdit(request, false);
            edit.visitCompound(tag);
            int moved = BatteryData.clampToForgeInt(edit.moved);
            if (moved <= 0) return 0;
            int accepted = energyStorage.receiveEnergy(moved, true);
            if (accepted <= 0) return 0;
            if (accepted < moved) {
                edit = new NbtEnergyEdit(accepted, false);
                tag = be.saveWithFullMetadata();
                edit.visitCompound(tag);
                moved = BatteryData.clampToForgeInt(edit.moved);
            }
            be.load(tag);
            be.setChanged();
            energyStorage.receiveEnergy(moved, false);
            return moved;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int fillEnergyNbt(BlockEntity be, int request) {
        if (request <= 0 || be == null || be instanceof OmniBatteryBlockEntity) return 0;
        try {
            int extracted = energyStorage.extractEnergy(request, true);
            if (extracted <= 0) return 0;
            CompoundTag tag = be.saveWithFullMetadata();
            NbtEnergyEdit edit = new NbtEnergyEdit(extracted, true);
            edit.visitCompound(tag);
            int moved = BatteryData.clampToForgeInt(edit.moved);
            if (moved <= 0) return 0;
            be.load(tag);
            be.setChanged();
            energyStorage.extractEnergy(moved, false);
            return moved;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static class NbtEnergyEdit {
        private long budget;
        private long moved;
        private final boolean fill;

        private NbtEnergyEdit(long budget, boolean fill) {
            this.budget = Math.max(0L, budget);
            this.fill = fill;
        }

        private void visitCompound(CompoundTag tag) {
            if (budget <= 0 || tag == null) return;
            for (String key : new java.util.ArrayList<>(tag.getAllKeys())) {
                if (budget <= 0) return;
                net.minecraft.nbt.Tag value = tag.get(key);
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                if (value instanceof CompoundTag compound) {
                    visitCompound(compound);
                } else if (value instanceof net.minecraft.nbt.ListTag list) {
                    visitList(list);
                } else if (isEnergyNbtName(lower) && tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
                    long current = tag.getLong(key);
                    long max = findNbtCapacityNear(tag);
                    if (fill) {
                        if (max <= 0) max = Long.MAX_VALUE;
                        long add = Math.min(budget, Math.max(0L, max - current));
                        if (add > 0) {
                            putNumber(tag, key, value, current + add);
                            budget -= add;
                            moved += add;
                        }
                    } else {
                        long remove = Math.min(budget, Math.max(0L, current));
                        if (remove > 0) {
                            putNumber(tag, key, value, current - remove);
                            budget -= remove;
                            moved += remove;
                        }
                    }
                }
            }
        }

        private void visitList(net.minecraft.nbt.ListTag list) {
            for (int i = 0; i < list.size() && budget > 0; i++) {
                net.minecraft.nbt.Tag value = list.get(i);
                if (value instanceof CompoundTag compound) visitCompound(compound);
                else if (value instanceof net.minecraft.nbt.ListTag nested) visitList(nested);
            }
        }

        private static boolean isEnergyNbtName(String name) {
            if (name.equals("x") || name.equals("y") || name.equals("z") || name.equals("id") || name.equals("burntime") || name.equals("cooktime")) return false;
            return name.equals("energy") || name.equals("stored") || name.equals("storedenergy") || name.equals("energy_stored") ||
                    name.equals("energystored") || name.equals("joules") || name.equals("fe") || name.equals("power") ||
                    name.contains("energy") || name.contains("stored") || name.contains("joule");
        }

        private static long findNbtCapacityNear(CompoundTag tag) {
            long max = -1L;
            for (String key : tag.getAllKeys()) {
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                if ((lower.contains("capacity") || lower.contains("max") || lower.contains("limit")) && tag.contains(key, net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
                    max = Math.max(max, tag.getLong(key));
                }
            }
            return max;
        }

        private static void putNumber(CompoundTag tag, String key, net.minecraft.nbt.Tag oldValue, long value) {
            if (oldValue instanceof net.minecraft.nbt.IntTag) tag.putInt(key, BatteryData.clampToForgeInt(value));
            else tag.putLong(key, value);
        }
    }

    private boolean isGeneratorLike(BlockEntity be) {
        try {
            String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType())).toLowerCase(java.util.Locale.ROOT);
            String blockId = String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock())).toLowerCase(java.util.Locale.ROOT);
            String cls = be.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            String text = id + " " + blockId + " " + cls;
            String[] keys = new String[]{"generator", "dynamo", "alternator", "reactor", "solar", "wind", "water_wheel", "thermo", "thermal", "heat", "lava", "biofuel", "combustion", "steam", "turbine", "发电", "gen"};
            for (String key : keys) if (text.contains(key)) return true;
            // Mekanism generators often expose obvious generation-related class names.
            if (text.contains("mekanismgenerators")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private int drainEnergyReflective(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        long stored = readEnergyReflective(storage);
        if (stored <= 0) return 0;
        int amount = BatteryData.clampToForgeInt(Math.min((long) request, stored));
        int accepted = energyStorage.receiveEnergy(amount, true);
        if (accepted <= 0) return 0;
        if (writeEnergyReflective(storage, stored - accepted)) {
            energyStorage.receiveEnergy(accepted, false);
            return accepted;
        }
        return 0;
    }

    private int fillEnergyReflective(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        long stored = readEnergyReflective(storage);
        long max = readMaxEnergyReflective(storage);
        if (max <= stored) return 0;
        int amount = BatteryData.clampToForgeInt(Math.min((long) request, max - stored));
        int extracted = energyStorage.extractEnergy(amount, true);
        if (extracted <= 0) return 0;
        if (writeEnergyReflective(storage, stored + extracted)) {
            energyStorage.extractEnergy(extracted, false);
            return extracted;
        }
        return 0;
    }

    private static long readEnergyReflective(Object obj) {
        EnergyFields fields = findEnergyFields(obj);
        if (fields == null || fields.energy == null) return -1L;
        try { return getNumberField(fields.owner, fields.energy); } catch (Throwable ignored) { return -1L; }
    }

    private static long readMaxEnergyReflective(Object obj) {
        EnergyFields fields = findEnergyFields(obj);
        if (fields == null) return -1L;
        try {
            if (fields.capacity != null) return getNumberField(fields.owner, fields.capacity);
            if (obj instanceof IEnergyStorage storage) return storage.getMaxEnergyStored();
        } catch (Throwable ignored) {}
        return -1L;
    }

    private static boolean writeEnergyReflective(Object obj, long value) {
        EnergyFields fields = findEnergyFields(obj);
        if (fields == null || fields.energy == null) return false;
        try {
            setNumberField(fields.owner, fields.energy, Math.max(0L, value));
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static EnergyFields findEnergyFields(Object root) {
        return findEnergyFields(root, 0, new java.util.HashSet<>());
    }

    private static EnergyFields findEnergyFields(Object obj, int depth, java.util.HashSet<Object> visited) {
        if (obj == null || depth > 3 || visited.contains(obj)) return null;
        visited.add(obj);
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            Field energy = null;
            Field capacity = null;
            for (Field f : c.getDeclaredFields()) {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                Class<?> t = f.getType();
                if ((t == int.class || t == long.class || Number.class.isAssignableFrom(t)) && isEnergyName(n)) energy = f;
                if ((t == int.class || t == long.class || Number.class.isAssignableFrom(t)) && isCapacityName(n)) capacity = f;
            }
            if (energy != null) return new EnergyFields(obj, energy, capacity);
            c = c.getSuperclass();
        }
        c = obj.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object child = f.get(obj);
                    if (child == null || child == obj) continue;
                    if (child instanceof String || child instanceof Number || child instanceof Enum<?>) continue;
                    String type = child.getClass().getName().toLowerCase(java.util.Locale.ROOT);
                    if (!(child instanceof IEnergyStorage) && !type.contains("energy") && !type.contains("storage")) continue;
                    EnergyFields found = findEnergyFields(child, depth + 1, visited);
                    if (found != null) return found;
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean isEnergyName(String name) {
        return name.equals("energy") || name.equals("stored") || name.equals("storedenergy") || name.equals("energycontainer") || name.equals("f_1550_");
    }

    private static boolean isCapacityName(String name) {
        return name.equals("capacity") || name.equals("maxenergy") || name.equals("maxenergystored") || name.equals("maxstored") || name.equals("max") || name.equals("f_1551_");
    }

    private static long getNumberField(Object owner, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object v = field.get(owner);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static void setNumberField(Object owner, Field field, long value) throws IllegalAccessException {
        field.setAccessible(true);
        if (field.getType() == int.class || field.getType() == Integer.class) field.set(owner, BatteryData.clampToForgeInt(value));
        else if (field.getType() == long.class || field.getType() == Long.class) field.set(owner, value);
    }

    private record EnergyFields(Object owner, Field energy, Field capacity) {}


    private static Direction[] capabilitySides() {
        return new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    }

    private static Direction[] directionalCapabilitySides() {
        return new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    }

    /** Charge player inventory/offhand/armor/curios across loaded dimensions. Does not recurse into backpack/container items. */
    private long chargePlayersInLoadedDimensions(ServerLevel originLevel, long budget) {
        long remaining = Math.min(budget, energy);
        if (remaining <= 0) return 0;
        long start = remaining;

        for (ServerPlayer player : originLevel.getServer().getPlayerList().getPlayers()) {
            if (remaining <= 0) break;
            if (!(player.level() instanceof ServerLevel playerLevel)) continue;
            if (range >= 0 && playerLevel != originLevel) continue;
            if (!canUsePower(player)) continue;
            if (!isLevelLoaded(playerLevel, player.blockPosition())) continue;
            if (!isTargetInRange(player.blockPosition())) continue;

            List<ItemStack> targets = new ArrayList<>();
            // 物品栏供电开关（背包类容器物品永不受电，见下方 isBackpackLikeItem）
            if (chargeInventory) {
                Inventory inv = player.getInventory();
                // Player inventory only. Do NOT recurse into backpack/container items.
                targets.addAll(inv.items);
                targets.addAll(inv.offhand);
                targets.addAll(inv.armor);
            }
            // 饰品栏供电开关
            if (chargeCurios) {
                addCuriosItemsSoft(player, targets);
            }

            int touched = 0;
            for (ItemStack target : targets) {
                if (remaining <= 0 || touched++ > MAX_PLAYER_ITEMS_PER_SCAN) break;
                if (target.isEmpty() || target.getItem() instanceof OmniBatteryItem || isBackpackLikeItem(target)) continue;
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                remaining -= moveEnergyToPlayerItem(target, chunk);
            }
        }
        if (remaining < start) setChanged();
        return start - remaining;
    }

    private static boolean isBackpackLikeItem(ItemStack stack) {
        try {
            String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().toLowerCase(java.util.Locale.ROOT);
            String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
            return id.contains("backpack") || id.contains("rucksack") || id.contains("bag") || id.contains("satchel") ||
                    id.contains("sophisticatedbackpacks") || id.contains("travelersbackpack") ||
                    name.contains("背包") || name.contains("backpack") || name.contains("rucksack") || name.contains("satchel");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int moveEnergyToPlayerItem(ItemStack target, int max) {
        if (max <= 0) return 0;
        LazyOptional<IEnergyStorage> opt = target.getCapability(ForgeCapabilities.ENERGY);
        return opt.map(storage -> {
            int moved = transferReceiveLoop(storage, max);
            if (moved < max) moved += fillEnergyReflective(storage, max - moved);
            return moved;
        }).orElse(0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addCuriosItemsSoft(ServerPlayer player, List<ItemStack> out) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("curios")) return;
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Object optional = getCuriosInventory.invoke(null, player);
            if (!(optional instanceof Optional opt) || opt.isEmpty()) return;
            Object handler = opt.get();
            Method getCurios = handler.getClass().getMethod("getCurios");
            Object curios = getCurios.invoke(handler);
            if (!(curios instanceof Map map)) return;
            for (Object slotHandlerObj : map.values()) {
                Method getStacks = slotHandlerObj.getClass().getMethod("getStacks");
                Object stacksObj = getStacks.invoke(slotHandlerObj);
                if (stacksObj instanceof net.minecraftforge.items.IItemHandler itemHandler) {
                    for (int i = 0; i < itemHandler.getSlots(); i++) out.add(itemHandler.getStackInSlot(i));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean canActuallyExtract(IEnergyStorage storage, int request) {
        try { return storage.extractEnergy(Math.max(1, Math.min(request, 1024)), true) > 0; } catch (Exception e) { return false; }
    }

    private static boolean canActuallyReceive(IEnergyStorage storage, int request) {
        try { return storage.receiveEnergy(Math.max(1, Math.min(request, 1024)), true) > 0; } catch (Exception e) { return false; }
    }

    private static boolean isLevelLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private List<BlockPos> getLoadedBlockEntityPositions(ServerLevel level) {
        return range < 0 ? getUltimateLoadedBlockEntityPositions(level) : getRangedLoadedBlockEntityPositions(level);
    }

    private List<BlockPos> getUltimateLoadedBlockEntityPositions(ServerLevel level) {
        // The reflective full-loaded-chunk path is kept as a bonus, but the stable path below is used first.
        // It covers chunks that are actually ticking: around online players and around this battery itself.
        List<BlockPos> result = new ArrayList<>();
        java.util.HashSet<Long> visited = new java.util.HashSet<>();
        collectPlayerViewLoadedBlockEntities(level, result, visited);
        collectAroundBatteryLoadedBlockEntities(level, result, visited);
        if (!result.isEmpty()) return result;
        return getAllLoadedBlockEntityPositions(level);
    }

    private void collectPlayerViewLoadedBlockEntities(ServerLevel level, List<BlockPos> out, java.util.HashSet<Long> visited) {
        int radius = Math.max(2, level.getServer().getPlayerList().getViewDistance() + ULTIMATE_EXTRA_CHUNK_RADIUS);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel playerLevel) || playerLevel != level) continue;
            collectLoadedBlockEntitiesAround(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, radius, out, visited);
        }
    }

    private void collectAroundBatteryLoadedBlockEntities(ServerLevel level, List<BlockPos> out, java.util.HashSet<Long> visited) {
        if (level != this.level) return;
        collectLoadedBlockEntitiesAround(level, worldPosition.getX() >> 4, worldPosition.getZ() >> 4, ULTIMATE_BATTERY_FALLBACK_CHUNK_RADIUS, out, visited);
    }

    private void collectLoadedBlockEntitiesAround(ServerLevel level, int centerChunkX, int centerChunkZ, int radius, List<BlockPos> out, java.util.HashSet<Long> visited) {
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                long key = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
                if (!visited.add(key)) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) continue;
                    out.add(be.getBlockPos());
                }
            }
        }
    }

    private List<BlockPos> getRangedLoadedBlockEntityPositions(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        int chunkRadius = Math.max(1, (Math.max(1, range) + 15) / 16);
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        int count = 0;
        for (int chunkX = cx - chunkRadius; chunkX <= cx + chunkRadius; chunkX++) {
            for (int chunkZ = cz - chunkRadius; chunkZ <= cz + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) continue;
                    BlockPos bePos = be.getBlockPos();
                    if (!isTargetInRange(bePos)) continue;
                    result.add(bePos);
                    if (++count >= maxBlocksPerScan()) return result;
                }
            }
        }
        return result;
    }

    private List<BlockPos> getAllLoadedBlockEntityPositions(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        for (LevelChunk chunk : getLoadedChunks(level)) {
            if (chunk == null) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null || be.isRemoved()) continue;
                result.add(be.getBlockPos());
            }
        }
        return result;
    }

    private boolean isTargetInRange(BlockPos targetPos) {
        if (range < 0) return true;
        long r = Math.max(1, range);
        return targetPos.distSqr(worldPosition) <= r * r;
    }

    private int maxBlocksPerScan() {
        return range < 0 ? Integer.MAX_VALUE : MAX_BLOCKS_PER_SCAN;
    }

    /** Reflectively enumerate loaded chunks to avoid depending on obfuscated internals directly. */
    @SuppressWarnings("unchecked")
    private static List<LevelChunk> getLoadedChunks(ServerLevel level) {
        List<LevelChunk> chunks = new ArrayList<>();
        try {
            Object chunkSource = level.getChunkSource();
            Object chunkMap = null;
            for (String name : new String[]{"chunkMap", "f_8325_"}) {
                try {
                    Field f = chunkSource.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    chunkMap = f.get(chunkSource);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (chunkMap == null) return chunks;

            Object visibleChunks = null;
            for (String name : new String[]{"visibleChunkMap", "f_140130_"}) {
                try {
                    Field f = chunkMap.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    visibleChunks = f.get(chunkMap);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            Iterable<?> holders = null;
            if (visibleChunks instanceof Map<?, ?> map) {
                holders = map.values();
            } else if (visibleChunks instanceof Iterable<?> iterable) {
                holders = iterable;
            } else {
                try {
                    Method values = visibleChunks.getClass().getMethod("values");
                    Object valueCollection = values.invoke(visibleChunks);
                    if (valueCollection instanceof Iterable<?> iterable) holders = iterable;
                } catch (Throwable ignored) {}
            }
            if (holders == null) return chunks;

            for (Object holder : holders) {
                LevelChunk chunk = extractLevelChunk(holder);
                if (chunk != null) chunks.add(chunk);
            }
        } catch (Throwable ignored) {}
        return chunks;
    }

    private static LevelChunk extractLevelChunk(Object holder) {
        if (holder == null) return null;
        try {
            for (String methodName : new String[]{"getTickingChunk", "m_140085_", "getFullChunk", "m_140082_", "getEntityTickingChunk", "m_140093_"}) {
                try {
                    Method m = holder.getClass().getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    Object value = m.invoke(holder);
                    if (value instanceof LevelChunk chunk) return chunk;
                } catch (NoSuchMethodException ignored) {}
            }
            for (Field f : holder.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object value = f.get(holder);
                if (value instanceof LevelChunk chunk) return chunk;
                if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof LevelChunk chunk) return chunk;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", energy);
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("RateIndex", rateIndex);
        tag.putInt("Range", range);
        tag.putInt("Access", access.ordinal());   // 0 私人 / 1 队伍 / 2 公开
        tag.putBoolean("ChargeInventory", chargeInventory);
        tag.putBoolean("ChargeCurios", chargeCurios);
        if (ownerUuid != null) tag.putString("OwnerUUID", ownerUuid.toString());
        if (ownerName != null) tag.putString("OwnerName", ownerName);
        CompoundTag trusted = new CompoundTag();
        for (Map.Entry<UUID, String> entry : trustedPlayers.entrySet()) {
            if (entry.getKey() != null) trusted.putString(entry.getKey().toString(), entry.getValue() == null ? "" : entry.getValue());
        }
        tag.put("TrustedPlayers", trusted);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = Math.max(0L, Math.min(tier.capacity(), tag.getLong("Energy")));
        BatteryMode[] modes = BatteryMode.values();
        int mi = tag.getInt("Mode");
        mode = (mi >= 0 && mi < modes.length) ? modes[mi] : BatteryMode.BOTH;
        rateIndex = Math.max(0, Math.min(4, tag.getInt("RateIndex")));
        range = tag.contains("Range") ? clampRange(tag.getInt("Range")) : tier.defaultRange();
        if (tag.contains("Access")) {
            int ai = tag.getInt("Access");
            BatteryAccess[] accs = BatteryAccess.values();
            access = ai >= 0 && ai < accs.length ? accs[ai] : BatteryAccess.PRIVATE;
        } else {
            // 旧档兼容：布尔 PublicAccess（true=公开 / false=私人）
            access = (!tag.contains("PublicAccess") || tag.getBoolean("PublicAccess"))
                    ? BatteryAccess.PUBLIC : BatteryAccess.PRIVATE;
        }
        chargeInventory = tag.contains("ChargeInventory") && tag.getBoolean("ChargeInventory");
        chargeCurios = tag.contains("ChargeCurios") && tag.getBoolean("ChargeCurios");
        ownerUuid = parseUUID(tag.getString("OwnerUUID"));
        ownerName = tag.getString("OwnerName");
        trustedPlayers.clear();
        if (tag.contains("TrustedPlayers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag trusted = tag.getCompound("TrustedPlayers");
            for (String key : trusted.getAllKeys()) {
                UUID uuid = parseUUID(key);
                if (uuid != null) trustedPlayers.put(uuid, trusted.getString(key));
            }
        }
    }

    @Override public Component getDisplayName() { return Component.translatable("screen.omnibattery.title"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new OmniBatteryMenu(id, inv, this);
    }

    public BatteryTier getTier() { return tier; }
    public long getEnergy() { return energy; }
    public long getAbsorbedPerSecond() { return lastAbsorbed; }
    public long getSuppliedPerSecond() { return lastSupplied; }
    public boolean isChargeInventory() { return chargeInventory; }
    public boolean isChargeCurios() { return chargeCurios; }

    public void setChargeInventory(boolean v) { chargeInventory = v; setChanged(); }
    public void setChargeCurios(boolean v) { chargeCurios = v; setChanged(); }

    /** 记录趋势图采样点（每秒一次）。 */
    public void recordHistory(long absorbed, long supplied) {
        absorbHistory[historyIndex] = absorbed;
        supplyHistory[historyIndex] = supplied;
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
    }

    /** 按时间顺序（旧->新）读取历史吸电值。 */
    public long getAbsorbHistory(int i) {
        int idx = (historyIndex + i) % HISTORY_SIZE;
        if (idx < 0) idx += HISTORY_SIZE;
        return absorbHistory[idx];
    }

    /** 按时间顺序（旧->新）读取历史供电值。 */
    public long getSupplyHistory(int i) {
        int idx = (historyIndex + i) % HISTORY_SIZE;
        if (idx < 0) idx += HISTORY_SIZE;
        return supplyHistory[idx];
    }

    public int getHistorySize() { return HISTORY_SIZE; }
    public void setEnergy(long e) { this.energy = Math.max(0L, Math.min(tier.capacity(), e)); setChanged(); }
    public BatteryMode getMode() { return mode; }
    public void setMode(BatteryMode m) { this.mode = m; setChanged(); }
    public int getRateIndex() { return rateIndex; }
    public void setRateIndex(int r) { this.rateIndex = Math.max(0, Math.min(4, r)); setChanged(); }
    public int getRange() { return range; }
    private int clampRange(int r) {
        if (tier.isUltimate() && r < 0) return -1;
        int max = 1;
        int[] steps = tier.rangeSteps();
        for (int step : steps) {
            if (step > 0) max = Math.max(max, step);
        }
        return Math.max(1, Math.min(max, r));
    }
    public void setRange(int r) {
        this.range = clampRange(r);
        setChanged();
    }

    public BatteryAccess getAccess() { return access; }
    public void setAccess(BatteryAccess a) { this.access = a; setChanged(); }
    public UUID getOwnerUuid() { return ownerUuid; }
    /** 是否已被认领（有主人）。 */
    public boolean isClaimed() { return ownerUuid != null; }

    public String getOwnerName() { return ownerName == null ? "" : ownerName; }
    public Map<UUID, String> getTrustedPlayers() { return new LinkedHashMap<>(trustedPlayers); }

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public boolean ensureOwner(Player player) {
        if (ownerUuid == null && player != null) {
            setOwner(player.getUUID(), player.getGameProfile().getName());
            return true;
        }
        return false;
    }

    /**
     * 谁可以修改电池设置：
     * 私人 → 仅主人；队伍 / 公开 → 主人 + 同队队友（原版 /team 或 FTB 队伍）。
     */
    public boolean canManage(Player player) {
        if (player == null || ownerUuid == null) return false;
        if (ownerUuid.equals(player.getUUID())) return true;
        if (access == BatteryAccess.PRIVATE) return false;
        // 队伍 / 公开档位：队友同样可以修改全部配置
        return sameTeam(ownerUuid, ownerName, player.getUUID(), player.getGameProfile().getName());
    }

    public boolean canUsePower(Player player) {
        if (player == null) return false;
        if (access == BatteryAccess.PUBLIC) return true;
        if (canUseUuid(player.getUUID())) return true;
        return false;   // 未认领的电池不放行任何人（右键一次即认领）
    }

    public void setTrustedPlayers(Map<UUID, String> trusted) {
        trustedPlayers.clear();
        if (trusted != null) {
            for (Map.Entry<UUID, String> entry : trusted.entrySet()) {
                if (entry.getKey() != null) trustedPlayers.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        setChanged();
    }

    public void addTrusted(Player player) {
        if (player != null) {
            trustedPlayers.put(player.getUUID(), player.getGameProfile().getName());
            setChanged();
        }
    }

    public void removeTrusted(UUID uuid) {
        if (uuid != null && trustedPlayers.remove(uuid) != null) setChanged();
    }

    public String trustedListDisplay() {
        if (trustedPlayers.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (String name : trustedPlayers.values()) {
            if (sb.length() > 0) sb.append("、");
            sb.append(name == null || name.isBlank() ? "未知玩家" : name);
        }
        return sb.toString();
    }

    private static UUID parseUUID(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }
}
