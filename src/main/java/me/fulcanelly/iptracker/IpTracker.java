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
        db.syncExecuteUpdate("UPDATE ips SET ip = ?, count = ? WHERE ip = ?", ip, count, ip);
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
            .andThen(optMap -> { 
                
                if (optMap.isEmpty()) {
                    var ipcount = new IpPingCount(ip);
                    ipcount.createIn(sql);
                    return ipcount;
                } else {
                    return IpPingCount.of(optMap.get());
                }
        
            })
            .andThenSilently(ipcount -> {
                ipcount.count++;

                var namesList = sql.parseListOf(
                    sql.syncExecuteQuery("SELECT * FROM names WHERE ip = ?", ip)
                );
              
                var names = namesList.stream()
                    .map(item -> item.get("nick").toString())
                    .map(name -> name.substring(0, name.length() - 1))
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
        
        sql.executeQuery("SELECT * FROM names WHERE ip = ? AND nick = ?", ip, name + "_") //fix 
            .andThen(sql::safeParseOne)
            .andThenSilently(it -> {
                if (it.isEmpty()) {
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
            "   ip STRING,".trim() +
            "   count INTEGER".trim() +
            ")"
        );
        sql.execute(
            "CREATE TABLE IF NOT EXISTS names(" +
            "    ip STRING,".trim() +
            "    nick STRING".trim() +
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
