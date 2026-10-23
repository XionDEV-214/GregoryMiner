package com.eisiadev.gregoryminer.client;

import com.eisiadev.gregoryminer.GregoryMiner;
import com.eisiadev.gregoryminer.machine.GregoryMinerMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = GregoryMiner.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AreaRenderer {

    private AreaRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (AreaRenderRegistry.machines().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cam = event.getCamera().getPosition();
        double maxDist = (mc.options.getEffectiveRenderDistance() + 2) * 16.0;
        double maxDistSq = maxDist * maxDist;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = null;

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        Iterator<GregoryMinerMachine> it = AreaRenderRegistry.machines().iterator();
        while (it.hasNext()) {
            GregoryMinerMachine m = it.next();
            if (m.isInValid() || m.getLevel() != mc.level) {
                if (m.isInValid()) it.remove();
                continue;
            }
            if (!m.isShowArea()) continue;

            double minX = m.getAreaMinX(), minZ = m.getAreaMinZ();
            double maxX = m.getAreaMaxX() + 1.0, maxZ = m.getAreaMaxZ() + 1.0;
            double cx = Math.max(minX, Math.min(cam.x, maxX));
            double cz = Math.max(minZ, Math.min(cam.z, maxZ));
            double dx = cx - cam.x, dz = cz - cam.z;
            if (dx * dx + dz * dz > maxDistSq) continue;

            if (lines == null) lines = buffers.getBuffer(RenderType.lines());
            double top = m.getAreaTopY() + 1.0;
            double bottom = m.getAreaBottomY();
            LevelRenderer.renderLineBox(pose, lines, new AABB(minX, bottom, minZ, maxX, top, maxZ),
                    0.25f, 0.75f, 1.0f, 1.0f);
            if (!m.isDone()) {
                double x = m.getCursorX(), z = m.getCursorZ();
                LevelRenderer.renderLineBox(pose, lines, new AABB(x, bottom, z, x + 1, top, z + 1),
                        1.0f, 0.55f, 0.1f, 1.0f);
            }
        }

        pose.popPose();
        if (lines != null) buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) AreaRenderRegistry.clear();
    }
}
