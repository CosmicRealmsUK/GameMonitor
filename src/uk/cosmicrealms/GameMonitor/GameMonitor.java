package uk.cosmicrealms.GameMonitor;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameMonitor extends JavaPlugin {
    Connection conn;
    FileConfiguration config = this.getConfig();
    public void log(String message) {
        Logger logger = getLogger();
        logger.log(Level.INFO, "[uk.cosmicrealms.GameMonitor.GameMonitor] "+message);
    }
    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        connectToDatabase();
        updateGameState();
        log("Attempted to Start all Processes");
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

    public void connectToDatabase() {
        String url = config.getString("database.url");
        String username = config.getString("database.username");
        String password = config.getString("database.password");
        String port = config.getString("database.port");
        String database = config.getString("database.database");
        String DB_NAME = "jdbc:mysql://"+url+":"+port+"/"+database;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            log("About to connect to Database!");
            conn = DriverManager.getConnection(DB_NAME, username, password);
            log("Successfully connected");

            log("About to Create a Statement");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void updateGameState() {
        log("Preparing to Update Database!");
        boolean containsServer = false;
        try {
            PreparedStatement sql = conn
                    .prepareStatement("SELECT * FROM 'GameStates' WHERE Server=?");
            sql.setString(1, getServer().getName());
            ResultSet resultSet = sql.executeQuery();
            containsServer = resultSet.next();
        }catch(Exception e) {
            e.printStackTrace();
        }
        String sql;
        if (containsServer) {
            sql = "INSERT INTO 'GameStates' ('Server', 'State') VALUES ('" + getServer().getName() + "', '" + getGameState() + "')";
            log("Trying to input serverState! ServerName = " + getServer().getName() + " & State:" + getGameState());
        }else{
            sql = "UPDATE 'GameStates' SET 'Server'='"+getServer().getName()+"','State'='"+getGameState()+"' WHERE 'Server'="+getServer().getName();
        } // This should hopefully be 100% Complete now, I will continue tomorrow @CosmicRealms (GitHub on the ServerMachine)
        try {
            Statement s = conn.createStatement();
            s.executeUpdate(sql);

        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
