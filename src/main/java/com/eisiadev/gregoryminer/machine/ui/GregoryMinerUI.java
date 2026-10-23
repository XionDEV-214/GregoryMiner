package com.eisiadev.gregoryminer.machine.ui;

import com.eisiadev.gregoryminer.machine.GregoryMinerMachine;
import com.eisiadev.gregoryminer.machine.MinerArea;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.PhantomSlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GregoryMinerUI {

    private GregoryMinerUI() {}

    private static IGuiTexture buttonText(Supplier<String> langKey) {
        return new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(langKey));
    }

    private static Consumer<ClickData> serverOnly(Runnable action) {
        return cd -> {
            if (!cd.isRemote) action.run();
        };
    }

    public static Widget createMainPage(GregoryMinerMachine machine) {
        WidgetGroup group = new WidgetGroup(0, 0, 172, 110);

        WidgetGroup screen = new WidgetGroup(0, 0, 144, 84);
        screen.setBackground(GuiTextures.BACKGROUND_INVERSE);
        DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(4, 4, 136, 76);
        scroll.setBackground(GuiTextures.DISPLAY);
        ComponentPanelWidget panel = new ComponentPanelWidget(4, 4, list -> machine.addDisplayText(list, true))
                .setMaxWidthLimit(126);
        scroll.addWidget(panel);
        screen.addWidget(scroll);
        group.addWidget(screen);

        SlotWidget toolSlot = new SlotWidget(machine.getToolInventory(), 0, 150, 2, true, true);
        toolSlot.setBackground(GuiTextures.SLOT);
        toolSlot.setHoverTooltips(Component.translatable("gregoryminer.gui.tool_slot.tooltip"));
        group.addWidget(toolSlot);

        ProgressWidget energy = new ProgressWidget(
                () -> {
                    long cap = machine.energyContainer.getEnergyCapacity();
                    return cap <= 0 ? 0 : machine.energyContainer.getEnergyStored() / (double) cap;
                },
                151, 24, 16, 60, new ProgressTexture(IGuiTexture.EMPTY, GuiTextures.ENERGY_BAR_BASE));
        energy.setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP);
        energy.setBackground(GuiTextures.ENERGY_BAR_BACKGROUND);
        energy.setHoverTooltips(Component.translatable("gregoryminer.gui.energy.tooltip"));
        group.addWidget(energy);

        group.addWidget(new ButtonWidget(0, 90, 56, 20,
                buttonText(() -> machine.isShowArea() ? "gregoryminer.gui.show_area.on" :
                        "gregoryminer.gui.show_area.off"),
                serverOnly(machine::toggleShowArea))
                .setHoverTooltips(Component.translatable("gregoryminer.gui.show_area.tooltip")));
        group.addWidget(new ButtonWidget(58, 90, 56, 20,
                buttonText(() -> machine.isChunkByChunk() ? "gregoryminer.gui.order.chunk" :
                        "gregoryminer.gui.order.line"),
                serverOnly(machine::toggleChunkByChunk))
                .setHoverTooltips(Component.translatable("gregoryminer.gui.order.tooltip")));
        group.addWidget(new ButtonWidget(116, 90, 56, 20,
                buttonText(() -> "gregoryminer.gui.restart"),
                serverOnly(machine::resetProgress))
                .setHoverTooltips(Component.translatable("gregoryminer.gui.restart.tooltip")));
        return group;
    }

    public static final class AreaPage implements IFancyUIProvider {

        private final GregoryMinerMachine machine;

        public AreaPage(GregoryMinerMachine machine) {
            this.machine = machine;
        }

        @Override
        public Widget createMainPage(FancyMachineUIWidget widget) {
            WidgetGroup group = new WidgetGroup(0, 0, 172, 150);

            group.addWidget(new ButtonWidget(0, 0, 172, 20,
                    buttonText(() -> machine.getAreaUnit() == MinerArea.Unit.CHUNK ? "gregoryminer.gui.unit.chunk" :
                            "gregoryminer.gui.unit.block"),
                    serverOnly(machine::toggleAreaUnit))
                    .setHoverTooltips(Component.translatable("gregoryminer.gui.unit.tooltip")));

            addExtentRow(group, 24, "gregoryminer.gui.west", machine::getExtWest, machine::setExtWest);
            addExtentRow(group, 46, "gregoryminer.gui.east", machine::getExtEast, machine::setExtEast);
            addExtentRow(group, 68, "gregoryminer.gui.north", machine::getExtNorth, machine::setExtNorth);
            addExtentRow(group, 90, "gregoryminer.gui.south", machine::getExtSouth, machine::setExtSouth);

            WidgetGroup screen = new WidgetGroup(0, 114, 172, 36);
            screen.setBackground(GuiTextures.DISPLAY);
            screen.addWidget(new ComponentPanelWidget(4, 4, list -> {
                MinerArea.Unit unit = machine.getAreaUnit();
                int w = machine.getAreaMaxX() - machine.getAreaMinX() + 1;
                int d = machine.getAreaMaxZ() - machine.getAreaMinZ() + 1;
                list.add(Component.translatable("gregoryminer.gui.area_summary", w, d, w / 16.0f, d / 16.0f));
                list.add(Component.translatable(unit == MinerArea.Unit.CHUNK ? "gregoryminer.gui.area_hint.chunk" :
                        "gregoryminer.gui.area_hint.block"));
            }).setMaxWidthLimit(164));
            group.addWidget(screen);
            return group;
        }

        private static void addExtentRow(WidgetGroup group, int y, String labelKey, Supplier<Integer> getter,
                                         Consumer<Integer> setter) {
            group.addWidget(new ImageWidget(0, y, 50, 20, new TextTexture(labelKey)));
            group.addWidget(new IntInputWidget(52, y, 120, 20, getter, setter)
                    .setMin(0).setMax(32 * 16));
        }

        @Override
        public IGuiTexture getTabIcon() {
            return new ItemStackTexture(Items.FILLED_MAP);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gregoryminer.gui.tab.area");
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable("gregoryminer.gui.tab.area"),
                    Component.translatable("gregoryminer.gui.tab.area.desc").withStyle(ChatFormatting.GRAY));
        }
    }

    public static final class FilterPage implements IFancyUIProvider {

        private final GregoryMinerMachine machine;

        public FilterPage(GregoryMinerMachine machine) {
            this.machine = machine;
        }

        @Override
        public Widget createMainPage(FancyMachineUIWidget widget) {
            int side = GregoryMinerMachine.filterGridSize(machine.getVoltageTier());
            int gridPx = side * 18;
            int width = 172;
            int height = Math.max(gridPx, 60) + 50;
            WidgetGroup group = new WidgetGroup(0, 0, width, height);

            int gx = (width - gridPx) / 2;
            for (int row = 0; row < side; row++) {
                for (int col = 0; col < side; col++) {
                    int index = row * side + col;
                    PhantomSlotWidget slot = new PhantomSlotWidget(machine.getFilterInventory(), index,
                            gx + col * 18, row * 18);
                    slot.setClearSlotOnRightClick(true);
                    slot.setMaxStackSize(1);
                    slot.setBackground(GuiTextures.SLOT);
                    group.addWidget(slot);
                }
            }

            int y = Math.max(gridPx, 60) + 4;
            group.addWidget(new ButtonWidget(0, y, 172, 20,
                    buttonText(() -> "gregoryminer.gui.filter.clear"),
                    serverOnly(machine::clearFilter)));

            WidgetGroup screen = new WidgetGroup(0, y + 24, 172, 22);
            screen.setBackground(GuiTextures.DISPLAY);
            screen.addWidget(new ComponentPanelWidget(4, 4, list -> list.add(Component.translatable(
                    machine.getWhitelistSize() == 0 ? "gregoryminer.gui.filter.empty" :
                            "gregoryminer.gui.filter.active",
                    machine.getWhitelistSize()))).setMaxWidthLimit(164));
            group.addWidget(screen);
            return group;
        }

        @Override
        public IGuiTexture getTabIcon() {
            return new ItemStackTexture(Items.HOPPER);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gregoryminer.gui.tab.filter");
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable("gregoryminer.gui.tab.filter"),
                    Component.translatable("gregoryminer.gui.tab.filter.desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
