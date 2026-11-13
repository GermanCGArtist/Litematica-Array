package dev.schemarray.shape.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.gui.screens.Screen;

/** The Shape Generator screen, opened from Litematica's main menu. Work package C. */
public class ShapeGeneratorScreen extends GuiBase {
    public ShapeGeneratorScreen(Screen parent) {
        this.setParent(parent);
        this.title = "Shape Generator";
    }
}
