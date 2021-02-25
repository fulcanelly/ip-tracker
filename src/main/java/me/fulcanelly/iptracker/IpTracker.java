package me.fulcanelly.iptracker;

import org.bukkit.event.server.ServerListPingEvent;

import java.util.stream.Collectors;

import com.google.common.base.Supplier;

import me.fulcanelly.clsql.databse.SQLQueryHandler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

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


class LastWritenAngle {

    Float yaw;
    Float pitch;

    public boolean isSame(Float yaw, Float pitch) {
        return pitch.equals(this.pitch) && yaw.equals(this.yaw);
    }

    public void update(Float yaw, Float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public LastWritenAngle(Float yaw, Float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
}



public class IpTracker extends JavaPlugin implements Listener {

    Map<String, LastWritenAngle> angleCache = new HashMap<>();

    ExecutorService executor = Executors.newSingleThreadExecutor();

    int findOrCreatePlayerIndex(String nick) {
        var result = sql.safeParseOne(
            sql.syncExecuteQuery("SELECT * FROM players WHERE nick = ?", nick)
        );

        return result.map(hash -> (int)hash.get("id")).orElseGet(() -> {
            sql.syncExecuteUpdate("INSERT INTO players(nick) VALUES(?)", nick);
            return findOrCreatePlayerIndex(nick);
        });
    }

    @EventHandler
    void onMove(PlayerMoveEvent event) {

        if (event.isCancelled()) {
            return;
        }

        var loc = event.getPlayer().getLocation();

        var yaw = loc.getYaw();
        var pitch = loc.getPitch();
        var time = System.currentTimeMillis();
        var nick = event.getPlayer().getName();

        executor.execute(() -> {

            var last = angleCache.get(nick);

            if (last != null && last.isSame(yaw, pitch)) {
                return;
            } else {
                angleCache.put(nick, new LastWritenAngle(yaw, pitch));
                sql.syncExecuteUpdate(
                    "INSERT INTO angles VALUES(?, ?, ?, ?)", yaw, pitch, time, findOrCreatePlayerIndex(nick)
                );
            }

            
        });
    }

    void insertConnectionAction(String action, PlayerEvent event) {
        var time = System.currentTimeMillis();

        executor.execute(() -> {
            sql.syncExecuteUpdate(
                "INSERT INTO connections VALUES(?, ?, ?)", action, time, event.getPlayer().getName()
            );
        });
    }

    @EventHandler
    void onLogin(PlayerJoinEvent event) {
        insertConnectionAction("join", event);
    }

    @EventHandler
    void onLeave(PlayerQuitEvent event) {
        insertConnectionAction("quit", event);
        angleCache.remove(event.getPlayer().getName());
    }

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
            "   ip STRING," +
            "   count INTEGER" +
            ")"
        );
        sql.execute(
            "CREATE TABLE IF NOT EXISTS names(" +
            "    ip STRING," +
            "    nick STRING" +
            ")"
        );

        sql.execute(
            "CREATE TABLE IF NOT EXISTS connections(" +
            "    action STRING," +  //join or leave
            "    time INTEGER," +
            "    nick STRING" +
            ")"
        );

        sql.execute(
            "CREATE TABLE IF NOT EXISTS angles(" +
            "    yaw INTEGER," +
            "    pitch INTEGER," +
            "    time INTEGER,"  +
            "    player_id INTEGER" +
            ")"
        );

        sql.execute(
            "CREATE TABLE IF NOT EXISTS players(" +
            "     id INTEGER PRIMARY KEY AUTOINCREMENT ," +
            "     nick STRING" +
            ")"
        );

        sql.execute(
            "CREATE INDEX IF NOT EXISTS idx_contacts_email ON players (nick)"
        );
    }

    @Override
    public void onEnable() {

        this.getDataFolder().mkdir();
        
        sql = new SQLQueryHandler(
            new ConnectionProvider(this).getConnection(), false
        );

        initTables();
        regListeners(this);

    }

    @Override
    public void onDisable() {
        executor.shutdownNow();
    }
}
