package com.zorbatron.zbgt.api;

import net.minecraft.block.state.IBlockState;

import com.zorbatron.zbgt.common.block.blocks.CoALCasing;
import com.zorbatron.zbgt.common.block.blocks.PreciseCasing;

import gregtech.common.blocks.BlockMachineCasing;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class ZBGTAPI {

    public static final Object2ObjectMap<IBlockState, CoALCasing.CasingType> CoAL_CASINGS = new Object2ObjectOpenHashMap<>();

    public static final Object2ObjectMap<IBlockState, PreciseCasing.CasingType> PRECISE_CASINGS = new Object2ObjectOpenHashMap<>();

    public static final Object2ObjectMap<IBlockState, BlockMachineCasing.MachineCasingType> MACHINE_CASINGS = new Object2ObjectOpenHashMap<>();
}