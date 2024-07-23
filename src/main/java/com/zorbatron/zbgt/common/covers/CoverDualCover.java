package com.zorbatron.zbgt.common.covers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.math.IntMath;
import com.zorbatron.zbgt.ZBGTCore;
import com.zorbatron.zbgt.client.ClientHandler;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.impl.FluidHandlerDelegate;
import gregtech.api.capability.impl.ItemHandlerDelegate;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.*;
import gregtech.api.util.GTTransferUtils;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverPump;
import gregtech.common.covers.ManualImportExportMode;
import gregtech.common.covers.filter.FluidFilterContainer;
import gregtech.common.covers.filter.ItemFilterContainer;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipe;

public class CoverDualCover extends CoverBase implements CoverWithUI, ITickable, IControllable {

    public final int maxItemTransferRate;
    private int itemTransferRate;
    protected CoverConveyor.ConveyorMode conveyorMode;
    protected int itemsLeftToTransferLastSecond;
    private CoverableItemHandlerWrapper itemHandlerWrapper;
    protected ItemFilterContainer itemFilterContainer;

    public final int maxFluidTransferRate;
    private int fluidTransferRate;
    protected CoverPump.PumpMode pumpMode;
    protected int fluidLeftToTransferLastSecond;
    private CoverableFluidHandlerWrapper fluidHandlerWrapper;
    protected FluidFilterContainer fluidFilterContainer;
    protected CoverPump.BucketMode bucketMode;

    protected ManualImportExportMode manualImportExportMode;
    public final int tier;
    protected boolean isWorkingAllowed = true;
    protected DualMode dualMode;

    public CoverDualCover(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                          @NotNull EnumFacing attachedSide, int tier, int itemsPerSecond, int mbPerTick) {
        super(definition, coverableView, attachedSide);
        this.tier = tier;
        this.maxItemTransferRate = itemsPerSecond;
        this.maxFluidTransferRate = mbPerTick;
        this.itemTransferRate = maxItemTransferRate;
        this.fluidTransferRate = maxFluidTransferRate;
        this.itemsLeftToTransferLastSecond = itemTransferRate;
        this.fluidLeftToTransferLastSecond = fluidTransferRate;
        this.manualImportExportMode = ManualImportExportMode.DISABLED;
        this.conveyorMode = CoverConveyor.ConveyorMode.EXPORT;
        this.pumpMode = CoverPump.PumpMode.EXPORT;
        this.itemFilterContainer = new ItemFilterContainer(this);
        this.fluidFilterContainer = new FluidFilterContainer(this, () -> false);
        this.dualMode = DualMode.ITEM;
        this.bucketMode = CoverPump.BucketMode.MILLI_BUCKET;
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.isWorkingAllowed;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        this.isWorkingAllowed = isWorkingAllowed;
    }

    protected ModularUI buildUI(ModularUI.Builder builder, EntityPlayer player) {
        return builder.build(this, player);
    }

