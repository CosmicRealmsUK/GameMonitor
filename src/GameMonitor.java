import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class GameMonitor extends JavaPlugin {
    Connection conn;
    FileConfiguration config = this.getConfig();
    @Override
    public void onEnable() {
        connectToDatabase();
        updateGameState();
    }
    public void onDisable() {

    }

    public String getGameState() {
        String gameStateMode = this.getConfig().getString("Game State Mode");
        if (gameStateMode .equalsIgnoreCase("MOTD")) {
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
            getLogger().info("About to connect to Database!");
            conn = DriverManager.getConnection(DB_NAME, username, password);
            getLogger().info("Successfully connected");

            getLogger().info("About to Create a Statement");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void updateGameState() {
        getLogger().info("Preparing to Update Database!");
        String sql = "INSERT INTO GameStates (Server, State) VALUES ("+getServer().getName()+", "+getGameState()+";";
        getLogger().info("Trying to input serverState! ServerName = "+getServer().getName()+" & State:"+getGameState());
        Statement s = null;
        try {
            s.executeUpdate(sql);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
