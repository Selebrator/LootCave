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

package de.selebrator.lootcave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.io.IOException;
import java.util.*;

public class SpecialChest {
	private final Location location;
	private final LootTable lootTable;
	private final double probability;
	private final String customName;
	private final String lock;
	private final BlockFace facing;
	private final boolean waterlogged;
	private Set<String> groups;

	private Map<UUID, Inventory> inventoryByPlayerUuid = new HashMap<>();

	private SpecialChest(Location location, LootTable lootTable, double probability, String customName, String lock, BlockFace facing, boolean waterlogged, Set<String> groups) {
		this.location = location;
		this.lootTable = lootTable;
		this.probability = probability;
		this.customName = customName;
		this.lock = lock;
		this.facing = facing;
		this.waterlogged = waterlogged;
		this.groups = groups;
	}

	public InventoryView open(HumanEntity player, Random random) {
		return player.openInventory(this.inventoryByPlayerUuid.computeIfAbsent(player.getUniqueId(), uuid -> {
			final Inventory inventory;
			if(this.customName != null) {
				inventory = Bukkit.createInventory(player, InventoryType.CHEST, ChatColor.translateAlternateColorCodes('&', this.customName));
			} else {
				inventory = Bukkit.createInventory(player, InventoryType.CHEST);
			}
			final LootContext lootContext = new LootContext.Builder(this.location)
					.luck((float) player.getAttribute(Attribute.GENERIC_LUCK).getValue())
					.build();
			this.lootTable.fillInventory(inventory, random, lootContext);
			return inventory;
		}));
	}

	public void resetInventory() {
		this.inventoryByPlayerUuid = new HashMap<>();
	}

	public boolean place(boolean force, Random random) {
		return this.probability > random.nextDouble() && this.place(force);
	}

	//return true if chest was placed
	public boolean place(boolean force) {
		final Block block = this.location.getBlock();
		if(!force && !block.isEmpty()) {
			return false;
		}
		block.setType(Material.CHEST);
		final org.bukkit.block.Chest state = (org.bukkit.block.Chest) block.getState();
		final org.bukkit.block.data.type.Chest data = (org.bukkit.block.data.type.Chest) state.getBlockData();
		data.setType(Chest.Type.SINGLE);
		if(this.customName != null) {
			state.setCustomName(ChatColor.translateAlternateColorCodes('&', this.customName));
		}
		if(this.lock != null) {
			state.setLock(ChatColor.translateAlternateColorCodes('&', this.lock));
		}
		if(this.facing != null) {
			data.setFacing(this.facing);
		}
		data.setWaterlogged(this.waterlogged);
		state.setBlockData(data);
		state.update(force);
		this.resetInventory();
		return true;
	}

	public boolean remove() {
		final Block block = this.location.getBlock();
		if(block.getType() == Material.CHEST) {
			block.setType(((Chest) block.getBlockData()).isWaterlogged() ? Material.WATER : Material.AIR);
			return true;
		}
		return false;
	}

	public boolean isInGroup(String group) {
		return group.equals("all") || this.groups.contains(group);
	}

	public boolean isInAnyGroup(Collection<String> groups) {
		return groups.contains("all") || this.groups.stream().anyMatch(groups::contains);
	}

	public Inventory getInventory(UUID uuid) {
		return this.inventoryByPlayerUuid.get(uuid);
	}

	public Inventory getInventory(OfflinePlayer player) {
		return this.getInventory(player.getUniqueId());
	}

	public Location getLocation() {
		return this.location;
	}

	public LootTable getLootTable() {
		return this.lootTable;
	}

	public double getProbability() {
		return this.probability;
	}

	public String getCustomName() {
		return this.customName;
	}

	public String getLock() {
		return this.lock;
	}

	public BlockFace getFacing() {
		return this.facing;
	}

	public boolean isWaterlogged() {
		return this.waterlogged;
	}

	public static class Adapter extends TypeAdapter<SpecialChest> {

		@Override
		public void write(JsonWriter out, SpecialChest value) throws IOException {
			out.beginObject();
			out.name("location")
					.beginObject()
					.name("world").value(value.location.getWorld().getName())
					.name("x").value(value.location.getBlockX())
					.name("y").value(value.location.getBlockY())
					.name("z").value(value.location.getBlockZ())
					.endObject();
			out.name("loot_table").value(value.lootTable.getKey().toString());
			out.name("probability").value(value.probability);
			if(value.customName != null) {
				out.name("custom_name").value(value.customName);
			}
			if(value.lock != null) {
				out.name("lock").value(value.lock);
			}
			if(value.facing != null) {
				out.name("facing").value(value.facing.toString());
			}
			if(value.waterlogged) {
				out.name("waterlogged").value(true);
			}
			if(!value.groups.isEmpty()) {
				out.name("groups").beginArray();
				for(String group : value.groups) {
					out.value(group);
				}
				out.endArray();
			}
			out.endObject();
		}

