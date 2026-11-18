package ru.geyserevent.schematic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * WorldEdit / FAWE Sponge schematic v2 (.schem).
 */
public final class SpongeSchematicReader {

    public record LoadedBlock(int dx, int dy, int dz, Material material, String data) {
    }

    public record Result(List<LoadedBlock> blocks, int width, int height, int length,
                         int originX, int originY, int originZ) {
    }

    private SpongeSchematicReader() {
    }

    public static Result read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return read(in);
        }
    }

    public static Result read(InputStream in) throws IOException {
        byte[] raw = in.readAllBytes();
        NbtMap root;
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(raw))))) {
            root = NbtMap.readNamed(data);
        } catch (IOException gzipFailed) {
            try (DataInputStream data = new DataInputStream(new BufferedInputStream(
                    new ByteArrayInputStream(raw)))) {
                root = NbtMap.readNamed(data);
            }
        }
        NbtMap schematic = root.containsKey("Width") ? root : root.child("Schematic");
        if (schematic == null) {
            throw new IOException("Не Sponge schematic");
        }
        int width = schematic.getShort("Width");
        int height = schematic.getShort("Height");
        int length = schematic.getShort("Length");
        NbtMap palette = schematic.child("Palette");
        if (palette == null) {
            throw new IOException("Нет Palette");
        }
        Map<Integer, String> byId = new HashMap<>();
        for (Map.Entry<String, Object> entry : palette.values.entrySet()) {
            byId.put(toInt(entry.getValue()), entry.getKey());
        }
        byte[] blockData = schematic.getByteArray("BlockData");
        int[] ids = readVarInts(blockData, width * height * length);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int giftX = Integer.MIN_VALUE;
        int giftY = Integer.MIN_VALUE;
        int giftZ = Integer.MIN_VALUE;
        List<int[]> solids = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String name = byId.getOrDefault(ids[i], "minecraft:air");
            if (name.startsWith("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")) {
                continue;
            }
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);
            solids.add(new int[]{x, y, z, ids[i]});
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            if (name.startsWith("minecraft:red_shulker_box") && name.contains("facing=up")) {
                giftX = x;
                giftY = y;
                giftZ = z;
            }
        }
        if (giftX == Integer.MIN_VALUE) {
            for (int[] cell : solids) {
                String name = byId.getOrDefault(cell[3], "");
                if (name.startsWith("minecraft:red_shulker_box")) {
                    giftX = cell[0];
                    giftY = cell[1];
                    giftZ = cell[2];
                    break;
                }
            }
        }
        if (giftX == Integer.MIN_VALUE) {
            giftX = width / 2;
            giftY = minY == Integer.MAX_VALUE ? 0 : minY;
            giftZ = length / 2;
        }
        if (minY == Integer.MAX_VALUE) {
            minY = 0;
        }

        List<LoadedBlock> blocks = new ArrayList<>(solids.size());
        for (int[] cell : solids) {
            int x = cell[0];
            int y = cell[1];
            int z = cell[2];
            String state = byId.get(cell[3]);
            Material material = materialOf(state);
            if (material == null || material.isAir()) {
                continue;
            }
            int dx = x - giftX;
            int dy = y - minY + 1;
            int dz = z - giftZ;
            blocks.add(new LoadedBlock(dx, dy, dz, material, state));
        }
        return new Result(blocks, width, height, length, giftX, minY, giftZ);
    }

    public static BlockData blockDataOf(String state, Material fallback) {
        if (state != null && !state.isEmpty()) {
            try {
                return Bukkit.createBlockData(state);
            } catch (IllegalArgumentException ignored) {
                int bracket = state.indexOf('[');
                if (bracket > 0) {
                    try {
                        return Bukkit.createBlockData(state.substring(0, bracket));
                    } catch (IllegalArgumentException ignoredAgain) {
                    }
                }
            }
        }
        return fallback.createBlockData();
    }

    private static Material materialOf(String state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        String id = state;
        int bracket = id.indexOf('[');
        if (bracket > 0) {
            id = id.substring(0, bracket);
        }
        Material mat = Material.matchMaterial(id);
        if (mat != null) {
            return mat;
        }
        if (id.startsWith("minecraft:")) {
            return Material.matchMaterial(id.substring("minecraft:".length()));
        }
        return null;
    }

    private static int[] readVarInts(byte[] data, int count) {
        int[] out = new int[count];
        int i = 0;
        int n = 0;
        while (i < data.length && n < count) {
            int value = 0;
            int shift = 0;
            while (i < data.length) {
                int b = data[i++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            out[n++] = value;
        }
        return out;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static final class NbtMap {
        private final Map<String, Object> values = new HashMap<>();

        boolean containsKey(String key) {
            return values.containsKey(key);
        }

        NbtMap child(String key) {
            Object value = values.get(key);
            return value instanceof NbtMap map ? map : null;
        }

        int getShort(String key) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            return 0;
        }

        byte[] getByteArray(String key) {
            Object value = values.get(key);
            return value instanceof byte[] bytes ? bytes : new byte[0];
        }

        static NbtMap readNamed(DataInputStream in) throws IOException {
            int type = in.readByte();
            if (type != 10) {
                throw new IOException("Корень schematic не compound");
            }
            readString(in);
            return readCompound(in);
        }

        private static NbtMap readCompound(DataInputStream in) throws IOException {
            NbtMap map = new NbtMap();
            while (true) {
                int type = in.readByte();
                if (type == 0) {
                    return map;
                }
                String name = readString(in);
                map.values.put(name, readPayload(in, type));
            }
        }

        private static Object readPayload(DataInputStream in, int type) throws IOException {
            return switch (type) {
                case 1 -> in.readByte();
                case 2 -> in.readShort();
                case 3 -> in.readInt();
                case 4 -> in.readLong();
                case 5 -> in.readFloat();
                case 6 -> in.readDouble();
                case 7 -> {
                    int n = in.readInt();
                    byte[] bytes = new byte[Math.max(0, n)];
                    in.readFully(bytes);
                    yield bytes;
                }
                case 8 -> readString(in);
                case 9 -> {
                    int et = in.readByte();
                    int n = in.readInt();
                    List<Object> list = new ArrayList<>(Math.max(0, n));
                    for (int i = 0; i < n; i++) {
                        list.add(readPayload(in, et));
                    }
                    yield list;
                }
                case 10 -> readCompound(in);
                case 11 -> {
                    int n = in.readInt();
                    int[] arr = new int[Math.max(0, n)];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = in.readInt();
                    }
                    yield arr;
                }
                case 12 -> {
                    int n = in.readInt();
                    long[] arr = new long[Math.max(0, n)];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = in.readLong();
                    }
                    yield arr;
                }
                default -> throw new IOException("Неизвестный NBT tag " + type);
            };
        }

        private static String readString(DataInputStream in) throws IOException {
            int len = in.readUnsignedShort();
            byte[] bytes = new byte[len];
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
