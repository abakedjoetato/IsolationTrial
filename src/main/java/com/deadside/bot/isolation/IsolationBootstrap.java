package com.deadside.bot.isolation;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.db.repositories.*;
import com.deadside.bot.utils.GuildIsolationManager;
import java.util.ArrayList;
import java.util.List;
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
    /**
     * Verify the integrity of data isolation across all repositories
     * This ensures all records have proper guild and server boundaries
     */
    public void verifyIsolationIntegrity() {
        logger.info("Verifying data isolation integrity across all repositories");
        int totalVerified = 0;
        
        try {
            // Verify player repository isolation
            if (playerRepository != null) {
                int playerCount = verifyPlayersIsolation();
                logger.info("Verified isolation for {} player records", playerCount);
                totalVerified += playerCount;
            }
            
            // Verify game server repository isolation
            if (gameServerRepository != null) {
                int serverCount = verifyServersIsolation();
                logger.info("Verified isolation for {} server records", serverCount);
                totalVerified += serverCount;
            }
            
            // Verify alert repository isolation
            if (alertRepository != null) {
                int alertCount = verifyAlertsIsolation();
                logger.info("Verified isolation for {} alert records", alertCount);
                totalVerified += alertCount;
            }
            
            // Verify bounty repository isolation
            if (bountyRepository != null) {
                int bountyCount = verifyBountiesIsolation();
                logger.info("Verified isolation for {} bounty records", bountyCount);
                totalVerified += bountyCount;
            }
            
            // Verify currency repository isolation
            if (currencyRepository != null) {
                int currencyCount = verifyCurrenciesIsolation();
                logger.info("Verified isolation for {} currency records", currencyCount);
                totalVerified += currencyCount;
            }
            
            logger.info("Data isolation verification complete. {} total records verified", totalVerified);
        } catch (Exception e) {
            logger.error("Error during isolation integrity verification", e);
        }
    }
    
    /**
     * Verify isolation for player records
     * @return Number of records verified
     */
    private int verifyPlayersIsolation() {
        List<Player> playersWithoutGuild = new ArrayList<>();
        int count = 0;
        
        try {
            // Find all players that might be missing proper isolation fields
            for (Player player : playerRepository.getAllPlayers()) {
                if (player.getGuildId() <= 0 || player.getServerId() == null || player.getServerId().isEmpty()) {
                    playersWithoutGuild.add(player);
                } else {
                    count++;
                }
            }
            
            logger.info("Found {} player records with proper isolation, {} without proper isolation", 
                count, playersWithoutGuild.size());
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying player isolation", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for server records
     * @return Number of records verified
     */
    private int verifyServersIsolation() {
        int count = 0;
        
        try {
            // Check all servers for proper guild IDs
            List<GameServer> allServers = gameServerRepository.getAllServers();
            for (GameServer server : allServers) {
                if (server.getGuildId() > 0) {
                    count++;
                }
            }
            
            logger.info("Found {} server records with proper isolation, {} without proper isolation", 
                count, allServers.size() - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying server isolation", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for alert records
     * @return Number of records verified
     */
    private int verifyAlertsIsolation() {
        int count = 0;
        
        try {
            // Check all alerts for proper isolation fields
            List<Alert> allAlerts = alertRepository.getAllAlerts();
            for (Alert alert : allAlerts) {
                if (alert.getGuildId() > 0 && alert.getServerId() != null && !alert.getServerId().isEmpty()) {
                    count++;
                }
            }
            
            logger.info("Found {} alert records with proper isolation, {} without proper isolation", 
                count, allAlerts.size() - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying alert isolation", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for bounty records
     * @return Number of records verified
     */
    private int verifyBountiesIsolation() {
        int count = 0;
        
        try {
            // Check all bounties for proper isolation fields
            List<Bounty> allBounties = bountyRepository.getAllBounties();
            for (Bounty bounty : allBounties) {
                if (bounty.getGuildId() > 0 && bounty.getServerId() != null && !bounty.getServerId().isEmpty()) {
                    count++;
                }
            }
            
            logger.info("Found {} bounty records with proper isolation, {} without proper isolation", 
                count, allBounties.size() - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying bounty isolation", e);
            return 0;
        }
    }
    
    /**
     * Verify isolation for currency records
     * @return Number of records verified
     */
    private int verifyCurrenciesIsolation() {
        int count = 0;
        
        try {
            // Check all currencies for proper isolation fields
            List<Currency> allCurrencies = currencyRepository.getAllCurrencies();
            for (Currency currency : allCurrencies) {
                if (currency.getGuildId() > 0 && currency.getServerId() != null && !currency.getServerId().isEmpty()) {
                    count++;
                }
            }
            
            logger.info("Found {} currency records with proper isolation, {} without proper isolation", 
                count, allCurrencies.size() - count);
                
            return count;
        } catch (Exception e) {
            logger.error("Error verifying currency isolation", e);
            return 0;
        }
    }
    
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