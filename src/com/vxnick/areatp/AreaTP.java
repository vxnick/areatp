package com.vxnick.areatp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
    }
	
	@Override
	public void onDisable() {
		
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("atp")) {
			if (!perms.has(sender, "areatp.use")) {
				sender.sendMessage(ChatColor.RED + "Sorry, you don't have access to areaTP");
				return true;
			}
			
			String command;
			
			if (args.length > 0) {
				command = args[0].toLowerCase();
			} else {
				// Show owned area teleports
				List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", sender.getName()));
				Collections.sort(playerAreas);
				
				Integer usedAreas = playerAreas.size();
				String totalAreas;
				
				if (perms.has(sender, "areatp.unlimited")) {
					totalAreas = "unlimited";
				} else {
					totalAreas = String.valueOf(getConfig().getInt(String.format("groups.%s.limit", perms.getPrimaryGroup((String) null, sender.getName())), 0));
				}
				
				sender.sendMessage(ChatColor.GOLD + "Your Area Teleports");
				sender.sendMessage(ChatColor.YELLOW + String.format("%d/%s area teleports", usedAreas, totalAreas));
				
				if (usedAreas > 0) {
					StringBuilder areaList = new StringBuilder();
					
					for (String areaName : playerAreas) {
						areaList.append(areaName + ", ");
					}
					
					sender.sendMessage(areaList.toString().replaceAll(", $", ""));
				} else {
					sender.sendMessage(ChatColor.YELLOW + "No area teleports belong to you");
				}
				
				return true;
			}
			
			if (perms.has(sender, "areatp.admin.reload") && command.equals("reload")) {
				sender.sendMessage(ChatColor.YELLOW + "Reloading configuration");
				reloadConfig();
				return true;
			} else if (command.equals("remove")) {
				if (args.length < 2) {
					sender.sendMessage(ChatColor.RED + "Please specify an area teleport to remove");
					return true;
				}
				
				String areaName = args[1].toLowerCase();
				
				String areaOwner = getConfig().getString(String.format("areas.%s.owner", areaName));
				
				if (areaOwner == null) {
					sender.sendMessage(ChatColor.RED + "The area teleport specified does not exist");
					return true;
				}
				
				// Remove the area teleport
				if (areaOwner.equals(sender.getName()) || perms.has(sender, "areatp.admin.remove")) {
					getConfig().set(String.format("areas.%s", areaName), null);
					
					List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", areaOwner));
					
					if (playerAreas.contains(areaName)) {
						playerAreas.remove(areaName);
						getConfig().set(String.format("players.%s.areas", areaOwner), playerAreas);
					}
					
					saveConfig();
					
					sender.sendMessage(ChatColor.GREEN + "Area teleport removed");
					return true;
				} else {
					sender.sendMessage(ChatColor.RED + "This area teleport does not belong to you");
					return true;
				}
			} else if (command.equals("set")) {
				if (!perms.has(sender, "areatp.set")) {
					sender.sendMessage(ChatColor.RED + "Sorry, you don't have permission to set area teleports");
					return true;
				}
				if (args.length < 2) {
					sender.sendMessage(ChatColor.RED + "Please specify a name for this area teleport");
					return true;
				}
				
				String areaName = args[1].toLowerCase();
				
				// Does this already exist?
				Object areaOwner = getConfig().get(String.format("areas.%s.owner", areaName), null);
				
				if (areaOwner != null && !areaOwner.equals(sender.getName())) {
					sender.sendMessage(ChatColor.RED + "An area teleport with this name already exists");
					return true;
				}
				
				List<String> playerAreas = getConfig().getStringList(String.format("players.%s.areas", sender.getName()));
				
				// Check if this is new or already exists for this user
				if (areaOwner == null && !perms.has(sender, "areatp.unlimited")) {
					// New, so we check their limit
					Integer usedAreas = playerAreas.size();
					Integer totalAreas = getConfig().getInt(String.format("groups.%s.limit", perms.getPrimaryGroup((String) null, sender.getName())), 0);
					
					if (usedAreas >= totalAreas) {
						sender.sendMessage(ChatColor.YELLOW + "You have used your maximum number of area teleports");
						return true;
					}
				}
				
				// Get player position
				Player player = (Player) sender;
				Location playerLocation = player.getLocation();
				
				// Set this area
				getConfig().set(String.format("areas.%s.owner", areaName), sender.getName());
				getConfig().set(String.format("areas.%s.world", areaName), playerLocation.getWorld().getName());
				getConfig().set(String.format("areas.%s.x", areaName), playerLocation.getX());
				getConfig().set(String.format("areas.%s.y", areaName), playerLocation.getY());
				getConfig().set(String.format("areas.%s.z", areaName), playerLocation.getZ());
				getConfig().set(String.format("areas.%s.pitch", areaName), playerLocation.getPitch());
				getConfig().set(String.format("areas.%s.yaw", areaName), playerLocation.getYaw());
				
				// Add this area to the player's list				
				if (!playerAreas.contains(areaName)) {
					playerAreas.add(areaName);
					getConfig().set(String.format("players.%s.areas", sender.getName()), playerAreas);
				}
				
				saveConfig();
				
				sender.sendMessage(ChatColor.GREEN + "Area teleport set to your current position");
				return true;
			} else if (command.equals("help")) {
				sender.sendMessage(ChatColor.GOLD + "Area Teleport Commands");
				sender.sendMessage("/atp -- Show area TPs that you have created");
				sender.sendMessage("/atp list -- Show a list of all area TPs");
				sender.sendMessage("/atp set <name> -- Create or update an area TP");
				sender.sendMessage("/atp remove <name> -- Remove one of your area TPs");
				sender.sendMessage("/atp <name> -- Go to an area teleport");
			} else if (command.equals("list")) {
				ConfigurationSection areas = getConfig().getConfigurationSection("areas");
				
				if (areas != null) {
					List<String> areaList = new ArrayList<String>(areas.getKeys(false));
					Collections.sort(areaList);
					
					for (String area : areaList) {
						String areaOwner = getConfig().getString(String.format("areas.%s.owner", area));
						
						sender.sendMessage(ChatColor.GOLD + area + ChatColor.RESET + " (owner: " + areaOwner + ")");
					}
				} else {
					sender.sendMessage(ChatColor.YELLOW + "Nothing to list");
				}
			} else {
				// Teleport to an area
				if (args.length != 1) {
					sender.sendMessage(ChatColor.RED + "Please specify an area TP to teleport to");
				} else {
					String areaName = args[0].toLowerCase();
					
					// Does this exist?
					if (getConfig().get(String.format("areas.%s.owner", areaName), null) == null) {
						sender.sendMessage(ChatColor.RED + "The area teleport you specified does not exist");
						return true;
					}
					
					Player player = (Player) sender;
					
					String areaOwner = getConfig().getString(String.format("areas.%s.owner", areaName));
					String areaWorld = getConfig().getString(String.format("areas.%s.world", areaName));
					double areaX = getConfig().getDouble(String.format("areas.%s.x", areaName));
					double areaY = getConfig().getDouble(String.format("areas.%s.y", areaName));
					double areaZ = getConfig().getDouble(String.format("areas.%s.z", areaName));
					float areaPitch = (float) getConfig().getDouble(String.format("areas.%s.pitch", areaName));
					Float areaYaw = (float) getConfig().getDouble(String.format("areas.%s.yaw", areaName));
	
					sender.sendMessage(String.format(ChatColor.GOLD + "Teleporting you to %s (owner: %s)", areaName, areaOwner));
	
					Location newLocation = new Location(getServer().getWorld(areaWorld), areaX, areaY, areaZ, areaYaw, areaPitch);
					player.teleport(newLocation);
				}
			}
		}
		
		return true;
	}
}