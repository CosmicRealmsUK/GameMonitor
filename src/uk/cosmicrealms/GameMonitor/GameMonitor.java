package uk.cosmicrealms.GameMonitor;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitScheduler;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GameMonitor extends JavaPlugin implements Listener, PluginMessageListener {
    static String serverMOTD = "";
    static String currentMOTD = "";
    static String bukkitMOTD = "";
    static String[] serverList;
    Connection conn;
    FileConfiguration config = this.getConfig();
    public void log(String message) {
        Logger logger = getLogger();
        logger.log(Level.INFO, message);
    }

    public void onEnable() {
        serverMOTD = Bukkit.getMotd();
        currentMOTD = Bukkit.getMotd();
        bukkitMOTD = Bukkit.getMotd();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        this.saveDefaultConfig();
        connectToDatabase();
        updateGameState();
        log("Attempted to Start all Processes");
        updateServerRoute(getConfig().getString("route"), getServerName());
        getServer().getPluginManager().registerEvents(this, this);
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                String newBukkitMOTD = Bukkit.getMotd();
                if (!(currentMOTD == serverMOTD)) {
                    updateGameState();
                    currentMOTD = serverMOTD;

                } else if (!(newBukkitMOTD == bukkitMOTD)) {
                    currentMOTD = serverMOTD;
                }
            }
        }, 0L, 20L);


    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (containsPlayer(event.getPlayer())) {
            String movement = getPlayerMovement(event.getPlayer());
            if (!(movement == "Idle")) {
                event.getPlayer().sendMessage(ChatColor.DARK_AQUA + "Comms> Connected to SurvivalGames");
                String[] routeSplit = movement.split("#");
                Boolean firstSplit = true;
                String directServer = "Error";
                StringBuilder afterRoute = new StringBuilder();
                Integer lengthCounter = 0;
                Boolean lastConnection = false;
                for (String s : routeSplit) {
                    lengthCounter = lengthCounter + 1;
                    if (firstSplit) {
                        firstSplit = false;
                        directServer = s;
                    } else {
                        if (routeSplit.length == 1) {
                            lastConnection = true;
                        }else if (lengthCounter == routeSplit.length) {
                            afterRoute.append(s);
                        } else {
                            afterRoute.append(s + "#");
                        }
                    }
                    if (!(lastConnection)) {
                        setPlayerMovement(event.getPlayer(), afterRoute.toString());
                        event.getPlayer().sendMessage(ChatColor.RED + "Debug> RoutePart: " + s);
                    }
                }
                if (!(lastConnection)) {
                    final String finalDirectServer = directServer;
                    final Player player = event.getPlayer();
                    event.getPlayer().sendMessage(ChatColor.DARK_AQUA + "Comms> About to try to connect Player to: " + directServer);
                    BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
                    scheduler.scheduleSyncDelayedTask(this, new Runnable() {
                        @Override
                        public void run() {
                            sendConnectPluginMessage(finalDirectServer, player);
                        }
                    }, 15L);
                }else{
                    setPlayerMovement(event.getPlayer(), "Idle");
                }
            }
        }



        event.getPlayer().sendMessage("Attempting to send a Plugin Message!");
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(this, new Runnable() {

            @Override
            public void run() {

            }
        }, 20L);
    }

    public String getPlayerMovement(Player player) {
        try {
            if (conn.isClosed()) {
                connectToDatabase();
            }
            PreparedStatement sql = conn
                    .prepareStatement("SELECT * FROM `player_movement` WHERE Player=?");
            sql.setString(1, player.getName());

            ResultSet resultSet = sql.executeQuery();
            resultSet.next();
            String PlayerMovement = resultSet.getString("Movement");
            return PlayerMovement;
        } catch (Exception e) {
            e.printStackTrace();
            return "NoRoute";
        }
    }

    public String getRemoteServerRoute(String serverName) {
        try {
            if(conn.isClosed()) {
                connectToDatabase();
            }
            PreparedStatement sql = conn
                    .prepareStatement("SELECT * FROM `GameStates` WHERE Server=?");
            sql.setString(1, serverName);

            ResultSet resultSet = sql.executeQuery();
            resultSet.next();
            String serverRoute = resultSet.getString("Route");
            return serverRoute;
        }catch(Exception e) {
            e.printStackTrace();
            return "NoRoute";
        }

    }


    public void onDisable() {
        try {
            if (conn.isClosed()) {
                connectToDatabase();
            }
            if(containsServer(getServerName())) {
                PreparedStatement stateUpdate = conn.prepareStatement("UPDATE `GameStates` SET `State`=? WHERE `Server`=?");
                stateUpdate.setString(1, "Offline");
                stateUpdate.setString(2, getServerName());
                stateUpdate.executeUpdate();
                stateUpdate.close();

            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public String getServerName() {
        return getConfig().getString("serverName");
    }
    public void connectToDatabase() {
        String url = config.getString("database.url");
        String username = config.getString("database.username");
        String password = config.getString("database.password");
        String port = config.getString("database.port");
        String database = config.getString("database.database");
        String DB_NAME = "jdbc:mysql://"+url+":"+port+"/"+database;
        try {
            log("About to connect to Database!");
            //conn = DriverManager.getConnection(DB_NAME, username, password);
            conn = DriverManager.getConnection("jdbc:mysql://panel.cosmicrealms.uk:3306/CR_GameMonitor", "TestUserAccount", "hFVwCJYNFTduK9zC");
            log("Successfully connected");

            log("About to Create a Statement");
        }catch(Exception e){
            log("Failed to Connect to the Database!");
            e.printStackTrace();
        }
    }
    public void closeConnection() {
        try {
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean containsServer(String serverName) {
        boolean containsServer = false;
        try {
            if(conn.isClosed()) {
                connectToDatabase();
            }
            PreparedStatement sql = conn
                    .prepareStatement("SELECT * FROM `GameStates` WHERE Server=?");
            sql.setString(1, serverName);

            ResultSet resultSet = sql.executeQuery();
            containsServer = resultSet.next();
            sql.close();
            resultSet.close();
        }catch(Exception e) {
            e.printStackTrace();
        //}finally {
        //    closeConnection();
        }
        return containsServer;
    }



    public void updateServerRoute(String route, String server) {
        log("Preparing to Update Database!");
        try {
            if (containsServer(getServerName())) {
                log("ServerMOTD: "+serverMOTD);
                PreparedStatement stateUpdate = conn.prepareStatement("UPDATE `GameStates` SET `Route`=? WHERE `Server`=?");
                stateUpdate.setString(1, route);
                stateUpdate.setString(2, server);
                stateUpdate.executeUpdate();
                stateUpdate.close();

            } else {
                PreparedStatement newServer = conn.prepareStatement("INSERT INTO `GameStates` values(?,?,?)");
                newServer.setString(1,server);
                newServer.setString(2,serverMOTD);
                newServer.setString(3, route);
                newServer.execute();
                newServer.close();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void updateGameState() {
        log("Preparing to Update Database!");
        try {
            if (containsServer(getServerName())) {
                log("ServerMOTD: "+serverMOTD);
                PreparedStatement stateUpdate = conn.prepareStatement("UPDATE `GameStates` SET `State`=? WHERE `Server`=?");
                stateUpdate.setString(1, serverMOTD);
                stateUpdate.setString(2, getServerName());
                stateUpdate.executeUpdate();
                stateUpdate.close();

            } else {
                PreparedStatement newServer = conn.prepareStatement("INSERT INTO `GameStates` values(?,?,?)");
                newServer.setString(1,getServerName());
                newServer.setString(2,serverMOTD);
                newServer.setString(3, getConfig().getString("Route"));
                newServer.execute();
                newServer.close();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public String getRemoteServerInfo(String type, String server) {
        if (type.equalsIgnoreCase("serverState")) {
            if ((containsServer(server))) {
                try {
                    if (conn.isClosed()) {
                        connectToDatabase();
                    }
                    PreparedStatement sql = conn
                            .prepareStatement("SELECT * FROM `GameStates` WHERE Server=?");
                    sql.setString(1, server);

                    ResultSet resultSet = sql.executeQuery();
                    resultSet.next();
                    String serverState = resultSet.getString("State");


                    sql.close();
                    resultSet.close();
                    return serverState;
                } catch (Exception e) {
                    e.printStackTrace();
                    return "Error";
                }
            } else {
                return "Server Not Found";
            }
        }
        return "Cannot Get Server Data";
    }

    public boolean containsPlayer (Player player) {
        boolean containsPlayer = false;
        try {
            if(conn.isClosed()) {
                connectToDatabase();
            }
            PreparedStatement sql = conn
                    .prepareStatement("SELECT * FROM `player_movement` WHERE Player=?");
            sql.setString(1, player.getName());

            ResultSet resultSet = sql.executeQuery();
            containsPlayer = resultSet.next();
            sql.close();
            resultSet.close();
        }catch(Exception e) {
            e.printStackTrace();
            //}finally {
            //    closeConnection();
        }
        return containsPlayer;
    }
    public void setPlayerMovement(Player player, String movement) {
        try {
            if (containsPlayer(player)) {
                PreparedStatement stateUpdate = conn.prepareStatement("UPDATE `player_movement` SET `Movement`=? WHERE `Player`=?");
                stateUpdate.setString(1, movement);
                stateUpdate.setString(2, player.getName());
                stateUpdate.executeUpdate();
                stateUpdate.close();

            } else {
                log("Attempting to Insert");
                PreparedStatement newServer = conn.prepareStatement("INSERT INTO `player_movement` values(?,?)");
                newServer.setString(1,player.getName());
                newServer.setString(2,movement);
                newServer.execute();
                newServer.close();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (getConfig().getBoolean("ManualMOTD") && serverMOTD != "")
            event.setMotd(serverMOTD);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("setMOTD")) {
            String newMOTD = "";
            for(String arg : args) {
                newMOTD = newMOTD+" "+arg;
            }
            sender.sendMessage("[GameMonitor] Set the MOTD to: "+ newMOTD);
            serverMOTD = newMOTD;
            return true;
        }else if(cmd.getName().equalsIgnoreCase("saveState")) {
            updateGameState();
            sender.sendMessage("[GameMonitor] Attempting to Update the State on the Database");
            return true;
        }else if(cmd.getName().equalsIgnoreCase("getInfo")) {
            if (args.length == 0) {
                sender.sendMessage("[GameMonitor] serverMOTD: " + serverMOTD);
                sender.sendMessage("[GameMonitor] currentMOTD: " + currentMOTD);
                sender.sendMessage("[GameMonitor] Server: " + getServerName());
                 return true;
            }else {
                if (args[0].equalsIgnoreCase("checkServerList")) {;
                    StringBuilder builder = new StringBuilder();
                    for(String s : serverList) {
                        builder.append(" ," + s);
                    }
                    sender.sendMessage(builder.toString());
                    return true;
                }
                String remoteServerState = getRemoteServerInfo("serverState", args[0]);
                if(containsServer(args[0])) {
                    sender.sendMessage("[GameMonitor] Server: " + args[0]); }else{ sender.sendMessage("[GameMonitor] Server Not Found"); }
                sender.sendMessage("[GameMonitor] State: " + remoteServerState);
            }
        }else if(cmd.getName().equalsIgnoreCase("connectToDatabase")) {
            sender.sendMessage("Attempting to connect to the Database");
            connectToDatabase();
        }else if(cmd.getName().equalsIgnoreCase("UpdateServerList")) {
            sendGetServersPluginMessage((Player) sender);
        }else if(cmd.getName().equalsIgnoreCase("Join")) {
            if (args.length == 0) {
                sender.sendMessage("Please give an Argument!");
            }else if (args.length == 1) {
                sender.sendMessage(ChatColor.BLUE + "[CosmicRealms] "+ChatColor.GOLD+" Going to ");
                sender.sendMessage(ChatColor.GREEN + "Bug Testing on a new Version of this Command is in Progress, please Ignore any bugs");
                sender.sendMessage(ChatColor.RED + "-----");

                sender.sendMessage(ChatColor.RED + "Debug> Attemping to get Server Route...");
                String route = getRemoteServerRoute(args[0]);
                if (route.contains("#")) {
                    sender.sendMessage(ChatColor.RED + "Debug> Warning: This feature is still very experimental...");
                    sender.sendMessage(ChatColor.RED + "Debug> Preparing splitting...");
                    String[] routeSplit = route.split("#");
                    sender.sendMessage(ChatColor.RED + "Debug> The full route:");
                    Boolean firstSplit = true;
                    String directServer = "Error";
                    StringBuilder afterRoute = new StringBuilder();
                    Integer lengthCounter = 0;
                    for (String s : routeSplit) {
                        lengthCounter = lengthCounter + 1;
                        if (firstSplit) {
                            firstSplit = false;
                            directServer = s;
                        }else {
                            if (lengthCounter == routeSplit.length) {
                                afterRoute.append(s);
                            }else{
                                afterRoute.append(s+"#");
                            }
                        }
                    }

                    sender.sendMessage(ChatColor.DARK_AQUA + "Comms> Bridging to Direct Server!");
                    if (directServer.equalsIgnoreCase(getServerName())) {
                        sender.sendMessage(ChatColor.DARK_AQUA + "Comms> First Connection Canceled, Server is Equal to Current Server.");
                        String connectTo;
                        String newRoute = afterRoute.toString();
                        if (newRoute.contains("#")) {
                            String[] newRouteList = newRoute.split("#");
                            connectTo = newRouteList[0];
                            StringBuilder reformedRoute = new StringBuilder();
                            for (String s : newRouteList) {
                                if (!(s.equalsIgnoreCase(newRouteList[0]))) {
                                    reformedRoute.append(s);
                                }

                            }
                            setPlayerMovement((Player) sender, reformedRoute.toString());
                        }else{
                            connectTo = newRoute;
                        }
                        sendConnectPluginMessage(connectTo, (Player) sender);
                    }else {
                        sendConnectPluginMessage(directServer, (Player) sender);
                        setPlayerMovement((Player) sender, afterRoute.toString());
                    }
                }else {
                    sender.sendMessage(ChatColor.RED + "Debug> The found Route was: '"+route+"'");
                    sendConnectPluginMessage(route, (Player) sender);
                }


            }
        }
        return false;
    }

    public String getGameState() {
        String gameStateMode = this.getConfig().getString("Game State Mode");
        return serverMOTD;
    }

    public void sendConnectPluginMessage(String server, Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    public void sendGetServersPluginMessage(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServers");
        player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        if (subchannel.equals("GetServers")) {
            serverList = in.readUTF().split(", ");
        }
    }
}