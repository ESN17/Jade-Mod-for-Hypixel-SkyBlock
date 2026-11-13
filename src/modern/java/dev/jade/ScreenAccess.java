package dev.jade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ScreenAccess {
    static void show(Minecraft mc,Screen screen) { mc.gui.setScreen(screen); }
    static Screen current(Minecraft mc) { return mc.gui.screen(); }
}
