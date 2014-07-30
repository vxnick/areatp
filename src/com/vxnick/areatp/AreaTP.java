package com.vxnick.areatp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class AreaTP extends JavaPlugin {
	public static Permission perms = null;

	@Override
	public void onEnable() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
		perms = rsp.getProvider();
		
		saveDefaultConfig();
		
		// Convert/purge empty players
		getServer().getLogger().log(Level.INFO, "Purging empty players");
		
		ConfigurationSection players = getConfig().getConfigurationSection("players");
		
		if (players != null) {
			List<String> playerList = new ArrayList<String>(players.getKeys(false));
			
			for (String player : playerList) {
				List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", player));
				
				if (playerAreas.isEmpty()) {
					getConfig().set(String.format("players.%s", player), null);
				}
			}
			
			saveConfig();
		}
		
		// Convert players		
		getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
			public void run() {
				// Update areas
				ConfigurationSection areas = getConfig().getConfigurationSection("areas");
				
				if (areas != null) {
					List<String> areaList = new ArrayList<String>(areas.getKeys(false));
					
					for (String area : areaList) {
						String owner = getConfig().getString(String.format("areas.%s.owner", area));
						
						// Is this already a UUID?
						try {
							UUID.fromString(owner);
							continue;
						} catch (IllegalArgumentException e) {}
						
						try {
							String ownerUUID = UUIDFetcher.getUUIDOf(owner).toString();
							
							// Set owner UUID
							getConfig().set(String.format("areas.%s.owner", area), ownerUUID);
						} catch (Exception e) {
							getLogger().warning(String.format("Could not fetch a UUID for %s", owner));
						}
					}
					
					saveConfig();
				}
				
				// Update players
				ConfigurationSection players = getConfig().getConfigurationSection("players");
				
				if (players != null) {
					List<String> playerList = new ArrayList<String>(players.getKeys(false));
					
					for (String player : playerList) {
						// Is this already a UUID?
						try {
							UUID.fromString(player);
							continue;
						} catch (IllegalArgumentException e) {}
						
						try {
							String playerUUID = UUIDFetcher.getUUIDOf(player).toString();
							
							// Get player data
							ConfigurationSection playerData = getConfig().getConfigurationSection(String.format("players.%s", player)); 
							
							// Change player and set player data
							getConfig().set(String.format("players.%s", playerUUID), playerData);
							
							// Delete old player section
							getConfig().set(String.format("players.%s", player), null);
						} catch (Exception e) {
							getLogger().warning(String.format("Could not fetch a UUID for %s", player));
						}
					}
					
					saveConfig();
				}
			}
		});
		
		// Purge old area TPs
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			public void run() {
				int purge = getConfig().getInt("purge");
				
				if (purge > 0) {
					ConfigurationSection areas = getConfig().getConfigurationSection("areas");
					
					if (areas != null) {
						List<String> areaList = new ArrayList<String>(areas.getKeys(false));
						
						for (String area : areaList) {
							long lastVisit = getConfig().getLong(String.format("areas.%s.last_visit", area));
							
							if (lastVisit > 0 && (getUnixTime() - lastVisit >= (purge * 86400))) {
								String owner = getConfig().getString(String.format("areas.%s.owner", area));
								
								getConfig().set(String.format("areas.%s", area), null);
								
								List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", owner));
								
								if (playerAreas.contains(area)) {
									playerAreas.remove(area);
									
									// Remove player if they have no areas left
									if (playerAreas.isEmpty()) {
										playerAreas = null;
									}
									
									getConfig().set(String.format("players.%s.areas", owner), playerAreas);
									
									getServer().getLogger().log(Level.INFO, String.format("Purged '%s' (owner: %s; last visit: %s ago)", 
											area, owner, formatDuration(getUnixTime() - lastVisit)));
								}
							}
						}
						
						saveConfig();
					}
				}
			}
		}, 30 * 20L);
	}
	
	@Override
	public void onDisable() {
		
	}
	
	private boolean isDangerous(Location location) {
		// Block blacklist (for safety checks)
		List<Material> blockBlacklist = Arrays.asList(Material.LAVA, Material.STATIONARY_LAVA, Material.WATER, Material.STATIONARY_WATER,
				Material.CACTUS, Material.FIRE, Material.STONE_PLATE, Material.WOOD_PLATE, Material.GOLD_PLATE, Material.WEB);
		
		// Check a column above/below the destination
		for (int y = -2; y <= 2; y++) {
			Block block = location.getBlock().getRelative(0, y, 0);
			
			// Check for suffocation
			if (y == 0 || y == 1) {
				if (block.getType() != Material.AIR) {
					return true;
				}
			}
			
			// Check for falls (block beneath feet being air)
			if (y == -1 && block.getType() == Material.AIR) {
				return true;
			}
			
			// Check for blacklisted blocks
			if (blockBlacklist.contains(block.getType())) {
				return true;
			}
		}
		
		return false;
	}
	
	private void teleportPlayer(Player player, String areaName) {		
		if (player == null) {
			return;
		}
		
		// Does this area exist?
		if (getConfig().get(String.format("areas.%s.owner", areaName), null) == null) {
			player.sendMessage(ChatColor.RED + "The area teleport you specified does not exist");
			return;
		}
		
		// Set visit timestamp
		getConfig().set(String.format("areas.%s.last_visit", areaName), getUnixTime());
		saveConfig();
		
		UUID areaOwner = UUID.fromString(getConfig().getString(String.format("areas.%s.owner", areaName)));
		String areaWorld = getConfig().getString(String.format("areas.%s.world", areaName));
		double areaX = getConfig().getDouble(String.format("areas.%s.x", areaName));
		double areaY = getConfig().getDouble(String.format("areas.%s.y", areaName));
		double areaZ = getConfig().getDouble(String.format("areas.%s.z", areaName));
		float areaPitch = (float) getConfig().getDouble(String.format("areas.%s.pitch", areaName));
		Float areaYaw = (float) getConfig().getDouble(String.format("areas.%s.yaw", areaName));
		
		Location newLocation = new Location(getServer().getWorld(areaWorld), areaX, areaY, areaZ, areaYaw, areaPitch);
		
		if (isDangerous(newLocation) && player.getUniqueId() != areaOwner) {
			player.sendMessage(ChatColor.YELLOW + "This ATP is unsafe");
			return;
		}
		
		// Load the chunk
		getServer().getWorld(areaWorld).loadChunk(newLocation.getChunk());
		
		player.sendMessage(String.format(ChatColor.GOLD + "Teleporting you to %s (owner: %s)", 
				areaName, getServer().getOfflinePlayer(areaOwner).getName()));
		player.teleport(newLocation);
	}
	
	public void paginate(CommandSender sender, SortedMap<Integer, String> map, int page, int pageLength) {
		int maxPages = (((map.size() % pageLength) == 0) ? map.size() / pageLength : (map.size() / pageLength) + 1);
		
		if (page > maxPages) {
			page = maxPages;
		}
		
		sender.sendMessage(ChatColor.YELLOW + "Page " + String.valueOf(page) + " of " + maxPages + ChatColor.RESET);
		
		int i = 0, k = 0;
		page--;
		
		for (final Entry<Integer, String> e : map.entrySet()) {
			k++;
			if ((((page * pageLength) + i + 1) == k) && (k != ((page * pageLength) + pageLength + 1))) {
				i++;
				sender.sendMessage(e.getValue());
			}
		}
	}
	
	private long getUnixTime() {
		return (System.currentTimeMillis() / 1000L);
	}
	
	private String formatDuration(long seconds) {
		if (seconds < 60) {
			return String.format("%d second%s", seconds, (seconds == 1 ? "" : "s"));
		} else if (seconds < 3600) {
			return String.format("%d minute%s", (seconds / 60), ((seconds / 60) == 1 ? "" : "s"));
		} else if (seconds < 86400) {
			return String.format("%d hour%s", (seconds / 3600), ((seconds / 3600) == 1 ? "" : "s"));
		} else {
			return String.format("%d day%s", (seconds / 86400), ((seconds / 86400) == 1 ? "" : "s"));
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("atp")) {
			if (!perms.has(sender, "areatp.use")) {
				sender.sendMessage(ChatColor.RED + "Sorry, you don't have access to AreaTP");
				return true;
			}
			
			String command;
			
			if (args.length > 0) {
				command = args[0].toLowerCase();
			} else {
				if (sender instanceof ConsoleCommandSender) {
					sender.sendMessage(ChatColor.RED + "This command is not available through the console");
					return true;
				}
				
				Player player = (Player) sender;
				
				// Show owned area teleports
				List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", player.getUniqueId().toString()));
				Collections.sort(playerAreas);
				
				Integer usedAreas = playerAreas.size();
				String totalAreas;
				
				if (perms.has(sender, "areatp.unlimited")) {
					totalAreas = "unlimited";
				} else {
					totalAreas = String.valueOf(getConfig().getInt(String.format("groups.%s.limit", perms.getPrimaryGroup((String) null, player)), 0));
				}
				
				player.sendMessage(ChatColor.GOLD + "Your Area Teleports");
				player.sendMessage(ChatColor.YELLOW + String.format("%d/%s area teleports", usedAreas, totalAreas));
				
				if (usedAreas > 0) {
					for (String areaName : playerAreas) {
						long lastVisit = getConfig().getLong(String.format("areas.%s.last_visit", areaName));
						String lastVisitFormatted;
						
						if (lastVisit == 0) {
							lastVisitFormatted = "never";
						} else {
							lastVisitFormatted = formatDuration(getUnixTime() - lastVisit) + " ago";
						}
						
						player.sendMessage(areaName + " (last visited: " + lastVisitFormatted + ")");
					}
				} else {
					player.sendMessage(ChatColor.YELLOW + "No area teleports belong to you");
				}
				
				return true;
			}
			
			if (perms.has(sender, "areatp.admin.reload") && command.equals("reload")) {
				sender.sendMessage(ChatColor.YELLOW + "Reloading configuration");
				reloadConfig();
				return true;
			} else if (command.equals("remove")) {
				if (sender instanceof ConsoleCommandSender) {
					sender.sendMessage(ChatColor.RED + "This command is not available through the console");
					return true;
				}
				
				Player player = (Player) sender;
				
				if (args.length < 2) {
					player.sendMessage(ChatColor.RED + "Please specify an area teleport to remove");
					return true;
				}
				
				String areaName = args[1].toLowerCase();
				String areaOwner = getConfig().getString(String.format("areas.%s.owner", areaName));
				
				if (areaOwner == null) {
					player.sendMessage(ChatColor.RED + "The area teleport specified does not exist");
					return true;
				}
				
				// Remove the area teleport
				if (areaOwner.equals(player.getUniqueId().toString()) || perms.has(player, "areatp.admin.remove")) {
					getConfig().set(String.format("areas.%s", areaName), null);
					
					List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", areaOwner));
					
					if (playerAreas.contains(areaName)) {
						playerAreas.remove(areaName);
						
						// Remove player if they have no more areas
						if (playerAreas.isEmpty()) {
							getConfig().set(String.format("players.%s", areaOwner), null);
						} else {
							getConfig().set(String.format("players.%s.areas", areaOwner), playerAreas);
						}
					}
					
					saveConfig();
					
					player.sendMessage(ChatColor.GREEN + "Area teleport removed");
					return true;
				} else {
					player.sendMessage(ChatColor.RED + "This area teleport does not belong to you");
					return true;
				}
			} else if (command.equals("set")) {
				if (sender instanceof ConsoleCommandSender) {
					sender.sendMessage(ChatColor.RED + "This command is not available through the console");
					return true;
				}
				
				Player player = (Player) sender;
				
				if (!perms.has(player, "areatp.set")) {
					player.sendMessage(ChatColor.RED + "Sorry, you don't have permission to set area teleports");
					return true;
				}
				if (args.length < 2) {
					player.sendMessage(ChatColor.RED + "Please specify a name for this area teleport");
					return true;
				}
				
				String areaName = args[1].toLowerCase();
				
				// Does this already exist?
				Object areaOwner = getConfig().get(String.format("areas.%s.owner", areaName), null);
				
				if (areaOwner != null && !areaOwner.equals(player.getUniqueId().toString())) {
					player.sendMessage(ChatColor.RED + "An area teleport with this name already exists");
					return true;
				}
				
				List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", player.getUniqueId().toString()));
				
				// Check if this is new or already exists for this user
				if (areaOwner == null && !perms.has(player, "areatp.unlimited")) {
					// New, so we check their limit
					Integer usedAreas = playerAreas.size();
					Integer totalAreas = getConfig().getInt(String.format("groups.%s.limit", perms.getPrimaryGroup((String) null, player)), 0);
					
					if (usedAreas >= totalAreas) {
						player.sendMessage(ChatColor.YELLOW + "You have used your maximum number of area teleports");
						return true;
					}
				}
				
				// Get player position
				Location playerLocation = player.getLocation();
				
				// Check if the area is dangerous
				if (isDangerous(playerLocation)) {
					player.sendMessage(ChatColor.YELLOW + "This area is unsafe - please choose another or remove dangerous blocks");
					return true;
				}
				
				// Set this area
				getConfig().set(String.format("areas.%s.owner", areaName), player.getUniqueId().toString());
				getConfig().set(String.format("areas.%s.world", areaName), playerLocation.getWorld().getName());
				getConfig().set(String.format("areas.%s.x", areaName), playerLocation.getX());
				getConfig().set(String.format("areas.%s.y", areaName), playerLocation.getY());
				getConfig().set(String.format("areas.%s.z", areaName), playerLocation.getZ());
				getConfig().set(String.format("areas.%s.pitch", areaName), playerLocation.getPitch());
				getConfig().set(String.format("areas.%s.yaw", areaName), playerLocation.getYaw());
				
				// Add this area to the player's list
				if (!playerAreas.contains(areaName)) {
					playerAreas.add(areaName);
					getConfig().set(String.format("players.%s.areas", player.getUniqueId().toString()), playerAreas);
				}
				
				saveConfig();
				
				player.sendMessage(ChatColor.GREEN + "Area teleport set to your current position");
				return true;
			} else if (command.equals("help")) {
				sender.sendMessage(ChatColor.GOLD + "Area Teleport Commands");
				sender.sendMessage("/atp -- Show area TPs that you have created");
				sender.sendMessage("/atp list [page] -- Show a list of all area TPs");
				sender.sendMessage("/atp set <name> -- Create or update an area TP");
				sender.sendMessage("/atp remove <name> -- Remove one of your area TPs");
				sender.sendMessage("/atp <name> -- Go to an area teleport");
			} else if (command.equals("list")) {
				ConfigurationSection areas = getConfig().getConfigurationSection("areas");
				
				if (areas != null) {
					List<String> areaList = new ArrayList<String>(areas.getKeys(false));
					Collections.sort(areaList);
					SortedMap<Integer, String> map = new TreeMap<Integer, String>();
					int i = 1;
					
					for (String area : areaList) {
						String areaOwner = getConfig().getString(String.format("areas.%s.owner", area));
						map.put(i, ChatColor.GOLD + area + ChatColor.RESET + " (owner: " + getServer().getOfflinePlayer(UUID.fromString(areaOwner)).getName() + ")");
						i++;
					}
					
					try {
						int pageNumber;
						if (args.length == 1) {
							pageNumber = 1;
						} else {
							pageNumber = Integer.valueOf(args[1]);
						}
						
						sender.sendMessage(ChatColor.GOLD + "Area TP List" + ChatColor.RESET);
						paginate(sender, map, pageNumber, getConfig().getInt("page_results", 10));
					} catch (NumberFormatException e) {
						sender.sendMessage(ChatColor.RED + "Please specify a page number");
					}
				} else {
					sender.sendMessage(ChatColor.YELLOW + "Nothing to list");
				}
			} else {
				if (sender instanceof ConsoleCommandSender) {
					sender.sendMessage(ChatColor.RED + "This command is not available through the console");
					return true;
				}
				
				final Player player = (Player) sender;
				
				// Teleport to an area
				if (args.length != 1) {
					player.sendMessage(ChatColor.RED + "Please specify an area TP to teleport to");
				} else {
					final String areaName = args[0].toLowerCase();
					
					// Does this exist?
					if (getConfig().get(String.format("areas.%s.owner", areaName), null) == null) {
						player.sendMessage(ChatColor.RED + "The area teleport you specified does not exist");
						return true;
					}
					
					if (getConfig().getInt("tp_delay", 0) > 0 && !perms.playerHas((String) null, player, "areatp.bypass")) {
						int tpDelay = getConfig().getInt("tp_delay");
						
						player.sendMessage(ChatColor.GOLD + String.format("Teleporting you in %d seconds...", tpDelay));
						
						getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
							public void run() {
								teleportPlayer(player, areaName);
							}
						}, tpDelay * 20L);
					} else {
						teleportPlayer(player, areaName);
					}
				}
			}
		}
		
		return true;
	}
}