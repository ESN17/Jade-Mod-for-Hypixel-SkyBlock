package dev.jade;

import net.minecraft.world.scores.*;

final class SidebarSlot {
    static DisplaySlot forTeam(PlayerTeam team) {
        return team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
    }
}
