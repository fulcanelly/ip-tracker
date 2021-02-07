package me.fulcanelly.iptracker;

import org.bukkit.event.server.ServerListPingEvent;

import java.util.stream.Collectors;

import me.fulcanelly.clsql.databse.SQLQueryHandler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import lombok.SneakyThrows;
import me.fulcanelly.iptracker.utils.*;


class IpPingCount {
    
    String ip;
    Integer count = 0;
    
    IpPingCount() {}

    IpPingCount(String ip) {
        this.ip = ip;
    }

    void createIn(SQLQueryHandler db) {
        db.syncExecuteUpdate("INSERT INTO ips VALUES(?, ?)", ip, count);
    }

    void updateIn(SQLQueryHandler db) {
        db.syncExecuteUpdate("UPDATE names SET ip = ?, count = ?", ip, count);
    }

    static IpPingCount of(Map<String, Object> map) {
        var obj = new IpPingCount();

        obj.ip = (String)map.get("ip");
        obj.count = (Integer)map.get("count");;

        return obj;
    }
}

public class IpTracker extends JavaPlugin implements Listener {

    @EventHandler
    void onPlayerPing(ServerListPingEvent event) {
        
        var ip = event
            .getAddress()
            .toString();
    
        sql.executeQuery("SELECT * FROM ips WHERE ip = ?", ip)
            .andThen(sql::safeParseOne)
            .andThen(map -> { 
                
                if (map == null) {
                    var ipcount = new IpPingCount(ip);
                    ipcount.createIn(sql);
                    return ipcount;
                } else {
                    return IpPingCount.of(map);
                }
        
            })
            .andThenSilently(ipcount -> {
                ipcount.count++;

                var namesList = sql.parseListOf(
                    sql.syncExecuteQuery("SELECT * FROM names WHERE ip = ?", ip)
                );

                var names = namesList.stream()
                    .map(item -> item.get("name").toString())
                    .map(name -> name.substring(0, name.length() - 2))
                    .collect(Collectors.joining(", "));

                //
                var builder = new StringBuilder();
                
                builder.append("got ping from " + ip + "ip for " + ipcount.count + " time");
                
                if (namesList.size() > 0) {
                    builder.append(", which was used by " + names);
                }

                getLogger().info(builder.toString());
                //

                ipcount.updateIn(sql);
            });
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        
        var player = event.getPlayer();
        var name = player.getName();

        var ip = player
            .getAddress()
            .getAddress()
            .toString();
        
        sql.executeQuery("SELECT * FROM names WHERE ip = ? AND nick = ?", ip, name)
            .andThen(sql::safeParseOne)
            .andThenSilently(it -> {
                if (it == null) {
                    sql.syncExecuteUpdate("INSERT INTO names VALUES(?, ?)", ip, name + "_");
                } 
            });
        
    }

    SQLQueryHandler sql = null;

    void regListeners(Listener ...listeners) {
        for (var one : listeners) {
            this.getServer()
                .getPluginManager()
                .registerEvents(one, this);
        }
    }

    void initTables() {
            
        sql.execute(
            "CREATE TABLE IF NOT EXISTS ips(" +
            "   STRING ip,".trim() +
            "   INTEGER count".trim() +
            ")"
        );
        sql.execute(
            "CREATE TABLE IF NOT EXISTS names(" +
            "    STRING ip,".trim() +
            "    STRING nick".trim() +
            ")"
        );

    }

    public void onEnable() {

        this.getDataFolder().mkdir();
        
        sql = new SQLQueryHandler(
            new ConnectionProvider(this).getConnection()
        );

        initTables();
        regListeners(this);

    }

}
