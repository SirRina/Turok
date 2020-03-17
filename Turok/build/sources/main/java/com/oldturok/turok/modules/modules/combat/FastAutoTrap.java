package com.oldturok.turok.module.modules.combat;

import com.oldturok.turok.util.BlockInteractionHelper;
import com.mojang.realmsclient.gui.ChatFormatting;
import com.oldturok.turok.module.ModuleManager;
import com.oldturok.turok.setting.Settings;
import com.oldturok.turok.setting.Setting;
import com.oldturok.turok.util.EntityUtil;
import com.oldturok.turok.module.Module;
import com.oldturok.turok.util.Friends;
import com.oldturok.turok.TurokMessage;
import com.oldturok.turok.TurokChat;

import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.block.BlockObsidian;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockLiquid;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockAir;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameType;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.Entity;
import net.minecraft.block.Block;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static com.oldturok.turok.util.BlockInteractionHelper.canBeClicked;
import static com.oldturok.turok.util.BlockInteractionHelper.faceVectorPacketInstant;

// Update by Rina 09/03/20.
@Module.Info(name = "FastAutoTrap", category = Module.Category.TUROK_COMBAT)
public class FastAutoTrap extends Module {
    private Setting<Double> range = register(Settings.doubleBuilder("Range").withMinimum(3.5).withValue(5.0).withMaximum(10.0).build());
    private Setting<Integer> blocksPerTick = register(Settings.integerBuilder("BlocksPerTick").withMinimum(1).withValue(7).withMaximum(23).build());
    private Setting<Integer> tickDelay = register(Settings.integerBuilder("TickDelay").withMinimum(0).withValue(2).withMaximum(10).build());
    private Setting<Cage> cage = register(Settings.e("Cage", Cage.CRYSTALFULL));
    private Setting<Boolean> rotate = register(Settings.b("Rotate", true));
    private Setting<Boolean> noGlitchBlocks = register(Settings.b("NoGlitchBlocks", true));
    private Setting<Boolean> activeInFreecam = register(Settings.b("Active In Freecam", true));
    private Setting<Boolean> infoMessage = register(Settings.b("Debug", false));

    private EntityPlayer closestTarget;
    private String lastTargetName;

    private int playerHotbarSlot = -1;
    private int lastHotbarSlot = -1;
    private boolean isSneaking = false;

    private int delayStep = 0;
    private int offsetStep = 0;
    private boolean firstRun;
    private boolean missingObiDisable = false;

    private static EnumFacing get_placeable_side(BlockPos pos) {
        for (EnumFacing side : EnumFacing.values()) {

            BlockPos neighbour = pos.offset(side);

            if (!mc.world.getBlockState(neighbour).getBlock().canCollideCheck(mc.world.getBlockState(neighbour), false)) {
                continue;
            }

            IBlockState blockState = mc.world.getBlockState(neighbour);
            if (!blockState.getMaterial().isReplaceable()) {
                return side;
            }
        }

        return null;

    }

    @Override
    protected void onEnable() {
        TurokMessage.send_msg("FastAutoTrap -> " + ChatFormatting.GREEN + "Enabled!");

        if (mc.player == null) {
            this.disable();
            return;
        }

        firstRun = true;

        playerHotbarSlot = mc.player.inventory.currentItem;
        lastHotbarSlot = -1;
    }

    @Override
    protected void onDisable() {
        TurokMessage.send_msg("FastAutoTrap <- " + ChatFormatting.RED + "Disabled!");

        if (mc.player == null) {
            return;
        }

        if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
            mc.player.inventory.currentItem = playerHotbarSlot;
        }

        if (isSneaking) {
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
            isSneaking = false;
        }

        playerHotbarSlot = -1;
        lastHotbarSlot = -1;

