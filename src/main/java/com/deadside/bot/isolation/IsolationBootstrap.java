package com.deadside.bot.isolation;

import com.deadside.bot.db.repositories.*;
import com.deadside.bot.utils.GuildIsolationManager;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap class for initializing isolation systems at startup
 * This ensures all isolation components are properly initialized and configured
 */
public class IsolationBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(IsolationBootstrap.class);
    
    // Singleton instance
    private static IsolationBootstrap instance;
    
    // Repositories
    private final PlayerRepository playerRepository;
    private final FactionRepository factionRepository;
    private final LinkedPlayerRepository linkedPlayerRepository;
    private final GameServerRepository gameServerRepository;
    private final CurrencyRepository currencyRepository;
    private final AlertRepository alertRepository;
    private final BountyRepository bountyRepository;
    
    // Cleanup tool
    private DataCleanupTool dataCleanupTool;
    
    /**
     * Get the singleton instance
     */
    public static synchronized IsolationBootstrap getInstance() {
        if (instance == null) {
            instance = new IsolationBootstrap();
        }
        return instance;
    }
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private IsolationBootstrap() {
        // Initialize repositories
        this.playerRepository = new PlayerRepository();
        this.factionRepository = new FactionRepository();
        this.linkedPlayerRepository = new LinkedPlayerRepository();
        this.gameServerRepository = new GameServerRepository();
        this.currencyRepository = new CurrencyRepository();
        this.alertRepository = new AlertRepository();
        this.bountyRepository = new BountyRepository();
        
        // Initialize cleanup tool
        this.dataCleanupTool = new DataCleanupTool(
            playerRepository,
            factionRepository,
            linkedPlayerRepository,
            gameServerRepository,
            currencyRepository,
            alertRepository,
            bountyRepository
        );
    }
    
    /**
     * Initialize all isolation systems
     * This should be called during bot startup
     */
    public void initialize() {
        logger.info("Initializing data isolation systems");
        
        // Initialize isolation manager (nothing to do, it's a singleton)
        GuildIsolationManager.getInstance();
        
        // Load cleanup settings
        DataCleanupTool.loadSettings();
        
        // Run startup cleanup if enabled
        if (DataCleanupTool.isRunCleanupOnStartup()) {
            logger.info("Automatic cleanup on startup is enabled, running cleanup...");
            runStartupCleanup();
        }
        
        logger.info("Data isolation systems initialized successfully");
    }
    
    /**
     * Run startup cleanup process
     */
    private void runStartupCleanup() {
        try {
            // This could be a full cleanup or a lighter version for startup
            dataCleanupTool.cleanupOrphanedRecords();
            logger.info("Startup cleanup completed successfully");
        } catch (Exception e) {
            logger.error("Error during startup cleanup", e);
        }
    }
    
    /**
     * Get the data cleanup tool
     */
    public DataCleanupTool getDataCleanupTool() {
        return dataCleanupTool;
    }
    
    /**
     * Clean orphaned records across all repositories
     * This ensures proper data isolation by removing records that may have been left behind
     * @return Number of records cleaned up
     */
    public int cleanOrphanedRecords() {
        logger.info("Cleaning orphaned records to maintain data isolation");
        int totalCleaned = 0;
        
        try {
            // Use the data cleanup tool to perform the cleaning
            if (dataCleanupTool != null) {
                Map<String, Object> cleanupResults = dataCleanupTool.cleanupOrphanedRecords();
                int cleanedCount = 0;
                if (cleanupResults.containsKey("totalCleaned") && cleanupResults.get("totalCleaned") instanceof Number) {
                    cleanedCount = ((Number) cleanupResults.get("totalCleaned")).intValue();
                }
                totalCleaned = cleanedCount;
                logger.info("Successfully cleaned up {} orphaned records", totalCleaned);
            } else {
                logger.warn("Data cleanup tool is not initialized, cannot clean orphaned records");
            }
        } catch (Exception e) {
            logger.error("Error cleaning orphaned records", e);
        }
        
        return totalCleaned;
    }
    
    /**
     * Get the player repository
     */
    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }
    
    /**
     * Get the faction repository
     */
    public FactionRepository getFactionRepository() {
        return factionRepository;
    }
    
    /**
     * Get the linked player repository
     */
    public LinkedPlayerRepository getLinkedPlayerRepository() {
        return linkedPlayerRepository;
    }
    
    /**
     * Get the game server repository
     */
    public GameServerRepository getGameServerRepository() {
        return gameServerRepository;
    }
    
    /**
     * Get the currency repository
     */
    public CurrencyRepository getCurrencyRepository() {
        return currencyRepository;
    }
    
    /**
     * Get the alert repository
     */
    public AlertRepository getAlertRepository() {
        return alertRepository;
    }
    
    /**
     * Get the bounty repository
     */
    public BountyRepository getBountyRepository() {
        return bountyRepository;
    }
}