package cn.ayaka.omnibattery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StickerSavedData extends SavedData {
    private static final String DATA_NAME = "omnibattery_stickers";
    private final Map<BlockPos, StickerEntry> stickers = new HashMap<>();

    public StickerSavedData() {}

    public static StickerSavedData load(CompoundTag tag) {
        StickerSavedData data = new StickerSavedData();
        ListTag list = tag.getList("stickers", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            int modeIdx = entry.getInt("mode");
            if (modeIdx >= 0 && modeIdx < StickerMode.values().length) {
                UUID owner = parseUUID(entry.getString("owner"));
                String ownerName = entry.getString("ownerName");
                data.stickers.put(pos.immutable(), new StickerEntry(StickerMode.values()[modeIdx], owner, ownerName,
                        entry.getLong("customCap")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, StickerEntry> entry : stickers.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.getKey().getX());
            entryTag.putInt("y", entry.getKey().getY());
            entryTag.putInt("z", entry.getKey().getZ());
            entryTag.putInt("mode", entry.getValue().mode().ordinal());
            if (entry.getValue().owner() != null) entryTag.putString("owner", entry.getValue().owner().toString());
            if (entry.getValue().ownerName() != null) entryTag.putString("ownerName", entry.getValue().ownerName());
            list.add(entryTag);
        }
        tag.put("stickers", list);
        return tag;
    }

    public StickerMode getMode(BlockPos pos) {
        StickerEntry entry = stickers.get(pos);
        return entry == null ? null : entry.mode();
    }

    /** 所有已打标签的机器坐标（供用电配置界面枚举）。 */
    public java.util.Set<BlockPos> positions() {
        return new java.util.HashSet<>(stickers.keySet());
    }

    public StickerEntry getEntry(BlockPos pos) {
        return stickers.get(pos);
    }

    public void setMode(BlockPos pos, StickerMode mode) {
        setMode(pos, mode, null, "");
    }

    public void setMode(BlockPos pos, StickerMode mode, UUID owner, String ownerName) {
        setMode(pos, mode, owner, ownerName, 0L);
    }

    /** 带自定义容量上限的写入（customCap 仅 CUSTOM 模式有意义）。 */
    public void setMode(BlockPos pos, StickerMode mode, UUID owner, String ownerName, long customCap) {
        if (mode == null) {
            stickers.remove(pos);
        } else {
            StickerEntry old = stickers.get(pos);
            long cap = customCap > 0L ? customCap : (old != null ? old.customCap() : 0L);
            stickers.put(pos.immutable(), new StickerEntry(mode, owner, ownerName == null ? "" : ownerName, cap));
        }
        setDirty();
    }

    public void removeSticker(BlockPos pos) {
        stickers.remove(pos);
        setDirty();
    }

    public static StickerSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StickerSavedData::load, StickerSavedData::new, DATA_NAME);
    }

    private static UUID parseUUID(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    public record StickerEntry(StickerMode mode, UUID owner, String ownerName, long customCap) {}
}
