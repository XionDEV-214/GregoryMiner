package com.eisiadev.gregoryminer.machine;

import com.eisiadev.gregoryminer.GregoryMiner;
import com.eisiadev.gregoryminer.client.AreaRenderRegistry;
import com.eisiadev.gregoryminer.config.GMConfig;
import com.eisiadev.gregoryminer.machine.ui.GregoryMinerUI;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.items.IItemHandler;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GregoryMinerMachine extends TieredEnergyMachine
                                 implements IControllable, IFancyUIMachine, IAutoOutputItem, IMachineLife,
                                 IDropSaveMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            GregoryMinerMachine.class, TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE |
            Block.UPDATE_SUPPRESS_DROPS;
    private static final int MAX_FREE_SKIPS = 64;
    private static final int OUTPUT_BACKOFF_TICKS = 20;
    public static final GameProfile FAKE_PROFILE = new GameProfile(
            UUID.fromString("6e0c5c7e-5b2b-4b9f-9a57-6772656f7279"), "[GregoryMiner]");

    @Persisted
    @DescSynced
    protected boolean workingEnabled = true;
    @Persisted
    @DescSynced
    @RequireRerender
    @Nullable
    protected Direction outputFacingItems;
    @Persisted
    @DescSynced
    @RequireRerender
    protected boolean autoOutputItems = true;
    @Persisted
    protected boolean allowInputFromOutputSideItems = false;

    @Persisted
    @DropSaved
    @DescSynced
    protected MinerArea.Unit areaUnit = MinerArea.Unit.CHUNK;
    @Persisted
    @DropSaved
    @DescSynced
    protected int extWest, extEast, extNorth, extSouth;
    @Persisted
    @DropSaved
    @DescSynced
    protected boolean chunkByChunk = true;
    @Persisted
    @DropSaved
    @DescSynced
    protected boolean showArea = false;

    @Persisted
    protected final CustomItemStackHandler toolInventory;
    @Persisted
    @DropSaved
    protected final CustomItemStackHandler filterInventory;
    protected final ItemBuffer buffer = new ItemBuffer();

    @Persisted
    @DescSynced
    protected int cursorX, cursorZ;
    @Persisted
    protected boolean cursorValid = false;
    @Persisted
    @DescSynced
    protected boolean done = false;
    @Persisted
    @DescSynced
    protected int columnsDone = 0;
    @Persisted
    @DescSynced
    protected long blocksMinedTotal = 0;
    @Persisted
    @DescSynced
    protected long itemsKeptTotal = 0, itemsVoidedTotal = 0, itemsOutputTotal = 0;

    @DescSynced
    protected int areaMinX, areaMinZ, areaMaxX, areaMaxZ;
    @DescSynced
    protected int areaTopY, areaBottomY;
    @DescSynced
    protected MiningStatus status = MiningStatus.IDLE;
    @DescSynced
    protected boolean outputMissing = false;
    @DescSynced
    protected boolean outputFull = false;
    @DescSynced
    protected int fortuneLevel = 0, silkLevel = 0;
    @DescSynced
    protected boolean toolValid = false, toolPresent = false;
    @DescSynced
    protected long bufferUsed = 0;
    @DescSynced
    protected int bufferKinds = 0;
    @DescSynced
    protected int whitelistSize = 0;
    @DescSynced
    protected int lastColumnBlocks = 0;
    @DescSynced
    protected int syncInterval = 1, syncBufferCap = 1;
    @DescSynced
    protected long syncEuPerColumn = 0;

    @Nullable
    private MinerArea area;
    @Nullable
    private TickableSubscription tickSubscription;
    private long nextOutputTick = 0;
    private final Set<Item> whitelist = new ReferenceOpenHashSet<>();
    @Nullable
    private GameProfile ownerProfile;

    private final LongArrayList scanPos = new LongArrayList(64);
    private final ByteArrayList scanKind = new ByteArrayList(64);
    private final ObjectArrayList<BlockState> scanState = new ObjectArrayList<>(64);
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

    public GregoryMinerMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
        this.toolInventory = new CustomItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        this.toolInventory.setFilter(MinerTools::isValidTool);
        this.toolInventory.setOnContentsChanged(this::onToolChanged);

        int side = filterGridSize(tier);
        this.filterInventory = new CustomItemStackHandler(side * side) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        this.filterInventory.setOnContentsChanged(this::rebuildWhitelist);
    }

    public static int filterGridSize(int tier) {
        return Math.max(2, Math.min(6, tier + 1));
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return GMConfig.amperage();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) {
            AreaRenderRegistry.add(this);
            return;
        }
        if (outputFacingItems == null || outputFacingItems == getFrontFacing()) {
            outputFacingItems = getFrontFacing().getOpposite();
        }
        recomputeArea();
        onToolChanged();
        rebuildWhitelist();
        refreshConfigSnapshot();
        updateBufferStats();
        updateSubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (isRemote()) {
            AreaRenderRegistry.remove(this);
        }
        tickSubscription = null;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(toolInventory);
    }

    @Override
    public boolean savePickClone() {
        return false;
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        IDropSaveMachine.super.loadFromItem(tag);
        resetProgress();
        rebuildWhitelist();
    }

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (!buffer.isEmpty()) tag.put("GMBuffer", buffer.save());
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("GMBuffer", Tag.TAG_LIST)) {
            buffer.load(tag.getList("GMBuffer", Tag.TAG_COMPOUND));
        } else {
            buffer.clear();
        }
        updateBufferStats();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        nextOutputTick = 0;
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        if (outputFacingItems == newFacing) {
            outputFacingItems = newFacing.getOpposite();
        }
        nextOutputTick = 0;
    }

    protected void updateSubscription() {
        if (isRemote()) return;
        boolean needed = (workingEnabled && !done) || !buffer.isEmpty();
        if (needed) {
            tickSubscription = subscribeServerTick(tickSubscription, this::serverTickMiner);
        } else if (tickSubscription != null) {
            tickSubscription.unsubscribe();
            tickSubscription = null;
        }
        refreshStatus();
    }

    private void serverTickMiner() {
        if (!(getLevel() instanceof ServerLevel level)) return;
        long now = getOffsetTimer();

        if (!buffer.isEmpty() && now >= nextOutputTick) {
            pushOutput(level, now);
        }

        if (!workingEnabled || done) {
            if (buffer.isEmpty()) updateSubscription();
            return;
        }

        if (now % GMConfig.interval(tier) != 0) return;
        doOperation(level);
    }

    private void doOperation(ServerLevel level) {
        if (buffer.usedStacks() >= GMConfig.bufferStacks()) {
            setStatus(MiningStatus.BUFFER_FULL);
            return;
        }
        refreshConfigSnapshot();
        long cost = Math.min(syncEuPerColumn, energyContainer.getEnergyCapacity());
        if (energyContainer.getEnergyStored() < cost) {
            setStatus(MiningStatus.NO_POWER);
            return;
        }
        MinerArea area = getArea();
        if (!cursorValid) {
            long head = area.head();
            cursorX = MinerArea.unpackX(head);
            cursorZ = MinerArea.unpackZ(head);
            cursorValid = true;
        }

        for (int skips = 0; skips <= MAX_FREE_SKIPS; skips++) {
            ColumnResult r = mineOneColumn(level, cursorX, cursorZ, cost);
            switch (r) {
                case WAIT_CHUNK -> {
                    setStatus(MiningStatus.CHUNK_NOT_LOADED);
                    return;
                }
                case MINED -> {
                    advanceCursor(area);
                    if (!done) setStatus(MiningStatus.WORKING);
                    if (!buffer.isEmpty()) pushOutput(level, getOffsetTimer());
                    markDirty();
                    return;
                }
                case EMPTY -> {
                    advanceCursor(area);
                    if (done) return;
                }
            }
        }
        setStatus(MiningStatus.WORKING);
        markDirty();
    }

    private enum ColumnResult {
        MINED,
        EMPTY,
        WAIT_CHUNK
    }

    private ColumnResult mineOneColumn(ServerLevel level, int x, int z, long cost) {
        int cx = SectionPos.blockToSectionCoord(x);
        int cz = SectionPos.blockToSectionCoord(z);
        if (!level.hasChunk(cx, cz) && !GMConfig.loadUnloadedChunks()) return ColumnResult.WAIT_CHUNK;
        LevelChunk chunk = level.getChunk(cx, cz);

        int top = getPos().getY() - 1;
        int bottom = level.getMinBuildHeight();
        int lx = x & 15, lz = z & 15;
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos mpos = scratchPos;

        scanPos.clear();
        scanKind.clear();
        scanState.clear();

        int y = Math.min(top, level.getMaxBuildHeight() - 1);
        while (y >= bottom) {
            int sectionIndex = chunk.getSectionIndex(y);
            LevelChunkSection section = sections[sectionIndex];
            int sectionBottom = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            if (section == null || section.hasOnlyAir()) {
                y = sectionBottom - 1;
                continue;
            }
            int stop = Math.max(sectionBottom, bottom);
            for (; y >= stop; y--) {
                BlockState state = section.getBlockState(lx, y & 15, lz);
                if (state.isAir()) continue;
                mpos.set(x, y, z);
                byte kind = BlockRules.classify(state, level, mpos);
                if (kind == BlockRules.SKIP) continue;
                scanPos.add(BlockPos.asLong(x, y, z));
                scanKind.add(kind);
                scanState.add(state);
            }
        }

        int count = scanPos.size();
        if (count == 0) {
            columnsDone++;
            return ColumnResult.EMPTY;
        }

        GMConfig.ProtectionMode protection = GMConfig.protectionMode();
        FakePlayer fakePlayer = protection == GMConfig.ProtectionMode.NONE ? null : getFakePlayer(level);
        if (protection == GMConfig.ProtectionMode.COLUMN) {
            mpos.set(scanPos.getLong(0));
            if (MinecraftForge.EVENT_BUS.post(
                    new BlockEvent.BreakEvent(level, mpos.immutable(), scanState.get(0), fakePlayer))) {
                columnsDone++;
                return ColumnResult.EMPTY;
            }
        }

        energyContainer.removeEnergy(cost);

        ItemStack tool = toolValid ? toolInventory.getStackInSlot(0) : MinerTools.fallbackTool();
        boolean filtering = !whitelist.isEmpty();
        int mined = 0;
        long kept = 0, voided = 0;

        for (int i = 0; i < count; i++) {
            mpos.set(scanPos.getLong(i));
            BlockState state = scanState.get(i);

            if (scanKind.getByte(i) == BlockRules.FLUID) {
                level.setBlock(mpos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
                mined++;
                continue;
            }
            if (protection == GMConfig.ProtectionMode.BLOCK &&
                    MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, mpos.immutable(), state, fakePlayer))) {
                continue;
            }

            LootParams.Builder loot = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(mpos))
                    .withParameter(LootContextParams.TOOL, tool);
            List<ItemStack> drops = state.getDrops(loot);
            for (int d = 0, n = drops.size(); d < n; d++) {
                ItemStack drop = drops.get(d);
                if (drop.isEmpty()) continue;
                if (filtering && !whitelist.contains(drop.getItem())) {
                    voided += drop.getCount();
                    continue;
                }
                kept += drop.getCount();
                buffer.add(drop);
            }
            level.setBlock(mpos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
            mined++;
        }

        columnsDone++;
        blocksMinedTotal += mined;
        lastColumnBlocks = mined;
        itemsKeptTotal += kept;
        itemsVoidedTotal += voided;
        updateBufferStats();
        if (GMConfig.debugLogging()) {
            GregoryMiner.LOGGER.info("[{}] column ({}, {}): {} blocks, {} items kept, {} voided by filter, " +
                    "buffer {} stacks / {} kinds", getPos(), x, z, mined, kept, voided, buffer.usedStacks(),
                    buffer.kinds());
        }
        if (!buffer.isEmpty()) updateSubscription();
        return ColumnResult.MINED;
    }

    private void advanceCursor(MinerArea area) {
        long next = area.next(cursorX, cursorZ, chunkByChunk);
        if (next == Long.MIN_VALUE) {
            done = true;
            setStatus(MiningStatus.DONE);
            updateSubscription();
        } else {
            cursorX = MinerArea.unpackX(next);
            cursorZ = MinerArea.unpackZ(next);
        }
    }

    private void pushOutput(ServerLevel level, long now) {
        IItemHandler target = autoOutputItems ? getOutputHandler(level) : null;
        boolean missing = target == null;
        if (outputMissing != missing) outputMissing = missing;
        if (target == null) {
            if (outputFull) outputFull = false;
            if (GMConfig.debugLogging()) {
                GregoryMiner.LOGGER.info("[{}] output -> {}: no item handler there", getPos(), outputFacingItems);
            }
            nextOutputTick = now + OUTPUT_BACKOFF_TICKS;
            return;
        }
        long before = buffer.totalItems();
        long moved = buffer.flushInto(target, GMConfig.outputStacksPerTick());
        itemsOutputTotal += moved;
        if (GMConfig.debugLogging()) {
            GregoryMiner.LOGGER.info("[{}] output -> {} ({}): moved {} of {} items, buffer left {}", getPos(),
                    outputFacingItems, target.getClass().getName(), moved, before, buffer.totalItems());
        }
        nextOutputTick = moved > 0 ? now + 1 : now + OUTPUT_BACKOFF_TICKS;
        boolean full = moved == 0 && !buffer.isEmpty();
        if (outputFull != full) outputFull = full;
        if (moved > 0) {
            updateBufferStats();
            markDirty();
            if (status == MiningStatus.BUFFER_FULL && buffer.usedStacks() < GMConfig.bufferStacks()) {
                setStatus(MiningStatus.WORKING);
            }
        }
        if (buffer.isEmpty()) updateSubscription();
    }

    @Nullable
    private IItemHandler getOutputHandler(ServerLevel level) {
        Direction face = outputFacingItems;
        if (face == null) return null;
        BlockEntity be = level.getBlockEntity(getPos().relative(face));
        if (be == null) return null;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite()).resolve().orElse(null);
    }

    private FakePlayer getFakePlayer(ServerLevel level) {
        if (!GMConfig.fakePlayerUsesOwnerUuid()) {
            return FakePlayerFactory.get(level, FAKE_PROFILE);
        }
        UUID owner = getOwnerUUID();
        if (owner == null) return FakePlayerFactory.get(level, FAKE_PROFILE);
        if (ownerProfile == null || !owner.equals(ownerProfile.getId())) {
            String name = FAKE_PROFILE.getName();
            GameProfileCache cache = level.getServer().getProfileCache();
            if (cache != null) {
                name = cache.get(owner).map(GameProfile::getName).orElse(name);
            }
            ownerProfile = new GameProfile(owner, name);
        }
        return FakePlayerFactory.get(level, ownerProfile);
    }

    private void onToolChanged() {
        if (isRemote()) return;
        ItemStack tool = toolInventory.getStackInSlot(0);
        toolPresent = !tool.isEmpty();
        toolValid = MinerTools.isValidTool(tool);
        fortuneLevel = toolValid ? MinerTools.fortune(tool) : 0;
        silkLevel = toolValid ? MinerTools.silkTouch(tool) : 0;
    }

    private void updateBufferStats() {
        long used = buffer.usedStacks();
        int kinds = buffer.kinds();
        if (bufferUsed != used) bufferUsed = used;
        if (bufferKinds != kinds) bufferKinds = kinds;
        if (buffer.isEmpty() && outputFull) outputFull = false;
    }

    private void refreshConfigSnapshot() {
        int interval = GMConfig.interval(tier);
        long eu = GMConfig.euPerColumn(tier);
        int cap = GMConfig.bufferStacks();
        if (syncInterval != interval) syncInterval = interval;
        if (syncEuPerColumn != eu) syncEuPerColumn = eu;
        if (syncBufferCap != cap) syncBufferCap = cap;
    }

    private void rebuildWhitelist() {
        whitelist.clear();
        for (int i = 0; i < filterInventory.getSlots(); i++) {
            ItemStack s = filterInventory.getStackInSlot(i);
            if (!s.isEmpty()) whitelist.add(s.getItem());
        }
        if (!isRemote()) whitelistSize = whitelist.size();
    }

    protected MinerArea getArea() {
        if (area == null) recomputeArea();
        return area;
    }

    protected void recomputeArea() {
        area = MinerArea.of(getPos(), areaUnit, extWest, extEast, extNorth, extSouth);
        areaMinX = area.minX;
        areaMinZ = area.minZ;
        areaMaxX = area.maxX;
        areaMaxZ = area.maxZ;
        areaTopY = getPos().getY() - 1;
        areaBottomY = getLevel() != null ? getLevel().getMinBuildHeight() : -64;
        if (cursorValid && !area.contains(cursorX, cursorZ)) cursorValid = false;
    }

    public void resetProgress() {
        if (isRemote()) return;
        recomputeArea();
        cursorValid = false;
        done = false;
        columnsDone = 0;
        long head = getArea().head();
        cursorX = MinerArea.unpackX(head);
        cursorZ = MinerArea.unpackZ(head);
        markDirty();
        updateSubscription();
    }

    private void setStatus(MiningStatus newStatus) {
        if (status != newStatus) {
            status = newStatus;
            RecipeLogic.Status render = newStatus.renderStatus;
            var renderState = getRenderState();
            if (renderState.hasProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS) &&
                    renderState.getValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS) != render) {
                setRenderState(renderState.setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS, render));
            }
        }
    }

    private void refreshStatus() {
        if (done) setStatus(MiningStatus.DONE);
        else if (!workingEnabled) setStatus(MiningStatus.PAUSED);
        else if (status == MiningStatus.IDLE || status == MiningStatus.PAUSED || status == MiningStatus.DONE) {
            setStatus(MiningStatus.WORKING);
        }
    }

    private int maxExtent() {
        int maxChunks = GMConfig.maxAreaChunks();
        return areaUnit == MinerArea.Unit.CHUNK ? maxChunks - 1 : maxChunks * 16 - 1;
    }

    private int clampExtent(int value, int opposite) {
        return Math.max(0, Math.min(value, maxExtent() - opposite));
    }

    public void setExtWest(int v) {
        if (isRemote()) return;
        int c = clampExtent(v, extEast);
        if (c != extWest) {
            extWest = c;
            resetProgress();
        }
    }

    public void setExtEast(int v) {
        if (isRemote()) return;
        int c = clampExtent(v, extWest);
        if (c != extEast) {
            extEast = c;
            resetProgress();
        }
    }

    public void setExtNorth(int v) {
        if (isRemote()) return;
        int c = clampExtent(v, extSouth);
        if (c != extNorth) {
            extNorth = c;
            resetProgress();
        }
    }

    public void setExtSouth(int v) {
        if (isRemote()) return;
        int c = clampExtent(v, extNorth);
        if (c != extSouth) {
            extSouth = c;
            resetProgress();
        }
    }

    public void toggleAreaUnit() {
        if (isRemote()) return;
        if (areaUnit == MinerArea.Unit.CHUNK) {
            areaUnit = MinerArea.Unit.BLOCK;
            MinerArea a = getArea();
            extWest = getPos().getX() - a.minX;
            extEast = a.maxX - getPos().getX();
            extNorth = getPos().getZ() - a.minZ;
            extSouth = a.maxZ - getPos().getZ();
        } else {
            areaUnit = MinerArea.Unit.CHUNK;
            extWest = extEast = extNorth = extSouth = 0;
        }
        resetProgress();
    }

    public void toggleChunkByChunk() {
        if (isRemote()) return;
        chunkByChunk = !chunkByChunk;
        resetProgress();
    }

    public void toggleShowArea() {
        if (isRemote()) return;
        showArea = !showArea;
    }

    public void clearFilter() {
        if (isRemote()) return;
        for (int i = 0; i < filterInventory.getSlots(); i++) {
            filterInventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        rebuildWhitelist();
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean enabled) {
        if (workingEnabled == enabled) return;
        workingEnabled = enabled;
        updateSubscription();
    }

    @Override
    public boolean isAutoOutputItems() {
        return autoOutputItems;
    }

    @Override
    public void setAutoOutputItems(boolean allow) {
        autoOutputItems = allow;
        nextOutputTick = 0;
        updateSubscription();
    }

    @Override
    public boolean isAllowInputFromOutputSideItems() {
        return allowInputFromOutputSideItems;
    }

    @Override
    public void setAllowInputFromOutputSideItems(boolean allow) {
        allowInputFromOutputSideItems = allow;
    }

    @Override
    @Nullable
    public Direction getOutputFacingItems() {
        return outputFacingItems;
    }

    @Override
    public void setOutputFacingItems(@Nullable Direction outputFacing) {
        if (outputFacing == null || outputFacing == getFrontFacing()) return;
        outputFacingItems = outputFacing;
        nextOutputTick = 0;
        updateSubscription();
    }

    @Override
    public Widget createUIWidget() {
        return GregoryMinerUI.createMainPage(this);
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        IFancyUIMachine.super.attachSideTabs(sideTabs);
        sideTabs.attachSubTab(new GregoryMinerUI.AreaPage(this));
        sideTabs.attachSubTab(new GregoryMinerUI.FilterPage(this));
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        return createUIWidget();
    }

    public void addDisplayText(List<Component> text, boolean full) {
        text.add(Component.translatable("gregoryminer.info.status",
                Component.translatable(status.langKey()).withStyle(status.color)));
        int total = Math.max(0, getTotalColumns());
        text.add(Component.translatable("gregoryminer.info.progress", columnsDone, total,
                String.format("%.1f", total == 0 ? 100.0 : columnsDone * 100.0 / total)));
        if (!done) {
            text.add(Component.translatable("gregoryminer.info.cursor", cursorX, cursorZ)
                    .withStyle(ChatFormatting.GRAY));
        }
        text.add(Component.translatable("gregoryminer.info.fortune", fortuneLevel)
                .append(silkLevel > 0 ? Component.translatable("gregoryminer.info.silk_touch") :
                        Component.empty()));
        text.add(Component.translatable("gregoryminer.info.buffer", bufferUsed, syncBufferCap, bufferKinds));
        if (bufferUsed > 0 && outputMissing) {
            text.add(Component.translatable("gregoryminer.info.no_output").withStyle(ChatFormatting.RED));
        } else if (outputFull) {
            text.add(Component.translatable("gregoryminer.info.output_full").withStyle(ChatFormatting.RED));
        }
        if (whitelistSize > 0) {
            text.add(Component.translatable("gregoryminer.info.filter.on", whitelistSize)
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (toolPresent && !toolValid) {
            text.add(Component.translatable("gregoryminer.info.invalid_tool").withStyle(ChatFormatting.RED));
        }
        if (!full) return;
        int w = areaMaxX - areaMinX + 1, d = areaMaxZ - areaMinZ + 1;
        text.add(Component.translatable("gregoryminer.info.area", w, d, areaMinX, areaMinZ, areaMaxX, areaMaxZ));
        text.add(Component.translatable("gregoryminer.info.depth", areaTopY, areaBottomY));
        text.add(Component.translatable(chunkByChunk ? "gregoryminer.info.order.chunk" :
                "gregoryminer.info.order.line"));
        text.add(Component.translatable("gregoryminer.info.speed", syncInterval, syncEuPerColumn));
        if (whitelistSize == 0) text.add(Component.translatable("gregoryminer.info.filter.off"));
        text.add(Component.translatable("gregoryminer.info.last_column", lastColumnBlocks)
                .withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("gregoryminer.info.total_mined", blocksMinedTotal)
                .withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("gregoryminer.info.items", itemsKeptTotal, itemsOutputTotal,
                itemsVoidedTotal).withStyle(ChatFormatting.GRAY));
    }

    public CustomItemStackHandler getToolInventory() {
        return toolInventory;
    }

    public CustomItemStackHandler getFilterInventory() {
        return filterInventory;
    }

    public MinerArea.Unit getAreaUnit() {
        return areaUnit;
    }

    public int getExtWest() {
        return extWest;
    }

    public int getExtEast() {
        return extEast;
    }

    public int getExtNorth() {
        return extNorth;
    }

    public int getExtSouth() {
        return extSouth;
    }

    public boolean isChunkByChunk() {
        return chunkByChunk;
    }

    public boolean isShowArea() {
        return showArea;
    }

    public boolean isDone() {
        return done;
    }

    public int getColumnsDone() {
        return columnsDone;
    }

    public int getTotalColumns() {
        return (areaMaxX - areaMinX + 1) * (areaMaxZ - areaMinZ + 1);
    }

    public int getCursorX() {
        return cursorX;
    }

    public int getCursorZ() {
        return cursorZ;
    }

    public int getAreaMinX() {
        return areaMinX;
    }

    public int getAreaMinZ() {
        return areaMinZ;
    }

    public int getAreaMaxX() {
        return areaMaxX;
    }

    public int getAreaMaxZ() {
        return areaMaxZ;
    }

    public int getAreaTopY() {
        return areaTopY;
    }

    public int getAreaBottomY() {
        return areaBottomY;
    }

    public MiningStatus getStatus() {
        return status;
    }

    public int getFortune() {
        return fortuneLevel;
    }

    public int getSilkTouch() {
        return silkLevel;
    }

    public ItemBuffer getBuffer() {
        return buffer;
    }

    public int getWhitelistSize() {
        return whitelistSize;
    }

    public boolean isOutputMissing() {
        return outputMissing;
    }

    public long getEnergyPerColumn() {
        return syncEuPerColumn;
    }

    public int getVoltageTier() {
        return tier;
    }

    public static String tierName(int tier) {
        return GTValues.VN[tier];
    }
}
