package com.enderio.core.common.handlers;

import java.util.List;
import java.util.Random;

import cofh.core.item.tool.ItemSickleAdv;
import cofh.lib.util.helpers.MathHelper;
import cofh.redstonearsenal.item.tool.ItemSickleRF;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;
import net.minecraftforge.oredict.OreDictionary;

import com.enderio.core.common.Handlers.Handler;
import com.enderio.core.common.config.ConfigHandler;
import com.enderio.core.common.util.ItemUtil;
import com.google.common.collect.Lists;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;

@Handler
public class RightClickCropHandler {
  public static class PlantInfo {
    public String seed;
    public String block;
    public int meta = 7;
    public int resetMeta = 0;

    private transient ItemStack seedStack;
    private transient Block blockInst;
    
    public PlantInfo() {
    }

    public PlantInfo(String seed, String block, int meta, int resetMeta) {
      this.seed = seed;
      this.block = block;
      this.meta = meta;
      this.resetMeta = resetMeta;
    }

    public void init() {
      seedStack = ItemUtil.parseStringIntoItemStack(seed);
      String[] blockinfo = block.split(":");
      blockInst = GameRegistry.findBlock(blockinfo[0], blockinfo[1]);
    }
  }

  private List<PlantInfo> plants = Lists.newArrayList();

  private PlantInfo currentPlant = null;
  private static final Random rnd = new Random();
  public static final RightClickCropHandler INSTANCE = new RightClickCropHandler();

  private RightClickCropHandler() {
  }

  public void addCrop(PlantInfo info) {
    plants.add(info);
  }

  @SubscribeEvent
  public void handleCropRightClick(PlayerInteractEvent event) {
    int x = event.x, y = event.y, z = event.z;
    Block block = event.world.getBlock(x, y, z);
    int meta = event.world.getBlockMetadata(x, y, z);
    ItemStack stack = event.entityPlayer.getHeldItem();
    if (ConfigHandler.allowCropRC && event.action == Action.RIGHT_CLICK_BLOCK && !(event.entityPlayer instanceof FakePlayer) && stack != null && stack.getUnlocalizedName().toLowerCase().contains("sickle")) {
      for (PlantInfo info : plants) {
        if (info.blockInst == block && meta == info.meta) {
          if (event.world.isRemote) {
            event.entityPlayer.swingItem();
          } else {
            int range = 3;
            boolean specialDurability = false;

            //Thermal Foundation Sickle
            if (stack.getItem() instanceof ItemSickleAdv) {
              ItemSickleAdv Sickle = (ItemSickleAdv) event.entityPlayer.getHeldItem().getItem();
              range = Sickle.radius;
            }
            //Redstone Arsenal Sickle
            if (stack.getItem() instanceof ItemSickleRF) {
              specialDurability = true;
              if (!canDoEnergyOperations(stack))
                range = 1;
            }
              for (int i = x - range; i <= x + range; i++) {
                for (int k = z - range; k <= z + range; k++) {
                  currentPlant = info;
                  block.dropBlockAsItem(event.world, i, y, k, meta, 0);
                  currentPlant = null;
                  event.world.setBlockMetadataWithNotify(i, y, k, info.resetMeta, 3);
                  event.setCanceled(true);
                }
              }
            if (!specialDurability) {
              event.entityPlayer.getHeldItem().damageItem(1, event.entityPlayer);
            }
          }
          break;
        }
      }
    }
  }

  @SubscribeEvent
  public void onHarvestDrop(HarvestDropsEvent event) {
    if (currentPlant != null) {
      for (int i = 0; i < event.drops.size(); i++) {
        ItemStack stack = event.drops.get(i);
        if (stack.getItem() == currentPlant.seedStack.getItem()
            && (currentPlant.seedStack.getItemDamage() == OreDictionary.WILDCARD_VALUE || stack.getItemDamage() == currentPlant.seedStack.getItemDamage())) {
          event.drops.remove(i);
          break;
        }
      }
    }
  }

  public boolean canDoEnergyOperations(ItemStack stack){
    ItemSickleRF sickle = (ItemSickleRF) stack.getItem();

    //RF Math variables
    boolean isEmpowered = sickle.isEmpowered(stack);
    int unbreakingLevel = MathHelper.clamp((EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack)), 0, 4);
    int costRF = sickle.energyPerUse * (5 - unbreakingLevel) / 5;

    if (sickle.getEnergyStored(stack) > costRF)
    {
      sickle.extractEnergy(stack, isEmpowered ? sickle.energyPerUseCharged * (5 - unbreakingLevel) / 5 : sickle.energyPerUse * (5 - unbreakingLevel) / 5, false);
      return true;
    }
    else
      return false;
  }
}