    @Override
    public ModularUI createUI(EntityPlayer player) {
        WidgetGroup primaryGroup = new WidgetGroup();
        primaryGroup.addWidget(new LabelWidget(10, 5, "cover.dual_cover.title", GTValues.VN[tier]));

        primaryGroup.addWidget(new ImageCycleButtonWidget(176 - 16 - 4, 2, 16, 16, ClientHandler.ITEM_FLUID_OVERLAY, 2,
                () -> dualMode.ordinal(), val -> dualMode = DualMode.values()[val])
                        .setTooltipHoverString("cover.dual_cover.mode_changer"));

        ServerWidgetGroup itemWidgetGroup = new ServerWidgetGroup(() -> dualMode == DualMode.ITEM);
        ServerWidgetGroup fluidWidgetGroup = new ServerWidgetGroup(() -> dualMode == DualMode.FLUID);

        itemWidgetGroup.addWidget(new IncrementButtonWidget(136, 20, 30, 20, 1, 8, 64, 512, this::adjustTransferRate)
                .setDefaultTooltip()
                .setShouldClientCallback(false));
        itemWidgetGroup
                .addWidget(new IncrementButtonWidget(10, 20, 30, 20, -1, -8, -64, -512, this::adjustTransferRate)
                        .setDefaultTooltip()
                        .setShouldClientCallback(false));

        itemWidgetGroup.addWidget(new ImageWidget(40, 20, 96, 20, GuiTextures.DISPLAY));
        itemWidgetGroup.addWidget(new TextFieldWidget2(42, 26, 92, 20, () -> String.valueOf(itemTransferRate), val -> {
            if (val != null && !val.isEmpty())
                setItemTransferRate(MathHelper.clamp(Integer.parseInt(val), 1, maxItemTransferRate));
        })
                .setNumbersOnly(1, maxItemTransferRate)
                .setMaxLength(4)
                .setPostFix("cover.conveyor.transfer_rate"));

        itemWidgetGroup.addWidget(new CycleButtonWidget(10, 45, 75, 20,
                CoverConveyor.ConveyorMode.class, this::getConveyorMode, this::setConveyorMode));

        this.itemFilterContainer.initUI(70, itemWidgetGroup::addWidget);

        fluidWidgetGroup.addWidget(new ImageWidget(44, 20, 62, 20, GuiTextures.DISPLAY));

        fluidWidgetGroup
                .addWidget(new IncrementButtonWidget(136, 20, 30, 20, 1, 10, 100, 1000, this::adjustTransferRate)
                        .setDefaultTooltip()
                        .setShouldClientCallback(false));
        fluidWidgetGroup
                .addWidget(new IncrementButtonWidget(10, 20, 34, 20, -1, -10, -100, -1000, this::adjustTransferRate)
                        .setDefaultTooltip()
                        .setShouldClientCallback(false));

        TextFieldWidget2 textField = new TextFieldWidget2(45, 26, 60, 20,
                () -> bucketMode == CoverPump.BucketMode.BUCKET ?
                        Integer.toString(fluidTransferRate / 1000) : Integer.toString(fluidTransferRate),
                val -> {
                    if (val != null && !val.isEmpty()) {
                        int amount = Integer.parseInt(val);
                        if (this.bucketMode == CoverPump.BucketMode.BUCKET) {
                            amount = IntMath.saturatedMultiply(amount, 1000);
                        }
                        setFluidTransferRate(amount);
                    }
                })
                        .setCentered(true)
                        .setNumbersOnly(1,
                                bucketMode == CoverPump.BucketMode.BUCKET ? maxFluidTransferRate / 1000 :
                                        maxFluidTransferRate)
                        .setMaxLength(8);
        fluidWidgetGroup.addWidget(textField);

        fluidWidgetGroup.addWidget(new CycleButtonWidget(106, 20, 30, 20,
                CoverPump.BucketMode.class, this::getBucketMode, mode -> {
                    if (mode != bucketMode) {
                        setBucketMode(mode);
                    }
                }));

        fluidWidgetGroup.addWidget(new CycleButtonWidget(10, 43, 75, 18,
                CoverPump.PumpMode.class, this::getPumpMode, this::setPumpMode));
        this.fluidFilterContainer.initUI(88, fluidWidgetGroup::addWidget);

        primaryGroup.addWidget(new CycleButtonWidget(7, 166, 116, 20,
                ManualImportExportMode.class, this::getManualImportExportMode, this::setManualImportExportMode)
                        .setTooltipHoverString("cover.universal.manual_import_export.mode.description"));

        primaryGroup.addWidget(itemWidgetGroup);
        primaryGroup.addWidget(fluidWidgetGroup);

        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, 176, 190 + 82)
                .widget(primaryGroup)
                .bindPlayerInventory(player.inventory, GuiTextures.SLOT, 7, 190);
        return buildUI(builder, player);
    }

    public void setBucketMode(CoverPump.BucketMode bucketMode) {
        this.bucketMode = bucketMode;
        if (this.bucketMode == CoverPump.BucketMode.BUCKET)
            setFluidTransferRate(fluidTransferRate / 1000 * 1000);
        markDirty();
    }

    public void setFluidTransferRate(int transferRate) {
        this.fluidTransferRate = transferRate;
        markDirty();
    }

    public void setPumpMode(CoverPump.PumpMode pumpMode) {
        this.pumpMode = pumpMode;
        writeCustomData(GregtechDataCodes.UPDATE_COVER_MODE, buf -> buf.writeEnumValue(pumpMode));
        markDirty();
    }

    public CoverPump.PumpMode getPumpMode() {
        return pumpMode;
    }

    public CoverPump.BucketMode getBucketMode() {
        return bucketMode;
    }

    public int getTransferRate() {
        return itemTransferRate;
    }

    protected void adjustTransferRate(int amount) {
        setItemTransferRate(MathHelper.clamp(itemTransferRate + amount, 1, maxItemTransferRate));
    }

    public void setConveyorMode(CoverConveyor.ConveyorMode conveyorMode) {
        this.conveyorMode = conveyorMode;
        writeCustomData(GregtechDataCodes.UPDATE_COVER_MODE, buf -> buf.writeEnumValue(conveyorMode));
        markDirty();
    }

    public CoverConveyor.ConveyorMode getConveyorMode() {
        return conveyorMode;
    }

    public ManualImportExportMode getManualImportExportMode() {
        return manualImportExportMode;
    }

    protected void setManualImportExportMode(ManualImportExportMode manualImportExportMode) {
        this.manualImportExportMode = manualImportExportMode;
        markDirty();
    }

    public void setItemTransferRate(int transferRate) {
        this.itemTransferRate = transferRate;
        CoverableView coverable = getCoverableView();
        coverable.markDirty();

        if (getWorld() != null && getWorld().isRemote) {
            // tile at cover holder pos
            TileEntity te = getTileEntityHere();
            if (te instanceof TileEntityItemPipe) {
                ((TileEntityItemPipe) te).resetTransferred();
            }
            // tile neighbour to holder pos at attached side
            te = getNeighbor(getAttachedSide());
            if (te instanceof TileEntityItemPipe) {
                ((TileEntityItemPipe) te).resetTransferred();
            }
        }
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return coverable.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side) &&
                coverable.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            @NotNull IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox,
                            @NotNull BlockRenderLayer layer) {
        Textures.BLANK_SCREEN.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public void readCustomData(int discriminator, @NotNull PacketBuffer buf) {
        super.readCustomData(discriminator, buf);
        if (discriminator == GregtechDataCodes.UPDATE_COVER_MODE) {
            this.pumpMode = buf.readEnumValue(CoverPump.PumpMode.class);
            this.conveyorMode = buf.readEnumValue(CoverConveyor.ConveyorMode.class);
            scheduleRenderUpdate();
        }
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.writeInitialSyncData(packetBuffer);
        packetBuffer.writeEnumValue(pumpMode);
        packetBuffer.writeEnumValue(conveyorMode);
    }

    @Override
    public void readInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.readInitialSyncData(packetBuffer);
        this.pumpMode = packetBuffer.readEnumValue(CoverPump.PumpMode.class);
        this.conveyorMode = packetBuffer.readEnumValue(CoverConveyor.ConveyorMode.class);
    }

    @Override
    public @NotNull EnumActionResult onScrewdriverClick(@NotNull EntityPlayer playerIn, @NotNull EnumHand hand,
                                                        @NotNull CuboidRayTraceResult hitResult) {
        if (!getCoverableView().getWorld().isRemote) {
            openUI((EntityPlayerMP) playerIn);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void update() {
        CoverableView coverable = getCoverableView();
        long timer = coverable.getOffsetTimer();
        if (timer % 5 == 0 && isWorkingAllowed && itemsLeftToTransferLastSecond > 0) {
            EnumFacing side = getAttachedSide();
            TileEntity tileEntity = coverable.getNeighbor(side);
            IItemHandler itemHandler = tileEntity == null ? null :
                    tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
            IItemHandler myItemHandler = coverable.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (itemHandler != null && myItemHandler != null) {
                int totalTransferred = doTransferItems(itemHandler, myItemHandler, itemsLeftToTransferLastSecond);
                this.itemsLeftToTransferLastSecond -= totalTransferred;
            }
        }

        if (isWorkingAllowed && fluidLeftToTransferLastSecond > 0) {
            this.fluidLeftToTransferLastSecond -= doTransferFluids(fluidLeftToTransferLastSecond);
        }

        if (timer % 20 == 0) {
            this.itemsLeftToTransferLastSecond = itemTransferRate;
            this.fluidLeftToTransferLastSecond = fluidTransferRate;
        }

        if (timer % 10 == 0) {
            ZBGTCore.LOGGER.info(dualMode);
        }
    }

    protected int doTransferItems(IItemHandler itemHandler, IItemHandler myItemHandler, int maxTransferAmount) {
        return doTransferItemsAny(itemHandler, myItemHandler, maxTransferAmount);
    }

    protected int doTransferItemsAny(IItemHandler itemHandler, IItemHandler myItemHandler, int maxTransferAmount) {
        if (conveyorMode == CoverConveyor.ConveyorMode.IMPORT) {
            return moveInventoryItems(itemHandler, myItemHandler, maxTransferAmount);
        } else if (conveyorMode == CoverConveyor.ConveyorMode.EXPORT) {
            return moveInventoryItems(myItemHandler, itemHandler, maxTransferAmount);
        }
        return 0;
    }

    protected int moveInventoryItems(IItemHandler sourceInventory, IItemHandler targetInventory,
                                     int maxTransferAmount) {
        int itemsLeftToTransfer = maxTransferAmount;
        for (int srcIndex = 0; srcIndex < sourceInventory.getSlots(); srcIndex++) {
            ItemStack sourceStack = sourceInventory.extractItem(srcIndex, itemsLeftToTransfer, true);
            if (sourceStack.isEmpty()) {
                continue;
            }
            if (!itemFilterContainer.testItemStack(sourceStack)) {
                continue;
            }
            ItemStack remainder = GTTransferUtils.insertItem(targetInventory, sourceStack, true);
            int amountToInsert = sourceStack.getCount() - remainder.getCount();

            if (amountToInsert > 0) {
                sourceStack = sourceInventory.extractItem(srcIndex, amountToInsert, false);
                if (!sourceStack.isEmpty()) {
                    GTTransferUtils.insertItem(targetInventory, sourceStack, false);
                    itemsLeftToTransfer -= sourceStack.getCount();

                    if (itemsLeftToTransfer == 0) {
                        break;
                    }
                }
            }
        }
        return maxTransferAmount - itemsLeftToTransfer;
    }

    protected int doTransferFluids(int transferLimit) {
        TileEntity tileEntity = getNeighbor(getAttachedSide());
        IFluidHandler fluidHandler = tileEntity == null ? null : tileEntity
                .getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, getAttachedSide().getOpposite());
        IFluidHandler myFluidHandler = getCoverableView().getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                getAttachedSide());
        if (fluidHandler == null || myFluidHandler == null) {
            return 0;
        }
        return doTransferFluidsInternal(myFluidHandler, fluidHandler, transferLimit);
    }

    protected int doTransferFluidsInternal(IFluidHandler myFluidHandler, IFluidHandler fluidHandler,
                                           int transferLimit) {
        if (pumpMode == CoverPump.PumpMode.IMPORT) {
            return GTTransferUtils.transferFluids(fluidHandler, myFluidHandler, transferLimit,
                    fluidFilterContainer::testFluidStack);
        } else if (pumpMode == CoverPump.PumpMode.EXPORT) {
            return GTTransferUtils.transferFluids(myFluidHandler, fluidHandler, transferLimit,
                    fluidFilterContainer::testFluidStack);
        }
        return 0;
    }

    protected boolean checkInputFluid(FluidStack fluidStack) {
        return fluidFilterContainer.testFluidStack(fluidStack);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, T defaultValue) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (defaultValue == null) {
                return null;
            }
            IItemHandler delegate = (IItemHandler) defaultValue;
            if (itemHandlerWrapper == null || itemHandlerWrapper.delegate != delegate) {
                this.itemHandlerWrapper = new CoverDualCover.CoverableItemHandlerWrapper(delegate);
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandlerWrapper);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (defaultValue == null) {
                return null;
            }
            IFluidHandler delegate = (IFluidHandler) defaultValue;
            if (fluidHandlerWrapper == null || fluidHandlerWrapper.delegate != delegate) {
                this.fluidHandlerWrapper = new CoverDualCover.CoverableFluidHandlerWrapper(delegate);
            }
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandlerWrapper);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return defaultValue;
    }

    @Override
    public void writeToNBT(@NotNull NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);

        tagCompound.setInteger("ItemTransferRate", this.itemTransferRate);
        tagCompound.setInteger("ConveyorMode", this.conveyorMode.ordinal());
        tagCompound.setTag("ItemFilterContainer", this.itemFilterContainer.serializeNBT());

        tagCompound.setInteger("FluidTransferRate", this.fluidTransferRate);
        tagCompound.setInteger("PumpMode", this.pumpMode.ordinal());
        tagCompound.setTag("FluidFilterContainer", this.fluidFilterContainer.serializeNBT());

        tagCompound.setInteger("ManualImportExportMode", this.manualImportExportMode.ordinal());
        tagCompound.setBoolean("IsWorkingAllowed", this.isWorkingAllowed);
        tagCompound.setInteger("DualMode", this.dualMode.ordinal());
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);

        this.itemTransferRate = tagCompound.getInteger("ItemTransferRate");
        this.conveyorMode = CoverConveyor.ConveyorMode.values()[tagCompound.getInteger("ConveyorMode")];
        this.itemFilterContainer.deserializeNBT(tagCompound.getCompoundTag("ItemFilterContainer"));

        this.fluidTransferRate = tagCompound.getInteger("FluidTransferRate");
        this.pumpMode = CoverPump.PumpMode.values()[tagCompound.getInteger("PumpMode")];
        this.fluidFilterContainer.deserializeNBT(tagCompound.getCompoundTag("FluidFilterContainer"));

        this.manualImportExportMode = ManualImportExportMode.values()[tagCompound.getInteger("ManualImportExportMode")];
        this.isWorkingAllowed = tagCompound.getBoolean("IsWorkingAllowed");
        this.dualMode = DualMode.values()[tagCompound.getInteger("DualMode")];
    }

    private class CoverableItemHandlerWrapper extends ItemHandlerDelegate {

        public CoverableItemHandlerWrapper(IItemHandler delegate) {
            super(delegate);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (conveyorMode == CoverConveyor.ConveyorMode.EXPORT &&
                    manualImportExportMode == ManualImportExportMode.DISABLED) {
                return stack;
            }
            if (manualImportExportMode == ManualImportExportMode.FILTERED &&
                    !itemFilterContainer.testItemStack(stack)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (conveyorMode == CoverConveyor.ConveyorMode.IMPORT &&
                    manualImportExportMode == ManualImportExportMode.DISABLED) {
                return ItemStack.EMPTY;
            }
            if (manualImportExportMode == ManualImportExportMode.FILTERED) {
                ItemStack result = super.extractItem(slot, amount, true);
                if (result.isEmpty() || !itemFilterContainer.testItemStack(result)) {
                    return ItemStack.EMPTY;
                }
                return simulate ? result : super.extractItem(slot, amount, false);
            }
            return super.extractItem(slot, amount, simulate);
        }
    }

    private class CoverableFluidHandlerWrapper extends FluidHandlerDelegate {

        public CoverableFluidHandlerWrapper(@NotNull IFluidHandler delegate) {
            super(delegate);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (pumpMode == CoverPump.PumpMode.EXPORT && manualImportExportMode == ManualImportExportMode.DISABLED) {
                return 0;
            }
            if (!checkInputFluid(resource) && manualImportExportMode == ManualImportExportMode.FILTERED) {
                return 0;
            }
            return super.fill(resource, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (pumpMode == CoverPump.PumpMode.IMPORT && manualImportExportMode == ManualImportExportMode.DISABLED) {
                return null;
            }
            if (manualImportExportMode == ManualImportExportMode.FILTERED && !checkInputFluid(resource)) {
                return null;
            }
            return super.drain(resource, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (pumpMode == CoverPump.PumpMode.IMPORT && manualImportExportMode == ManualImportExportMode.DISABLED) {
                return null;
            }
            if (manualImportExportMode == ManualImportExportMode.FILTERED) {
                FluidStack result = super.drain(maxDrain, false);
                if (result == null || result.amount <= 0 || !checkInputFluid(result)) {
                    return null;
                }
                return doDrain ? super.drain(maxDrain, true) : result;
            }
            return super.drain(maxDrain, doDrain);
        }
    }

    private enum DualMode implements IStringSerializable {

        ITEM("cover.cover_dual.mode.item"),
        FLUID("cover.cover_dual.mode.fluid");

        public final String localeName;

        DualMode(String localeName) {
            this.localeName = localeName;
        }

        @NotNull
        @Override
        public String getName() {
            return localeName;
        }
    }
}
