package com.zorbatron.zbgt.client;

import gregtech.api.gui.resources.TextureArea;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

public class ClientHandler {

    public static SimpleOverlayRenderer WATER_OVERLAY_INFINITY;
    public static SimpleOverlayRenderer ITEM_OVERLAY_INFINITY;
    public static SimpleOverlayRenderer CRIB_ACTIVE;
    public static SimpleOverlayRenderer CRIB_INACTIVE;

    public static TextureArea ITEM_FLUID_OVERLAY;
    public static TextureArea ME_PATTERN_OVERLAY;
    public static TextureArea EXPORT;

    public static void preInit() {
        WATER_OVERLAY_INFINITY = new SimpleOverlayRenderer("overlay/machine/overlay_water_infinity");
        ITEM_OVERLAY_INFINITY = new SimpleOverlayRenderer("overlay/machine/overlay_item_infinity");
        CRIB_ACTIVE = new SimpleOverlayRenderer("overlay/machine/OVERLAY_ME_CRAFTING_INPUT_BUFFER");
        CRIB_INACTIVE = new SimpleOverlayRenderer("overlay/machine/OVERLAY_ME_CRAFTING_INPUT_BUS");

        ITEM_FLUID_OVERLAY = TextureArea.fullImage("textures/gui/widget/item_fluid.png");
        ME_PATTERN_OVERLAY = TextureArea.fullImage("textures/gui/widget/pattern_me.png");
        EXPORT = TextureArea.fullImage("textures/gui/widget/export.png");
    }
}
