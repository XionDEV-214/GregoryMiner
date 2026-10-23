package com.eisiadev.gregoryminer.machine;

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
import net.minecraftforge.common.util.LazyOptional;
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
    private static final GameProfile ANONYMOUS_PROFILE = new GameProfile(
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
    protected long blocksMinedTotal = 0;

    @DescSynced
    protected int areaMinX, areaMinZ, areaMaxX, areaMaxZ;
    @DescSynced
    protected int areaTopY, areaBottomY;
    @DescSynced
    protected MiningStatus status = MiningStatus.IDLE;
    @DescSynced
    protected boolean outputMissing = false;

    @Nullable
    private MinerArea area;
    @Nullable
    private TickableSubscription tickSubscription;
    private long nextOutputTick = 0;
    private LazyOptional<IItemHandler> outputCap = LazyOptional.empty();
    private boolean outputCapDirty = true;
    private long pendingCost = 0;
    private int cachedFortune = 0, cachedSilk = 0;
    private boolean cachedToolValid = false;
    private final Set<Item> whitelist = new ReferenceOpenHashSet<>();
    @Nullable
    private GameProfile ownerProfile;
    private int lastColumnBlocks = 0;
    private long lastColumnEU = 0;

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
        outputCapDirty = true;
        updateSubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (isRemote()) {
            AreaRenderRegistry.remove(this);
        }
        tickSubscription = null;
        outputCap = LazyOptional.empty();
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
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        outputCapDirty = true;
        nextOutputTick = 0;
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        if (outputFacingItems == newFacing) {
            outputFacingItems = newFacing.getOpposite();
        }
        outputCapDirty = true;
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
        if (pendingCost > 0 && energyContainer.getEnergyStored() < pendingCost) {
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
            ColumnResult r = mineOneColumn(level, cursorX, cursorZ);
            switch (r) {
                case WAIT_POWER -> {
                    setStatus(MiningStatus.NO_POWER);
                    return;
                }
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
        WAIT_POWER,
        WAIT_CHUNK
    }

    private ColumnResult mineOneColumn(ServerLevel level, int x, int z) {
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

        long cost = count * GMConfig.euPerBlock(tier);
        long pay = Math.min(cost, energyContainer.getEnergyCapacity());
        if (energyContainer.getEnergyStored() < pay) {
            pendingCost = pay;
            return ColumnResult.WAIT_POWER;
        }
        pendingCost = 0;

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

        energyContainer.removeEnergy(pay);

        ItemStack tool = cachedToolValid ? toolInventory.getStackInSlot(0) : MinerTools.fallbackTool();
        boolean filtering = !whitelist.isEmpty();
        int mined = 0;

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
                if (filtering && !whitelist.contains(drop.getItem())) continue;
                buffer.add(drop);
            }
            level.setBlock(mpos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
            mined++;
        }

        columnsDone++;
        blocksMinedTotal += mined;
        lastColumnBlocks = mined;
        lastColumnEU = pay;
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
            outputCapDirty = true;
            nextOutputTick = now + OUTPUT_BACKOFF_TICKS;
            return;
        }
        long moved = buffer.flushInto(target, GMConfig.outputStacksPerTick());
        nextOutputTick = moved > 0 ? now + 1 : now + OUTPUT_BACKOFF_TICKS;
        if (moved > 0) {
            markDirty();
            if (status == MiningStatus.BUFFER_FULL && buffer.usedStacks() < GMConfig.bufferStacks()) {
                setStatus(MiningStatus.WORKING);
            }
        }
        if (buffer.isEmpty()) updateSubscription();
    }

    @Nullable
    private IItemHandler getOutputHandler(ServerLevel level) {
        if (outputCapDirty) {
            outputCapDirty = false;
            outputCap = LazyOptional.empty();
            Direction face = outputFacingItems;
            if (face == null) return null;
            BlockEntity be = level.getBlockEntity(getPos().relative(face));
            if (be != null) {
                LazyOptional<IItemHandler> cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite());
                if (cap.isPresent()) {
                    cap.addListener(l -> outputCapDirty = true);
                    outputCap = cap;
                }
            }
        }
        return outputCap.resolve().orElse(null);
    }

    private FakePlayer getFakePlayer(ServerLevel level) {
        if (ownerProfile == null) {
            UUID owner = getOwnerUUID();
            if (owner == null) {
                return FakePlayerFactory.get(level, ANONYMOUS_PROFILE);
            } else {
                String name = "[GregoryMiner]";
                GameProfileCache cache = level.getServer().getProfileCache();
                if (cache != null) {
                    name = cache.get(owner).map(GameProfile::getName).orElse(name);
                }
                ownerProfile = new GameProfile(owner, name);
            }
        }
        return FakePlayerFactory.get(level, ownerProfile);
    }

    private void onToolChanged() {
        ItemStack tool = toolInventory.getStackInSlot(0);
        cachedToolValid = MinerTools.isValidTool(tool);
        cachedFortune = cachedToolValid ? MinerTools.fortune(tool) : 0;
        cachedSilk = cachedToolValid ? MinerTools.silkTouch(tool) : 0;
    }

    private void rebuildWhitelist() {
        whitelist.clear();
        for (int i = 0; i < filterInventory.getSlots(); i++) {
            ItemStack s = filterInventory.getStackInSlot(i);
            if (!s.isEmpty()) whitelist.add(s.getItem());
        }
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
        pendingCost = 0;
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
        outputCapDirty = true;
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
        int total = getArea().totalColumns();
        text.add(Component.translatable("gregoryminer.info.progress", columnsDone, total,
                String.format("%.1f", total == 0 ? 100.0 : columnsDone * 100.0 / total)));
        if (!done) {
            text.add(Component.translatable("gregoryminer.info.cursor", cursorX, cursorZ)
                    .withStyle(ChatFormatting.GRAY));
        }
        text.add(Component.translatable("gregoryminer.info.fortune", cachedFortune)
                .append(cachedSilk > 0 ? Component.translatable("gregoryminer.info.silk_touch") :
                        Component.empty()));
        text.add(Component.translatable("gregoryminer.info.buffer", buffer.usedStacks(), GMConfig.bufferStacks(),
                buffer.kinds()));
        if (outputMissing && !buffer.isEmpty()) {
            text.add(Component.translatable("gregoryminer.info.no_output").withStyle(ChatFormatting.RED));
        }
        if (!full) return;
        MinerArea a = getArea();
        text.add(Component.translatable("gregoryminer.info.area", a.width(), a.depth(), a.minX, a.minZ, a.maxX,
                a.maxZ));
        text.add(Component.translatable("gregoryminer.info.depth", areaTopY, areaBottomY));
        text.add(Component.translatable(chunkByChunk ? "gregoryminer.info.order.chunk" :
                "gregoryminer.info.order.line"));
        text.add(Component.translatable("gregoryminer.info.speed", GMConfig.interval(tier),
                GMConfig.euPerBlock(tier)));
        text.add(Component.translatable(whitelist.isEmpty() ? "gregoryminer.info.filter.off" :
                "gregoryminer.info.filter.on", whitelist.size()));
        text.add(Component.translatable("gregoryminer.info.last_column", lastColumnBlocks, lastColumnEU)
                .withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("gregoryminer.info.total_mined", blocksMinedTotal)
                .withStyle(ChatFormatting.GRAY));
        if (!toolInventory.getStackInSlot(0).isEmpty() && !cachedToolValid) {
            text.add(Component.translatable("gregoryminer.info.invalid_tool").withStyle(ChatFormatting.RED));
        }
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
        return cachedFortune;
    }

    public int getSilkTouch() {
        return cachedSilk;
    }

    public ItemBuffer getBuffer() {
        return buffer;
    }

    public int getWhitelistSize() {
        return whitelist.size();
    }

    public boolean isOutputMissing() {
        return outputMissing;
    }

    public long getEnergyPerBlock() {
        return GMConfig.euPerBlock(tier);
    }

    public int getVoltageTier() {
        return tier;
    }

    public static String tierName(int tier) {
        return GTValues.VN[tier];
    }
}
