/*
 * Copyright (C) 2019  Selebrator
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.selebrator.lootcave.listener;

import de.selebrator.lootcave.LootCavePlugin;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class LootCaveChestListener implements Listener {

	private final LootCavePlugin plugin;

	public LootCaveChestListener(LootCavePlugin plugin) {
		this.plugin = plugin;
	}

	private static boolean isEmpty(Inventory inventory) {
		for(ItemStack item : inventory.getStorageContents()) {
			if(item != null) {
				return false;
			}
		}
		return true;
	}

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		Optional.of(event)
				.map(InventoryEvent::getInventory)
				.map(Inventory::getLocation)
				.map(this.plugin.chestsByLocation::get)
				.ifPresent(specialChest -> {
					event.setCancelled(true);
					HumanEntity player = event.getPlayer();
					if(this.plugin.blockEmptyChests && specialChest.getInventory(player.getUniqueId()) != null && isEmpty(specialChest.getInventory(player.getUniqueId()))) {
						player.sendMessage(this.plugin.message("message.event.chest_already_looted"));
						return;
					}
					specialChest.open(player, ThreadLocalRandom.current());
				});
	}
}