		@Override
		public SpecialChest read(JsonReader in) {
			JsonElement parsedJson = new JsonParser().parse(in);
			final JsonObject jObject = parsedJson.getAsJsonObject();

			final JsonObject jLocation = jObject.getAsJsonObject("location");
			final String worldName = jLocation.getAsJsonPrimitive("world").getAsString();
			final World world = Bukkit.getWorld(worldName);
			final double x = jLocation.getAsJsonPrimitive("x").getAsDouble();
			final double y = jLocation.getAsJsonPrimitive("y").getAsDouble();
			final double z = jLocation.getAsJsonPrimitive("z").getAsDouble();
			final Location location = new Location(world, x, y, z);

			final SpecialChest.Builder chest = new SpecialChest.Builder(location, jObject.getAsJsonPrimitive("loot_table").getAsString());

			if(jObject.has("probability")) {
				chest.setProbability(jObject.getAsJsonPrimitive("probability").getAsDouble());
			}
			if(jObject.has("custom_name")) {
				chest.setCustomName(jObject.getAsJsonPrimitive("custom_name").getAsString());
			}
			if(jObject.has("lock")) {
				chest.setLock(jObject.getAsJsonPrimitive("lock").getAsString());
			}
			if(jObject.has("facing")) {
				final String directionName = jObject.getAsJsonPrimitive("facing").getAsString();
				chest.setFacing(BlockFace.valueOf(directionName.toUpperCase(Locale.ROOT)));
			}
			if(jObject.has("waterlogged")) {
				chest.setWaterlogged(jObject.getAsJsonPrimitive("waterlogged").getAsBoolean());
			}
			if(jObject.has("groups")) {
				jObject.getAsJsonArray("groups").forEach(jsonElement -> chest.addGroup(jsonElement.getAsString()));
			}

			return chest.build();
		}
	}

	@SuppressWarnings("WeakerAccess")
	public static class Builder {
		private Location location;
		private LootTable lootTable;
		private double probability = 1.0d;
		private String customName = null;
		private String lock = null;
		private BlockFace facing = null;
		private boolean waterlogged = false;
		private Set<String> groups = new HashSet<>();

		public Builder(Location location, LootTable lootTable) {
			this.location = location;
			this.lootTable = lootTable;
		}

		public Builder(Location location, String lootTable) {
			this.location = location;
			this.setLootTable(lootTable);
		}

		public Builder(SpecialChest original) {
			this.location = original.location.clone();
			this.lootTable = original.lootTable;
			this.probability = original.probability;
			this.customName = original.customName;
			this.lock = original.lock;
			this.facing = original.facing;
			this.waterlogged = original.waterlogged;
			this.groups = original.groups;
		}

		public Location getLocation() {
			return this.location;
		}

		public void setLocation(Location location) {
			this.location = location;
		}

		public LootTable getLootTable() {
			return this.lootTable;
		}

		public void setLootTable(LootTable lootTable) {
			this.lootTable = lootTable;
		}

		public void setLootTable(String namespacedLootTable) {
			final String[] lootTableNameParts = namespacedLootTable.split(":", 2);
			//noinspection deprecation
			setLootTable(Bukkit.getLootTable(new NamespacedKey(lootTableNameParts[0], lootTableNameParts[1])));
		}

		public double getProbability() {
			return this.probability;
		}

		public void setProbability(double probability) {
			this.probability = probability;
		}

		public String getCustomName() {
			return this.customName;
		}

		public void setCustomName(String customName) {
			this.customName = customName;
		}

		public String getLock() {
			return this.lock;
		}

		public void setLock(String lock) {
			this.lock = lock;
		}

		public BlockFace getFacing() {
			return this.facing;
		}

		public void setFacing(BlockFace facing) {
			this.facing = facing;
		}

		public boolean isWaterlogged() {
			return this.waterlogged;
		}

		public void setWaterlogged(boolean waterlogged) {
			this.waterlogged = waterlogged;
		}

		public Set<String> getGroups() {
			return this.groups;
		}

		public void setGroups(Set<String> groups) {
			this.groups = groups;
		}

		public void addGroup(String group) {
			this.groups.add(group);
		}

		public void addGroups(Collection<String> groups) {
			this.groups.addAll(groups);
		}

		public SpecialChest build() {
			return new SpecialChest(
					this.location,
					this.lootTable,
					this.probability,
					this.customName,
					this.lock,
					this.facing,
					this.waterlogged,
					this.groups
			);
		}
	}
}