        missingObiDisable = false;

    }

    @Override
    public void onUpdate() {

        if (mc.player == null) {
            return;
        }

        if (!activeInFreecam.getValue() && ModuleManager.isModuleEnabled("Freecam")) {
            return;
        }


        if (firstRun) {
            if (find_obi_in_hotbar() == -1) {
                if (infoMessage.getValue()) {
                    TurokMessage.send_msg("FastAutoTrap <- " + ChatFormatting.RED + "Disabled" + ChatFormatting.RESET + ", Obsidian missing!");
                }
                this.disable();
                return;
            }
        } else {
            if (delayStep < tickDelay.getValue()) {
                delayStep++;
                return;
            } else {
                delayStep = 0;
            }
        }

        findClosestTarget();

        if (closestTarget == null) {
            return;
        }

        if (firstRun) {
            firstRun = false;
            lastTargetName = closestTarget.getName();
        } else if (!lastTargetName.equals(closestTarget.getName())) {
            offsetStep = 0;
            lastTargetName = closestTarget.getName();
        }

        List<Vec3d> placeTargets = new ArrayList<>();

        if (cage.getValue().equals(Cage.TRAP)) {
            Collections.addAll(placeTargets, Offsets.TRAP);
        }

        if (cage.getValue().equals(Cage.CRYSTALEXA)) {
            Collections.addAll(placeTargets, Offsets.CRYSTALEXA);
        }

        if (cage.getValue().equals(Cage.CRYSTALFULL)) {
            Collections.addAll(placeTargets, Offsets.CRYSTALFULL);
        }

        int blocksPlaced = 0;

        while (blocksPlaced < blocksPerTick.getValue()) {

            if (offsetStep >= placeTargets.size()) {
                offsetStep = 0;
                break;
            }

            BlockPos offsetPos = new BlockPos(placeTargets.get(offsetStep));
            BlockPos targetPos = new BlockPos(closestTarget.getPositionVector()).down().add(offsetPos.x, offsetPos.y, offsetPos.z);

            if (placeBlockInRange(targetPos, range.getValue())) {
                blocksPlaced++;
            }

            offsetStep++;

        }

        if (blocksPlaced > 0) {

            if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
                mc.player.inventory.currentItem = playerHotbarSlot;
                lastHotbarSlot = playerHotbarSlot;
            }

            if (isSneaking) {
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
                isSneaking = false;
            }

        }

        if (missingObiDisable) {
            missingObiDisable = false;
            if (infoMessage.getValue()) {
                TurokMessage.send_msg("FastAutoTrap <- " + ChatFormatting.RED + "Disabled" + ChatFormatting.RESET + ", Obsidian missing!");
            }
            this.disable();
        }

    }

    private boolean placeBlockInRange(BlockPos pos, double range) {

        Block block = mc.world.getBlockState(pos).getBlock();
        if (!(block instanceof BlockAir) && !(block instanceof BlockLiquid)) {
            return false;
        }

        for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(pos))) {
            if (!(entity instanceof EntityItem) && !(entity instanceof EntityXPOrb)) {
                return false;
            }
        }

        EnumFacing side = get_placeable_side(pos);

        if (side == null) {
            return false;
        }

        BlockPos neighbour = pos.offset(side);
        EnumFacing opposite = side.getOpposite();

        if (!canBeClicked(neighbour)) {
            return false;
        }

        Vec3d hitVec = new Vec3d(neighbour).add(0.5, 0.5, 0.5).add(new Vec3d(opposite.getDirectionVec()).scale(0.5));
        Block neighbourBlock = mc.world.getBlockState(neighbour).getBlock();

        if (mc.player.getPositionVector().distanceTo(hitVec) > range) {
            return false;
        }

        int obiSlot = find_obi_in_hotbar();

        if (obiSlot == -1) {
            missingObiDisable = true;
            return false;
        }

        if (lastHotbarSlot != obiSlot) {
            mc.player.inventory.currentItem = obiSlot;
            lastHotbarSlot = obiSlot;
        }

        if (!isSneaking && BlockInteractionHelper.blackList.contains(neighbourBlock) || BlockInteractionHelper.shulkerList.contains(neighbourBlock)) {
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));
            isSneaking = true;
        }

        if (rotate.getValue()) {
            faceVectorPacketInstant(hitVec);
        }

        mc.playerController.processRightClickBlock(mc.player, mc.world, neighbour, opposite, hitVec, EnumHand.MAIN_HAND);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        mc.rightClickDelayTimer = 4;

        if (noGlitchBlocks.getValue() && !mc.playerController.getCurrentGameType().equals(GameType.CREATIVE)) {
            mc.player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, neighbour, opposite));
        }

        return true;

    }

    private int find_obi_in_hotbar() {

        int slot = -1;
        for (int i = 0; i < 9; i++) {

            ItemStack stack = mc.player.inventory.getStackInSlot(i);

            if (stack == ItemStack.EMPTY || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }

            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block instanceof BlockObsidian) {
                slot = i;
                break;
            }

        }

        return slot;

    }

    private void findClosestTarget() {

        List<EntityPlayer> playerList = mc.world.playerEntities;

        closestTarget = null;

        for (EntityPlayer target : playerList) {

            if (target == mc.player) {
                continue;
            }

            if (mc.player.getDistance(target) > range.getValue() + 3) {
                continue;
            }

            if (!EntityUtil.isLiving(target)) {
                continue;
            }

            if ((target).getHealth() <= 0) {
                continue;
            }

            if (Friends.isFriend(target.getName())) {
                continue;
            }

            if (closestTarget == null) {
                closestTarget = target;
                continue;
            }

            if (mc.player.getDistance(target) < mc.player.getDistance(closestTarget)) {
                closestTarget = target;
            }

        }

    }

    @Override
    public String getHudInfo() {
        if (closestTarget != null) {
            return closestTarget.getName().toUpperCase();
        }
        return "NO TARGET";
    }

    private enum Cage {
        TRAP, CRYSTALEXA, CRYSTALFULL
    }

    private static class Offsets {

        private static final Vec3d[] TRAP = {
                new Vec3d(0, 0, -1),
                new Vec3d(1, 0, 0),
                new Vec3d(0, 0, 1),
                new Vec3d(-1, 0, 0),
                new Vec3d(0, 1, -1),
                new Vec3d(1, 1, 0),
                new Vec3d(0, 1, 1),
                new Vec3d(-1, 1, 0),
                new Vec3d(0, 2, -1),
                new Vec3d(1, 2, 0),
                new Vec3d(0, 2, 1),
                new Vec3d(-1, 2, 0),
                new Vec3d(0, 3, -1),
                new Vec3d(0, 3, 0)
        };

        private static final Vec3d[] CRYSTALEXA = {
                new Vec3d(0, 0, -1),
                new Vec3d(0, 1, -1),
                new Vec3d(0, 2, -1),
                new Vec3d(1, 2, 0),
                new Vec3d(0, 2, 1),
                new Vec3d(-1, 2, 0),
                new Vec3d(-1, 2, -1),
                new Vec3d(1, 2, 1),
                new Vec3d(1, 2, -1),
                new Vec3d(-1, 2, 1),
                new Vec3d(0, 3, -1),
                new Vec3d(0, 3, 0)
        };

        private static final Vec3d[] CRYSTALFULL = {
                new Vec3d(0, 0, -1),
                new Vec3d(1, 0, 0),
                new Vec3d(0, 0, 1),
                new Vec3d(-1, 0, 0),
                new Vec3d(-1, 0, 1),
                new Vec3d(1, 0, -1),
                new Vec3d(-1, 0, -1),
                new Vec3d(1, 0, 1),
                new Vec3d(-1, 1, -1),
                new Vec3d(1, 1, 1),
                new Vec3d(-1, 1, 1),
                new Vec3d(1, 1, -1),
                new Vec3d(0, 2, -1),
                new Vec3d(1, 2, 0),
                new Vec3d(0, 2, 1),
                new Vec3d(-1, 2, 0),
                new Vec3d(-1, 2, 1),
                new Vec3d(1, 2, -1),
                new Vec3d(0, 3, -1),
                new Vec3d(0, 3, 0)
        };
    }
}