package com.eisiadev.gregoryminer.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public final class MinerArea {

    public enum Unit {
        CHUNK,
        BLOCK
    }

    public final int minX, minZ, maxX, maxZ;

    private MinerArea(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public static MinerArea of(BlockPos origin, Unit unit, int west, int east, int north, int south) {
        if (unit == Unit.CHUNK) {
            int cx = SectionPos.blockToSectionCoord(origin.getX());
            int cz = SectionPos.blockToSectionCoord(origin.getZ());
            return new MinerArea(
                    SectionPos.sectionToBlockCoord(cx - west),
                    SectionPos.sectionToBlockCoord(cz - north),
                    SectionPos.sectionToBlockCoord(cx + east) + 15,
                    SectionPos.sectionToBlockCoord(cz + south) + 15);
        }
        return new MinerArea(origin.getX() - west, origin.getZ() - north, origin.getX() + east, origin.getZ() + south);
    }

    public static MinerArea ofBounds(int minX, int minZ, int maxX, int maxZ) {
        return new MinerArea(Math.min(minX, maxX), Math.min(minZ, maxZ), Math.max(minX, maxX), Math.max(minZ, maxZ));
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public int totalColumns() {
        return width() * depth();
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public long head() {
        return pack(minX, minZ);
    }

    public long next(int x, int z, boolean chunkByChunk) {
        if (!chunkByChunk) {
            if (x + 1 <= maxX) return pack(x + 1, z);
            if (z + 1 <= maxZ) return pack(minX, z + 1);
            return Long.MIN_VALUE;
        }
        int cx = SectionPos.blockToSectionCoord(x);
        int cz = SectionPos.blockToSectionCoord(z);
        if (x + 1 <= Math.min(SectionPos.sectionToBlockCoord(cx) + 15, maxX)) return pack(x + 1, z);
        if (z + 1 <= Math.min(SectionPos.sectionToBlockCoord(cz) + 15, maxZ)) {
            return pack(Math.max(SectionPos.sectionToBlockCoord(cx), minX), z + 1);
        }
        if (cx + 1 <= SectionPos.blockToSectionCoord(maxX)) {
            return pack(Math.max(SectionPos.sectionToBlockCoord(cx + 1), minX),
                    Math.max(SectionPos.sectionToBlockCoord(cz), minZ));
        }
        if (cz + 1 <= SectionPos.blockToSectionCoord(maxZ)) {
            return pack(minX, Math.max(SectionPos.sectionToBlockCoord(cz + 1), minZ));
        }
        return Long.MIN_VALUE;
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }
}
