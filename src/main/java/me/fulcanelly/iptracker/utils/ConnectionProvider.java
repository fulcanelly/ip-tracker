package me.fulcanelly.iptracker.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

import org.bukkit.plugin.java.JavaPlugin;

import lombok.SneakyThrows;

public class ConnectionProvider {
    
    JavaPlugin plugin;

    public ConnectionProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SneakyThrows
	public Connection getConnection() {
        var path = new File(this.plugin.getDataFolder(), "database.sqlite3").toString();
        return DriverManager.getConnection("jdbc:sqlite:" + path);
    }

}