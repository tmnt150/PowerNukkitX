package cn.nukkit.level.format.anvil;

import cn.nukkit.api.*;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.ChunkSection3DBiome;
import cn.nukkit.level.format.DimensionDataProvider;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.level.format.generic.BaseRegionLoader;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ChunkException;
import cn.nukkit.utils.ThreadCache;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * @author MagicDroidX (Nukkit Project)
 */
@Log4j2
public class Anvil extends BaseLevelProvider implements DimensionDataProvider {
    @PowerNukkitDifference(info = "pre-1.17 old chunk version", since = "1.6.0.0-PNX")
    public static final int OLD_VERSION = 19133;
    @PowerNukkitDifference(info = "1.18 new chunk support version", since = "1.6.0.0-PNX")
    public static final int VERSION = 19134;
    @PowerNukkitXOnly
    @Since("1.6.0.0-PNX")
    public static final int LOWER_PADDING_SIZE = 4;
    static private final byte[] PAD_256 = new byte[256];
    @PowerNukkitXOnly
    @Since("1.6.0.0-PNX")
    private final boolean isOldAnvil;
    @PowerNukkitXOnly
    @Since("1.19.20-r3")
    private DimensionData dimensionData;

    public Anvil(Level level, String path) throws IOException {
        super(level, path);
        isOldAnvil = getLevelData().getInt("version") == OLD_VERSION;
        if (getLevelData().contains("dimensionData")) {
            var dimNBT = getLevelData().getCompound("dimensionData");
            int chunkSectionCount;
            dimensionData = new DimensionData(dimNBT.getString("dimensionName"), dimNBT.getInt("dimensionId"), dimNBT.getInt("minHeight"), dimNBT.getInt("maxHeight"), (chunkSectionCount = dimNBT.getInt("chunkSectionCount")) != 0 ? chunkSectionCount : null);
        }
        getLevelData().putInt("version", VERSION);
    }

    public static String getProviderName() {
        return "anvil";
    }

    public static byte getProviderOrder() {
        return ORDER_YZX;
    }

    public static boolean usesChunkSection() {
        return true;
    }

    public boolean isOldAnvil() {
        return isOldAnvil;
    }

    public static boolean isValid(String path) {
        boolean isValid = (new File(path + "/level.dat").exists()) && new File(path + "/region/").isDirectory();
        if (isValid) {
            for (File file : Objects.requireNonNull(
                    new File(path + "/region/").listFiles((dir, name) -> Pattern.matches("^.+\\.mc[r|a]$", name)))) {
                if (!file.getName().endsWith(".mca")) {
                    isValid = false;
                    break;
                }
            }
        }
        return isValid;
    }

    @UsedByReflection
    public static void generate(String path, String name, long seed, Class<? extends Generator> generator) throws IOException {
        generate(path, name, seed, generator, new HashMap<>());
    }

