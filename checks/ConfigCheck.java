package com.dogpound.zoomies;
public class ConfigCheck { public static void main(String[] a) throws Exception {
  int hw = Runtime.getRuntime().availableProcessors();
  assert ZoomiesConfig.threads() == hw - 2 : "auto = all threads minus reserve: " + ZoomiesConfig.threads();
  assert ZoomiesConfig.mixinAllowed("MixinOreIngredient") && ZoomiesConfig.mixinAllowed("MixinEnderIOAlloyDedupe");
  assert new java.io.File("config/zoomies.cfg").isFile() : "file written";
  System.out.println("defaults OK, threads=" + ZoomiesConfig.threads() + " of " + hw);
}}
