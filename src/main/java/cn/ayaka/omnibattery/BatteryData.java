package cn.ayaka.omnibattery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BatteryData {
    private BatteryData() {}

    public static final String ENERGY = "OmniEnergy";
    public static final String RANGE = "OmniRange";
    public static final String RATE = "OmniRate";
    public static final String MODE = "OmniMode";
    public static final String WORK = "OmniWorking";
    public static final String PUBLIC_ACCESS = "OmniPublicAccess";
    public static final String ACCESS = "OmniAccess";   // 0 私人 / 1 队伍 / 2 公开
    public static final String OWNER_UUID = "OmniOwnerUUID";
    public static final String OWNER_NAME = "OmniOwnerName";
    public static final String TRUSTED = "OmniTrustedPlayers";

    public static long getEnergy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0L;
        if (!tag.contains(ENERGY)) return 0L;
        return Math.max(0L, tag.getLong(ENERGY));
    }

    public static void setEnergy(ItemStack stack, long energy, BatteryTier tier) {
        stack.getOrCreateTag().putLong(ENERGY, Math.max(0L, Math.min(tier.capacity(), energy)));
    }

    public static int getRateIndex(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return Math.max(0, Math.min(4, tag.getInt(RATE)));
    }

    public static void setRateIndex(ItemStack stack, int index) {
        stack.getOrCreateTag().putInt(RATE, Math.max(0, Math.min(4, index)));
    }

    public static int cycleRate(ItemStack stack) {
        int next = (getRateIndex(stack) + 1) % 5;
        setRateIndex(stack, next);
        return next;
    }

    public static BatteryMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return BatteryMode.BOTH;
        int id = tag.getInt(MODE);
        BatteryMode[] modes = BatteryMode.values();
        if (id < 0 || id >= modes.length) return BatteryMode.BOTH;
        return modes[id];
    }

    public static void setMode(ItemStack stack, BatteryMode mode) {
        stack.getOrCreateTag().putInt(MODE, mode.ordinal());
    }

    public static BatteryMode cycleMode(ItemStack stack) {
        BatteryMode next = getMode(stack).next();
        setMode(stack, next);
        return next;
    }

    public static int getRange(ItemStack stack, BatteryTier tier) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(RANGE)) return tier.defaultRange();
        int range = tag.getInt(RANGE);
        if (tier.isUltimate() && range < 0) return -1;
        return Math.max(1, range);
    }

    public static void setRange(ItemStack stack, BatteryTier tier, int range) {
        if (tier.isUltimate() && range < 0) {
            stack.getOrCreateTag().putInt(RANGE, -1);
            return;
        }
        int max = 1;
        for (int step : tier.rangeSteps()) {
            if (step > 0) max = Math.max(max, step);
        }
        stack.getOrCreateTag().putInt(RANGE, Math.max(1, Math.min(max, range)));
    }

    public static int cycleRange(ItemStack stack, BatteryTier tier, boolean reverse) {
        int[] steps = tier.rangeSteps();
        int current = getRange(stack, tier);
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == current) { idx = i; break; }
        }
        idx = reverse ? (idx - 1 + steps.length) % steps.length : (idx + 1) % steps.length;
        setRange(stack, tier, steps[idx]);
        return steps[idx];
    }

    public static boolean isWorking(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(WORK) || tag.getBoolean(WORK);
    }

    public static void setWorking(ItemStack stack, boolean working) {
        stack.getOrCreateTag().putBoolean(WORK, working);
    }

    public static BatteryAccess getAccess(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return BatteryAccess.PRIVATE;
        if (tag.contains(ACCESS)) {
            int i = tag.getInt(ACCESS);
            BatteryAccess[] all = BatteryAccess.values();
            return i >= 0 && i < all.length ? all[i] : BatteryAccess.PRIVATE;
        }
        // 旧档兼容：布尔 PUBLIC_ACCESS（true=公开 / false=私人）
        return (!tag.contains(PUBLIC_ACCESS) || tag.getBoolean(PUBLIC_ACCESS))
                ? BatteryAccess.PUBLIC : BatteryAccess.PRIVATE;
    }

    public static void setAccess(ItemStack stack, BatteryAccess access) {
        stack.getOrCreateTag().putInt(ACCESS, access.ordinal());
    }

    public static String accessDisplay(ItemStack stack) {
        return getAccess(stack).display();
    }

    public static UUID getOwnerUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(OWNER_UUID)) return null;
        return parseUUID(tag.getString(OWNER_UUID));
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(OWNER_NAME)) return "";
        return tag.getString(OWNER_NAME);
    }

    public static void setOwner(ItemStack stack, Player player) {
        if (player != null) setOwner(stack, player.getUUID(), player.getGameProfile().getName());
    }

    public static void setOwner(ItemStack stack, UUID uuid, String name) {
        if (uuid == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(OWNER_UUID, uuid.toString());
        tag.putString(OWNER_NAME, name == null ? "" : name);
    }

    public static void ensureOwner(ItemStack stack, Player player) {
        if (player != null && getOwnerUUID(stack) == null) setOwner(stack, player);
    }

    public static boolean isOwner(ItemStack stack, Player player) {
        if (player == null) return false;
        UUID owner = getOwnerUUID(stack);
        return owner == null || owner.equals(player.getUUID());
    }

    public static boolean canUsePower(ItemStack stack, Player player) {
        if (player == null) return false;
        BatteryAccess access = getAccess(stack);
        if (access == BatteryAccess.PUBLIC) return true;
        UUID owner = getOwnerUUID(stack);
        if (owner == null || owner.equals(player.getUUID()) || isTrusted(stack, player.getUUID())) return true;
        if (access == BatteryAccess.TEAM
                && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.server.level.ServerPlayer po = sp.server.getPlayerList().getPlayer(owner);
            if (po != null && po.getTeam() != null && po.getTeam() == sp.getTeam()) return true;
        }
        return false;
    }

    public static Map<UUID, String> getTrustedPlayers(ItemStack stack) {
        Map<UUID, String> ret = new LinkedHashMap<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TRUSTED, Tag.TAG_COMPOUND)) return ret;
        CompoundTag trusted = tag.getCompound(TRUSTED);
        for (String key : trusted.getAllKeys()) {
            UUID uuid = parseUUID(key);
            if (uuid != null) ret.put(uuid, trusted.getString(key));
        }
        return ret;
    }

    public static void setTrustedPlayers(ItemStack stack, Map<UUID, String> trustedPlayers) {
        CompoundTag trusted = new CompoundTag();
        if (trustedPlayers != null) {
            for (Map.Entry<UUID, String> entry : trustedPlayers.entrySet()) {
                if (entry.getKey() != null) trusted.putString(entry.getKey().toString(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        stack.getOrCreateTag().put(TRUSTED, trusted);
    }

    public static boolean isTrusted(ItemStack stack, UUID uuid) {
        if (uuid == null) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TRUSTED, Tag.TAG_COMPOUND)) return false;
        return tag.getCompound(TRUSTED).contains(uuid.toString());
    }

    public static void addTrusted(ItemStack stack, Player player) {
        if (player == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag trusted = tag.contains(TRUSTED, Tag.TAG_COMPOUND) ? tag.getCompound(TRUSTED) : new CompoundTag();
        trusted.putString(player.getUUID().toString(), player.getGameProfile().getName());
        tag.put(TRUSTED, trusted);
    }

    public static void removeTrusted(ItemStack stack, UUID uuid) {
        if (uuid == null) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TRUSTED, Tag.TAG_COMPOUND)) return;
        CompoundTag trusted = tag.getCompound(TRUSTED);
        trusted.remove(uuid.toString());
        tag.put(TRUSTED, trusted);
    }

    public static String trustedListDisplay(ItemStack stack) {
        Map<UUID, String> trusted = getTrustedPlayers(stack);
        if (trusted.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (String name : trusted.values()) {
            if (sb.length() > 0) sb.append("、");
            sb.append(name == null || name.isBlank() ? "未知玩家" : name);
        }
        return sb.toString();
    }

    public static int clampToForgeInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    private static UUID parseUUID(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }
}
