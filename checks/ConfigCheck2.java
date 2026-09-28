package com.dogpound.zoomies;
public class ConfigCheck2 { public static void main(String[] a) {
  assert ZoomiesConfig.threads() == 16 : "custom count: " + ZoomiesConfig.threads();
  assert !ZoomiesConfig.mixinAllowed("MixinEnderIOAlloyDedupe") : "switched off";
  assert ZoomiesConfig.mixinAllowed("MixinOreIngredient");
  System.out.println("custom values OK: threads=16, enderio off");
}}