    @UsedByReflection
    @PowerNukkitDifference(since = "1.4.0.0-PN", info = "Fixed resource leak")
    public static void generate(String path, String name, long seed, Class<? extends Generator> generator, Map<String, String> options) throws IOException {
        File regionDir = new File(path + "/region");
        if (!regionDir.exists() && !regionDir.mkdirs()) {
            throw new IOException("Could not create the directory " + regionDir);
        }

        CompoundTag levelData = new CompoundTag("Data")
                .putCompound("GameRules", new CompoundTag())

                .putLong("DayTime", 0)
                .putInt("GameType", 0)
                .putString("generatorName", Generator.getGeneratorName(generator))
                .putString("generatorOptions", options.getOrDefault("preset", ""))
                .putInt("generatorVersion", 1)
                .putBoolean("hardcore", false)
                .putBoolean("initialized", true)
                .putLong("LastPlayed", System.currentTimeMillis() / 1000)
                .putString("LevelName", name)
                .putBoolean("raining", false)
                .putInt("rainTime", 0)
                .putLong("RandomSeed", seed)
                .putInt("SpawnX", 128)
                .putInt("SpawnY", 70)
                .putInt("SpawnZ", 128)
                .putBoolean("thundering", false)
                .putInt("thunderTime", 0)
                .putInt("version", VERSION)
                .putLong("Time", 0)
                .putLong("SizeOnDisk", 0);

        Utils.safeWrite(new File(path, "level.dat"), file -> {
            try (FileOutputStream fos = new FileOutputStream(file); BufferedOutputStream out = new BufferedOutputStream(fos)) {
                NBTIO.writeGZIPCompressed(new CompoundTag().putCompound("Data", levelData), out, ByteOrder.BIG_ENDIAN);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public Chunk getEmptyChunk(int chunkX, int chunkZ) {
        return Chunk.getEmptyChunk(chunkX, chunkZ, this);
    }

    @Override
    public AsyncTask requestChunkTask(int x, int z) throws ChunkException {
        Chunk chunk = (Chunk) this.getChunk(x, z, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk Set");
        }

        long timestamp = chunk.getChanges();

        BiConsumer<BinaryStream, Integer> callback = (stream, subchunks) ->
                this.getLevel().chunkRequestCallback(timestamp, x, z, subchunks, stream.getBuffer());
        serialize(chunk, callback, this.level.getDimensionData());

        return null;
    }

    @PowerNukkitXDifference(info = "Non-static")
    public final void serialize(BaseChunk chunk, BiConsumer<BinaryStream, Integer> callback, DimensionData dimensionData) {
        byte[] blockEntities;
        if (chunk.getBlockEntities().isEmpty()) {
            blockEntities = new byte[0];
        } else {
            blockEntities = serializeEntities(chunk);
        }

        int subChunkCount = 0;
        cn.nukkit.level.format.ChunkSection[] sections = chunk.getSections();
        for (int i = sections.length - 1; i >= 0; i--) {
            if (!sections[i].isEmpty()) {
                subChunkCount = i + 1;
                break;
            }
        }

        int maxDimensionSections = dimensionData.getHeight() >> 4;
        subChunkCount = Math.min(maxDimensionSections, subChunkCount);

        byte[] biomePalettes = serializeBiomes(chunk, maxDimensionSections);
        BinaryStream stream = ThreadCache.binaryStream.get().reset();

        int writtenSections = subChunkCount;

        final var tmpSubChunkStreams = new BinaryStream[subChunkCount];
        for (int i = 0; i < subChunkCount; i++) { // 确保全部在主线程上分配
            tmpSubChunkStreams[i] = new BinaryStream(new byte[8192]).reset(); // 8KB
        }
        if (level != null && level.isAntiXrayEnabled()) {
            IntStream.range(0, subChunkCount).parallel().forEach(i -> sections[i].writeObfuscatedTo(tmpSubChunkStreams[i], level));
        } else {
            IntStream.range(0, subChunkCount).parallel().forEach(i -> sections[i].writeTo(tmpSubChunkStreams[i]));
        }
        for (int i = 0; i < subChunkCount; i++) {
            stream.put(tmpSubChunkStreams[i].getBuffer());
        }

        stream.put(biomePalettes);
        stream.putByte((byte) 0); // Border blocks
        stream.put(blockEntities);
        callback.accept(stream, writtenSections);
    }

    private static byte[] serializeEntities(BaseChunk chunk) {
        List<CompoundTag> tagList = new ObjectArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntitySpawnable) {
                tagList.add(((BlockEntitySpawnable) blockEntity).getSpawnCompound());
            }
        }
        try {
            return NBTIO.write(tagList, ByteOrder.LITTLE_ENDIAN, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] serializeBiomes(BaseFullChunk chunk, int sectionCount) {
        var stream = ThreadCache.binaryStream.get().reset();
        if (chunk instanceof cn.nukkit.level.format.Chunk sectionChunk && sectionChunk.isChunkSection3DBiomeSupported()) {
            var sections = sectionChunk.getSections();
            var len = Math.min(sections.length, sectionCount);
            final var tmpSectionBiomeStream = new BinaryStream[len];
            for (int i = 0; i < len; i++) { // 确保全部在主线程上分配
                tmpSectionBiomeStream[i] = new BinaryStream(new byte[4096 + 1024]).reset(); // 5KB
            }
            IntStream.range(0, len).parallel().forEach(i -> {
                if (sections[i] instanceof ChunkSection3DBiome each) {
                    var palette = PalettedBlockStorage.createWithDefaultState(Biome.getBiomeIdOrCorrect(chunk.getBiomeId(0, 0) & 0xFF));
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                var tmpBiome = Biome.getBiomeIdOrCorrect(each.getBiomeId(x, y, z) & 0xFF);
                                palette.setBlock(x, y, z, tmpBiome);
                            }
                        }
                    }
                    palette.writeTo(tmpSectionBiomeStream[i]);
                } else {
                    var palette = PalettedBlockStorage.createWithDefaultState(Biome.getBiomeIdOrCorrect(chunk.getBiomeId(0, 0) & 0xFF));
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int biomeId = Biome.getBiomeIdOrCorrect(chunk.getBiomeId(x, z) & 0xFF);
                            for (int y = 0; y < 16; y++) {
                                palette.setBlock(x, y, z, biomeId);
                            }
                        }
                    }
                    palette.writeTo(tmpSectionBiomeStream[i]);
                }
            });
            for (int i = 0; i < len; i++) {
                stream.put(tmpSectionBiomeStream[i].getBuffer());
            }
        } else {
            PalettedBlockStorage palette = PalettedBlockStorage.createWithDefaultState(Biome.getBiomeIdOrCorrect(chunk.getBiomeId(0, 0) & 0xFF));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int biomeId = Biome.getBiomeIdOrCorrect(chunk.getBiomeId(x, z));
                    for (int y = 0; y < 16; y++) {
                        palette.setBlock(x, y, z, biomeId);
                    }
                }
            }

            palette.writeTo(stream);
            byte[] bytes = stream.getBuffer();
            stream.reset();

            for (int i = 0; i < sectionCount; i++) {
                stream.put(bytes);
            }
        }
        return stream.getBuffer();
    }

    private int lastPosition = 0;

    @Override
    public void doGarbageCollection(long time) {
        long start = System.currentTimeMillis();
        int maxIterations = size();
        if (lastPosition > maxIterations) lastPosition = 0;
        int i;
        synchronized (chunks) {
            var iter = chunks.values().iterator();
            if (lastPosition != 0) {
                var tmpI = lastPosition;
                while (tmpI-- != 0 && iter.hasNext()) iter.next();
            }
            for (i = 0; i < maxIterations; i++) {
                if (!iter.hasNext()) {
                    iter = chunks.values().iterator();
                }
                if (!iter.hasNext()) break;
                BaseFullChunk chunk = iter.next();
                if (chunk == null) continue;
                if (chunk.isGenerated() && chunk.isPopulated() && chunk instanceof Chunk) {
                    chunk.compress();
                    if (System.currentTimeMillis() - start >= time) break;
                }
            }
        }
        lastPosition += i;
    }

    @Override
    public synchronized BaseFullChunk loadChunk(long index, int chunkX, int chunkZ, boolean create) {
        int regionX = getRegionIndexX(chunkX);
        int regionZ = getRegionIndexZ(chunkZ);
        BaseRegionLoader region = this.loadRegion(regionX, regionZ);
        this.level.timings.syncChunkLoadDataTimer.startTiming();
        BaseFullChunk chunk;
        try {
            chunk = region.readChunk(chunkX - regionX * 32, chunkZ - regionZ * 32);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (chunk == null) {
            if (create) {
                chunk = this.getEmptyChunk(chunkX, chunkZ);
                putChunk(index, chunk);
            }
        } else {
            putChunk(index, chunk);
        }
        this.level.timings.syncChunkLoadDataTimer.stopTiming();
        return chunk;
    }

    @Override
    public synchronized void saveChunk(int X, int Z) {
        BaseFullChunk chunk = this.getChunk(X, Z);
        if (chunk != null) {
            try {
                this.loadRegion(X >> 5, Z >> 5).writeChunk(chunk);
            } catch (Exception e) {
                throw new ChunkException("Error saving chunk (" + X + ", " + Z + ")", e);
            }
        }
    }


    @Override
    public synchronized void saveChunk(int x, int z, FullChunk chunk) {
        if (!(chunk instanceof Chunk)) {
            throw new ChunkException("Invalid Chunk class");
        }
        int regionX = x >> 5;
        int regionZ = z >> 5;
        this.loadRegion(regionX, regionZ);
        chunk.setX(x);
        chunk.setZ(z);
        try {
            this.getRegion(regionX, regionZ).writeChunk(chunk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @UsedByReflection
    public static ChunkSection createChunkSection(int y) {
        ChunkSection cs = new ChunkSection(y);
        cs.hasSkyLight = true;
        return cs;
    }

    protected synchronized BaseRegionLoader loadRegion(int x, int z) {
        BaseRegionLoader tmp = lastRegion.get();
        if (tmp != null && x == tmp.getX() && z == tmp.getZ()) {
            return tmp;
        }
        long index = Level.chunkHash(x, z);
        synchronized (regions) {
            BaseRegionLoader region = this.regions.get(index);
            if (region == null) {
                try {
                    region = new RegionLoader(this, x, z);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                this.regions.put(index, region);
            }
            lastRegion.set(region);
            return region;
        }
    }

    @PowerNukkitOnly
    @Override
    public int getMaximumLayer() {
        return 1;
    }

    @Nullable
    @Override
    public DimensionData getDimensionData() {
        return dimensionData;
    }

    @Override
    public void setDimensionData(DimensionData dimensionData) {
        this.dimensionData = dimensionData;
        if (dimensionData != null) {
            levelData.putCompound("dimensionData", new CompoundTag("dimensionData")
                    .putString("dimensionName", dimensionData.getDimensionName())
                    .putInt("dimensionId", dimensionData.getDimensionId())
                    .putInt("maxHeight", dimensionData.getMaxHeight())
                    .putInt("minHeight", dimensionData.getMinHeight())
                    .putInt("chunkSectionCount", dimensionData.getChunkSectionCount()));
        }
    }
}
