package com.github.jikoo.worldeditutilities;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;

/**
 * 
 * 
 * @author Jikoo
 */
public class WorldEditUtilities extends JavaPlugin {

	private final String regionFileFormat = "r.%s.%s.mca";

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equals("deletechunks")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Command must be issued by a player!");
				return true;
			}
			if (!getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
				sender.sendMessage("WorldEdit has been disabled!");
				return true;
			}
			Player player = (Player) sender;
			WorldEditPlugin worldEdit = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
			Selection selection = worldEdit.getSelection(player);
			if (selection == null) {
				sender.sendMessage("Your selection is not set!");
				return true;
			}
			int startX = selection.getMinimumPoint().getChunk().getX();
			int startZ = selection.getMinimumPoint().getChunk().getZ();
			int endX = selection.getMaximumPoint().getChunk().getX() - startX + 1;
			int endZ = selection.getMinimumPoint().getChunk().getZ() - startZ + 1;
			HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, Integer>>> toDelete = new HashMap<>();
			for (int chunkX = startX; chunkX < endX; chunkX++) {
				for (int chunkZ = startZ; chunkZ < endZ; chunkZ++) {
					Pair<Integer, Integer> region = new ImmutablePair<>(chunkX >> 5, chunkZ >> 5);
					if (!toDelete.containsKey(region)) {
						toDelete.put(region, new ArrayList<Pair<Integer, Integer>>());
					}
					toDelete.get(region).add(new ImmutablePair<>(chunkX - region.getLeft() << 5, chunkZ - region.getRight() << 5));
				}
			}
			File folder = new File(selection.getWorld().getWorldFolder(), "region");
			if (!folder.exists()) {
				folder = new File(selection.getWorld().getWorldFolder(), "DIM-1/region");
				if (!folder.exists()) {
					folder = new File(selection.getWorld().getWorldFolder(), "DIM1/region");
					if (!folder.exists()) {
						sender.sendMessage("Unable to find data folder for " + selection.getWorld().getName() + "! Please report this with the world's file structure.");
						return true;
					}
				}
			}
			for (Entry<Pair<Integer, Integer>, ArrayList<Pair<Integer, Integer>>> entry : toDelete.entrySet()) {
				File regionFile = new File(folder, String.format(regionFileFormat, entry.getKey().getLeft(), entry.getKey().getRight()));
				if (!regionFile.exists()) {
					continue;
				}
				if (entry.getValue().size() == 1024) {
					if (!regionFile.delete()) {
						sender.sendMessage("Unable to delete " + regionFile.getName() + "! Some chunks will not be deleted.");
					}
					continue;
				}
				if (!regionFile.canWrite() && !regionFile.setWritable(true) && !regionFile.canWrite()) {
					sender.sendMessage("Unable to write " + regionFile.getName() + "! Some chunks will not be deleted.");
					continue;
				}
				try (RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "rwd")) {
					for (Pair<Integer, Integer> chunkCoords : entry.getValue()) {
						// Pointers for chunks are 4 byte integers stored at coordinates relative to the region file itself.
						long chunkPointer = 4 * (chunkCoords.getLeft() + chunkCoords.getRight() * 32);
						regionRandomAccess.seek(chunkPointer);
						regionRandomAccess.writeInt(0);
					}
				} catch (IOException ex) {}
			}
		}
		return false;
	}
}
