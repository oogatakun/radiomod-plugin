package com.oogata.radioplugin.radio;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 杖の右クリックによる場所の選択と、チャンク読み込み時のパーツの確認。 */
public final class RadioListener implements Listener {

    private final RadioManager manager;
    private final Plugin plugin;

    public RadioListener(RadioManager manager, Plugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /** 「ラジオ設置の杖」を作る。見た目は光る棒で、PersistentDataContainer に目印を持つ。 */
    public static ItemStack createWand(RadioManager manager) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("ラジオ設置の杖");
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(manager.wandKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(manager.wandKey(), PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isWand(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("radioplugin.rd.manage")) {
            player.sendMessage("この杖を使う権限がありません");
            return;
        }
        Block block = event.getClickedBlock();
        Location top = block.getLocation().add(0.5, 1.0, 0.5);
        manager.select(player, top);
        player.sendMessage(String.format("場所を選びました: %s (%.1f, %.1f, %.1f)",
                top.getWorld().getName(), top.getX(), top.getY(), top.getZ()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearSelection(event.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Chunk chunk = event.getChunk();
        //イベントの最中にエンティティを増減させないよう、次の tick で確かめる
        Bukkit.getScheduler().runTask(plugin, () -> manager.reconcile(chunk));
    }
}
