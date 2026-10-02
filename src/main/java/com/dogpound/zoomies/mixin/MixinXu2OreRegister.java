package com.dogpound.zoomies.mixin;

import java.util.HashSet;

import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extra Utilities 2 adds crusher recipes (ore → 2 dust, ingot → 1 dust) for every metal that has an ore, an ingot
 * and a dust. It did that by, on EVERY ingot/ore/dust registration, copying all of the game's ore names into a new set
 * and re-checking every dust in it, compiling two regexes per dust (measured 2026-10-02: 40 s of her big pack's load).
 *
 * Same recipes, found directly: a registration can only complete the one metal it names, so only that metal's
 * dust/ore/ingot trio is checked (O(1) ore-name lookups). The recipe is added when the last of the three appears —
 * exactly when the original's rescan would first have found it.
 */
@Mixin(targets = "com.rwtema.extrautils2.machine.MachineInit", remap = false)
public abstract class MixinXu2OreRegister {
    @Shadow static HashSet<String> registeredDusts;

    @Shadow static void addCrusherOreRecipe(Object input, Object output, int amount) { throw new AssertionError(); }

    @Inject(method = "onOreRegister", at = @At("HEAD"), cancellable = true)
    private static void zoomies$onlyThisMetal(OreDictionary.OreRegisterEvent event, CallbackInfo ci) {
        String name = event.getName(), dust;
        if (name.startsWith("dust")) dust = name;
        else if (name.startsWith("ore")) dust = "dust" + name.substring(3);
        else if (name.startsWith("ingot")) dust = "dust" + name.substring(5);
        else return;                                   // the original ignores these names too
        ci.cancel();
        if (registeredDusts.contains(dust) || !OreDictionary.doesOreNameExist(dust)) return;
        String metal = dust.substring(4), ore = "ore" + metal, ingot = "ingot" + metal;
        if (OreDictionary.doesOreNameExist(ore) && OreDictionary.doesOreNameExist(ingot)) {
            addCrusherOreRecipe(ore, dust, 2);
            addCrusherOreRecipe(ingot, dust, 1);
            registeredDusts.add(dust);
        }
    }
}
