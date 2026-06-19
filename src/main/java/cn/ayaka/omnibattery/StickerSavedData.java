package cn.ayaka.omnibattery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class StickerSavedData extends SavedData {
    private static final String DATA_NAME = "omnibattery_stickers";
    private final Map<BlockPos, StickerMode> stickers = new HashMap<>();

    public StickerSavedData() {}

    public static StickerSavedData load(CompoundTag tag) {
        StickerSavedData data = new StickerSavedData();
        ListTag list = tag.getList("stickers", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            int modeIdx = entry.getInt("mode");
            if (modeIdx >= 0 && modeIdx < StickerMode.values().length) {
                data.stickers.put(pos.immutable(), StickerMode.values()[modeIdx]);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, StickerMode> entry : stickers.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.getKey().getX());
            entryTag.putInt("y", entry.getKey().getY());
            entryTag.putInt("z", entry.getKey().getZ());
            entryTag.putInt("mode", entry.getValue().ordinal());
            list.add(entryTag);
        }
        tag.put("stickers", list);
        return tag;
    }

    public StickerMode getMode(BlockPos pos) {
        return stickers.get(pos);
    }

    public void setMode(BlockPos pos, StickerMode mode) {
        if (mode == null) {
            stickers.remove(pos);
        } else {
            stickers.put(pos.immutable(), mode);
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
}
