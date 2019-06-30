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

package de.selebrator.lootcave.command;

import de.selebrator.lootcave.LootCavePlugin;
import de.selebrator.lootcave.SpecialChest;
import joptsimple.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class LootCaveCommand implements CommandExecutor {

	private final LootCavePlugin plugin;

	public LootCaveCommand(LootCavePlugin plugin) {
		this.plugin = plugin;
	}

	private static String[] betterArgs(String[] args) {
		List<String> betterArgs = new ArrayList<>(args.length);
		StringJoiner temp = new StringJoiner(" ");
		boolean waiting = false;
		for(String arg : args) {
			boolean qs = arg.startsWith("\"") && !arg.startsWith("\\\"");
			boolean qe = arg.endsWith("\"") && !arg.endsWith("\\\"");
			arg = arg.replace("\\\"", "\"");

			if(!waiting && qs && qe) {
				betterArgs.add(arg.substring(1, arg.length() - 1));
				continue;
			}

			if(!waiting && qs) {
				waiting = true;
				temp.add(arg.substring(1));
				continue;
			} else if(waiting && qe) {
				temp.add(arg.substring(0, arg.length() - 1));
				betterArgs.add(temp.toString());
				temp = new StringJoiner(" ");
				waiting = false;
				continue;
			}

			if(waiting)
				temp.add(arg);
			else
				betterArgs.add(arg);
		}
		return betterArgs.toArray(new String[0]);
	}

	private static Location getLocation(CommandSender sender, List<String> args) {
		if(args.isEmpty() && sender instanceof Player) {
			return Optional.ofNullable(((Player) sender).getTargetBlockExact(7))
					.map(Block::getLocation)
					.orElse(null);
		} else if(args.size() == 3 && sender instanceof Player) {
			return new Location(
					((Player) sender).getWorld(),
					Double.valueOf(args.get(0)),
					Double.valueOf(args.get(1)),
					Double.valueOf(args.get(2))
			);
		} else if(args.size() == 4) {
			return new Location(
					Bukkit.getWorld(args.get(3)),
					Double.valueOf(args.get(0)),
					Double.valueOf(args.get(1)),
					Double.valueOf(args.get(2))
			);
		} else {
			return null;
		}
	}

	private static void sendOrBroadcast(CommandSender sender, String message, boolean broadcast) {
		if(broadcast) {
			Bukkit.broadcastMessage(message);
		} else {
			sender.sendMessage(message);
		}
	}

	//just a shortcut
	private String message(String messagePath) {
		return this.plugin.message(messagePath);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!sender.hasPermission("lootcave.admin")) {
			sender.sendMessage(message("message.command.no_permission"));
			return true;
		}
		if(args.length == 0) {
			sender.sendMessage(
					this.plugin.getName() + ChatColor.GRAY + " version " +
							ChatColor.AQUA + this.plugin.getDescription().getVersion() + "\n" +
							ChatColor.GRAY + "Created by " +
							ChatColor.AQUA + String.join(", ", this.plugin.getDescription().getAuthors()) + "\n" +
							ChatColor.GRAY + "Try '/" + label + " help' for more information"
			);
			return true;
		}
		String[] betterSubArgs = betterArgs(Arrays.copyOfRange(args, 1, args.length));
		try {
			switch(args[0]) {
				case "help":
					sender.sendMessage("Not implemented yet :("); //TODO implement help command
					return true;
				case "reload":
					this.plugin.loadConfig();
					this.plugin.loadChests();
					sender.sendMessage(message("message.command.reload.success"));
					return true;
				case "add":
					return onAddCommand(sender, betterSubArgs);
				case "place":
					return onPlaceCommand(sender, betterSubArgs);
				case "remove":
					return onRemoveCommand(sender, betterSubArgs);
				default:
					return false;
			}
		} catch(OptionException e) {
			return false;
		}
	}

	/*
	 * [OPTION]... <lootTable> [<x> <y> <z> [<world>]]
	 *
	 * -p <probability>
	 * --probability=<probability>
	 *
	 * -n <customName>
	 * --name=<customName>
	 *
	 * -l <lock>
	 * --lock=<lock>
	 *
	 * -f <facing>
	 * --facing=<facing>
	 * <facing> out of { n, s, w, e, north, south, west, east }
	 *
	 * -w
	 * --waterlogged
	 *
	 * -g
	 * --groups
	 *
	 * -B
	 * --broadcast
	 */

	//uses betterArgs (important for name and lock)
	private boolean onAddCommand(CommandSender sender, String[] args) {
		OptionParser parser = new OptionParser(false);
		parser.posixlyCorrect(true);
		OptionSpec<Double> probability = parser.acceptsAll(Arrays.asList("p", "probability")).withRequiredArg().ofType(Double.class);
		OptionSpec<String> name = parser.acceptsAll(Arrays.asList("n", "name")).withRequiredArg();
		OptionSpec<String> lock = parser.acceptsAll(Arrays.asList("l", "lock")).withRequiredArg();
		OptionSpec<BlockFace> facing = parser.acceptsAll(Arrays.asList("f", "facing")).withRequiredArg().withValuesConvertedBy(new BlockFaceConverter());
		OptionSpec<Void> waterlogged = parser.acceptsAll(Arrays.asList("w", "waterlogged"));
		OptionSpec<String> groups = parser.acceptsAll(Arrays.asList("g", "groups")).withRequiredArg();
		OptionSpec<Void> broadcast = parser.acceptsAll(Arrays.asList("B", "broadcast"));

		OptionSet options = parser.parse(args);
		@SuppressWarnings("unchecked") List<String> rest = (List<String>) options.nonOptionArguments();
		if(rest.isEmpty()) {
			return false;
		}
		String lootTable = rest.get(0);
		Location location = getLocation(sender, rest.subList(1, rest.size()));
		if(location == null) {
			sender.sendMessage(message("message.command.add.error_location_missing"));
			return false;
		}

		SpecialChest.Builder chest = new SpecialChest.Builder(location, lootTable);
		if(options.has(probability)) {
			chest.setProbability(probability.value(options));
		}
		if(options.has(name)) {
			chest.setCustomName(name.value(options));
		}
		if(options.has(lock)) {
			chest.setLock(lock.value(options));
		}
		if(options.has(facing)) {
			chest.setFacing(facing.value(options));
		}
		if(options.has(waterlogged)) {
			chest.setWaterlogged(true);
		}
		if(options.has(groups)) {
			chest.addGroups(Arrays.asList(groups.value(options).split(",")));
		}

		boolean overwriting = this.plugin.chestsByLocation.containsKey(location);

		SpecialChest build = chest.build();
		this.plugin.chestsByLocation.put(build.getLocation(), build);

		try {
			this.plugin.writeChestsFile();
		} catch(IOException e) {
			e.printStackTrace();
			sender.sendMessage(message("message.command.add.error_file_write"));
		}

		String message = message("message.command.add.success_" + (overwriting ? "change" : "new"));
		sendOrBroadcast(sender, message, options.has(broadcast));
		return true;
	}

	/*
	 * [OPTION]... [(<x> <y> <z> [<world>])|<groups>]
	 *
	 * --ignore-probability
	 * make every chest spawn, no matter it's probability
	 *
	 * -f
	 * --force
	 * make the chests replace the block where it wants to be
	 *
	 * -B
	 * --broadcast
	 */
	private boolean onPlaceCommand(CommandSender sender, String[] args) {
		OptionParser parser = new OptionParser(false);
		parser.posixlyCorrect(true);
		OptionSpec<Void> ignoreProbability = parser.accepts("ignore-probability");
		OptionSpec<Void> force = parser.acceptsAll(Arrays.asList("f", "force"));
		OptionSpec<Void> broadcast = parser.acceptsAll(Arrays.asList("B", "broadcast"));

		OptionSet options = parser.parse(args);
		@SuppressWarnings("unchecked") List<String> rest = (List<String>) options.nonOptionArguments();

		Set<SpecialChest> chests;
		if(rest.size() == 1) {
			List<String> groups = Arrays.asList(rest.get(0).split(","));
			chests = this.plugin.chestsByLocation.values().stream()
					.filter(chest -> chest.isInAnyGroup(groups))
					.collect(Collectors.toSet());
		} else {
			Location location = getLocation(sender, rest);
			if(location == null) {
				sender.sendMessage(message("message.command.place.error_location_missing"));
				return false;
			}
			if(!this.plugin.chestsByLocation.containsKey(location)) {
				sender.sendMessage(message("message.command.place.error_location_invalid"));
				return true;
			}
			chests = Collections.singleton(this.plugin.chestsByLocation.get(location));
		}

		long placedChests = chests.stream()
				.filter(specialChest -> options.has(ignoreProbability)
						? specialChest.place(options.has(force))
						: specialChest.place(options.has(force), ThreadLocalRandom.current()))
				.count();
		String message = message("message.command.place.success_" + (placedChests == 1 ? "single" : "multiple")).replace("%count%", String.valueOf(placedChests));
		sendOrBroadcast(sender, message, options.has(broadcast));
		return true;
	}

	/*
	 * [OPTION]... [(<x> <y> <z> [<world>])|<groups>]
	 *
	 * -f
	 * --file
	 * remove the chest(s) from the save file
	 *
	 * -w
	 * --world
	 * remove the chest(s) from the world
	 *
	 * -i
	 * --inventory
	 * clear the chest's inventory
	 *
	 * -B
	 * --broadcast
	 */
	private boolean onRemoveCommand(CommandSender sender, String[] args) {
		OptionParser parser = new OptionParser(false);
		parser.posixlyCorrect(true);
		OptionSpec<Void> inventory = parser.acceptsAll(Arrays.asList("i", "inventory"));
		OptionSpec<Void> world = parser.acceptsAll(Arrays.asList("w", "world"));
		OptionSpec<Void> file = parser.acceptsAll(Arrays.asList("f", "file"));
		OptionSpec<Void> broadcast = parser.acceptsAll(Arrays.asList("B", "broadcast"));

		OptionSet options = parser.parse(args);
		@SuppressWarnings("unchecked") List<String> rest = (List<String>) options.nonOptionArguments();

		if(!(options.has(inventory) || options.has(world) || options.has(file))) {
			return false;
		}

		Collection<SpecialChest> chests;
		if(rest.size() == 1) {
			List<String> groups = Arrays.asList(rest.get(0).split(","));
			chests = this.plugin.chestsByLocation.values().stream()
					.filter(chest -> chest.isInAnyGroup(groups))
					.collect(Collectors.toSet());
		} else {
			Location location = getLocation(sender, rest);
			if(location == null) {
				sender.sendMessage(message("message.command.remove.error_location_missing"));
				return false;
			}
			if(!this.plugin.chestsByLocation.containsKey(location)) {
				sender.sendMessage(message("message.command.remove.error_location_invalid"));
				return true;
			}
			chests = Collections.singleton(this.plugin.chestsByLocation.get(location));
		}

		if(options.has(inventory)) {
			chests.forEach(SpecialChest::resetInventory);
			String message = message("message.command.remove.success_inventory_" + (chests.size() == 1 ? "single" : "multiple")).replace("%count%", String.valueOf(chests.size()));
			sendOrBroadcast(sender, message, options.has(broadcast));
		}
		if(options.has(world)) {
			long removedCount = chests.stream()
					.filter(SpecialChest::remove)
					.count();
			String message = message("message.command.remove.success_world_" + (removedCount == 1 ? "single" : "multiple")).replace("%count%", String.valueOf(removedCount));
			sendOrBroadcast(sender, message, options.has(broadcast));
		}
		if(options.has(file)) {
			chests.forEach(specialChest -> this.plugin.chestsByLocation.remove(specialChest.getLocation()));
			try {
				this.plugin.writeChestsFile();
				String message = message("message.command.remove.success_file_" + (chests.size() == 1 ? "single" : "multiple")).replace("%count%", String.valueOf(chests.size()));
				sendOrBroadcast(sender, message, options.has(broadcast));
			} catch(IOException e) {
				e.printStackTrace();
				sender.sendMessage(message("message.command.remove.error_file_write"));
				return true;
			}
		}
		return true;
	}

	private static class BlockFaceConverter implements ValueConverter<BlockFace> {

		@Override
		public BlockFace convert(String value) {
			switch(value.toUpperCase(Locale.ROOT)) {
				case "N":
				case "NORTH":
					return BlockFace.NORTH;
				case "E":
				case "EAST":
					return BlockFace.EAST;
				case "S":
				case "SOUTH":
					return BlockFace.SOUTH;
				case "W":
				case "WEST":
					return BlockFace.WEST;
				default:
					throw new ValueConversionException("\"" + value + "\" is not a valid direction for a chest to face in");
			}
		}

		@Override
		public Class<? extends BlockFace> valueType() {
			return BlockFace.class;
		}

		@Override
		public String valuePattern() {
			return "n, north, e, east, s, south, w, west";
		}
	}
}
