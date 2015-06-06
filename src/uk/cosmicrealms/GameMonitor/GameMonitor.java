package uk.cosmicrealms.GameMonitor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameMonitor extends JavaPlugin implements Listener{
    String serverMOTD = "";
    Connection conn;
    FileConfiguration config = this.getConfig();
    public void log(String message) {
        Logger logger = getLogger();
        logger.log(Level.INFO, message);
    }
    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        connectToDatabase();
        updateGameState();
        log("Attempted to Start all Processes");
        getServer().getPluginManager().registerEvents(this, this);
    }
    public void onDisable() {

    }

    public String getGameState() {
        String gameStateMode = this.getConfig().getString("Game State Mode");
        if (gameStateMode.equalsIgnoreCase("MOTD")) {
            String gameState = getServer().getMotd();
            return gameState;
        }else{
            return "Idle";
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

    public void updateGameState() {
        log("Preparing to Update Database!");
        boolean containsServer = false;
        try {
            PreparedStatement sql = conn
                    .prepareStatement("SELECT * FROM `GameStates` WHERE Server=?");
            sql.setString(1, getServerName());
            ResultSet resultSet = sql.executeQuery();
            containsServer = resultSet.next();
            sql.close();
            resultSet.close();
        }catch(Exception e) {
            e.printStackTrace();
        }

        try {
            if (containsServer) {
                PreparedStatement stateUpdate = conn.prepareStatement("UPDATE `GameStates` SET State=? WHERE `Server`=?");
                stateUpdate.setString(1, getGameState());
                stateUpdate.setString(2, getServerName());
                stateUpdate.executeUpdate();

                stateUpdate.close();

            } else {
                PreparedStatement newServer = conn.prepareStatement("INSERT INTO `GameStates` values(?,?)");
                newServer.setString(1,getServerName());
                newServer.setString(2,getGameState());
                newServer.execute();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void setMOTD(String motd) {
        serverMOTD = motd;
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
                newMOTD = newMOTD+arg;
            }
            sender.sendMessage("[GameMonitor] Set the MOTD to: "+ newMOTD);
            setMOTD(newMOTD);
            return true;
        }else if(cmd.getName().equalsIgnoreCase("saveState")) {
            updateGameState();
            sender.sendMessage("[GameMonitor] Attempting to Update the State on the Database");
            return true;
        }else if(cmd.getName().equalsIgnoreCase("getInfo")) {
            sender.sendMessage("[GameMonitor] MOTD: "+ getServer().getMotd());
            sender.sendMessage("[GameMonitor] Server: "+ getServerName());
        }else if(cmd.getName().equalsIgnoreCase("connectToDatabase")) {
            sender.sendMessage("Attempting to connect to the Database");
            connectToDatabase();
        }
        return false;
    }
}
