package com.antaurora.apofirstlight.thirst;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Tooltip line of a drink: the thirst it restores, drawn as a water drop and the amount (ClientThirstTooltip). */
public record ThirstTooltip(double amount) implements TooltipComponent {}
