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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.selebrator.lootcave.command.LootCaveCommand;
import de.selebrator.lootcave.listener.LootCaveChestListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;

public class LootCavePlugin extends JavaPlugin implements Listener {

	private static final Collector<SpecialChest, HashMap<Location, SpecialChest>, HashMap<Location, SpecialChest>> IDENTIFY_BY_LOCATION_COLLECTOR = Collector.of(
			HashMap::new,
			(chestsByLocation, nextChest) -> chestsByLocation.putIfAbsent(nextChest.getLocation(), nextChest),
			(a, b) -> a
	);
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(SpecialChest.class, new SpecialChest.Adapter())
			.create();
	public Map<Location, SpecialChest> chestsByLocation = new HashMap<>();
	public boolean blockEmptyChests;
	private Path chestsPath;

	@Override
	public void onEnable() {
		this.loadConfig();
		this.loadChests();
		Bukkit.getPluginManager().registerEvents(new LootCaveChestListener(this), this);
		Bukkit.getPluginCommand("lootcave").setExecutor(new LootCaveCommand(this));
	}

	public void loadConfig() {
		this.saveDefaultConfig();
		this.reloadConfig();
	}

	public void loadChests() {
		this.blockEmptyChests = this.getConfig().getBoolean("block_empty_chest");
		this.chestsPath = this.getDataFolder().toPath().resolve("chests.json");
		if(Files.notExists(this.chestsPath)) {
			try(InputStream defaultContent = new ByteArrayInputStream("[]".getBytes())) {
				Files.copy(defaultContent, this.chestsPath);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		try {
			this.readChestsFile();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public void readChestsFile() throws IOException {
		try(BufferedReader bufferedReader = Files.newBufferedReader(this.chestsPath)) {
			List<SpecialChest> chests = GSON.fromJson(bufferedReader, new TypeToken<List<SpecialChest>>() {
			}.getType());
			this.chestsByLocation = chests.stream().collect(IDENTIFY_BY_LOCATION_COLLECTOR);
		}
	}

	public void writeChestsFile() throws IOException {
		try(BufferedWriter bufferedWriter = Files.newBufferedWriter(this.chestsPath)) {
			GSON.toJson(this.chestsByLocation.values(), bufferedWriter);
		}
	}

	public String message(String messagePath) {
		return ChatColor.translateAlternateColorCodes('&', this.getConfig().getString(messagePath));
	}
}
