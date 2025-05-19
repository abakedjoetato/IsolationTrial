package com.deadside.bot.schedulers;

import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.db.models.GuildConfig;
import com.deadside.bot.db.repositories.GameServerRepository;
import com.deadside.bot.db.repositories.GuildConfigRepository;
import com.deadside.bot.parsers.KillfeedParser;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Scheduler for processing killfeed data
 */
public class KillfeedScheduler {
    private static final Logger logger = LoggerFactory.getLogger(KillfeedScheduler.class);
    private final GameServerRepository serverRepository;
    private final GuildConfigRepository guildConfigRepository;
    private KillfeedParser killfeedParser;
    
    public KillfeedScheduler() {
        this.serverRepository = new GameServerRepository();
        this.guildConfigRepository = new GuildConfigRepository();
    }
    
    /**
     * Initialize the scheduler with JDA instance
     * @param jda The JDA instance
     */
    public void initialize(JDA jda) {
        this.killfeedParser = new KillfeedParser(jda);
    }
    
    /**
     * Process killfeed data for all servers
     * @param processHistorical If true, will process all historical files; otherwise just new entries
     */
    public void processAllServers(boolean processHistorical) {
        if (killfeedParser == null) {
            logger.error("KillfeedScheduler not initialized with JDA instance");
            return;
        }
        
        try {
            logger.info("Starting scheduled killfeed processing" + (processHistorical ? " (including historical data)" : ""));
            
            List<GameServer> servers = serverRepository.findAll();
            int totalProcessed = 0;
            
            for (GameServer server : servers) {
                // Check if this guild has premium (if not, killfeed is still allowed)
                GuildConfig guildConfig = guildConfigRepository.findByGuildId(server.getGuildId());
                
                // Process killfeed for this server
                int processed = killfeedParser.processServer(server);
                totalProcessed += processed;
                
                // Save server state with updated progress
                if (processed > 0) {
                    serverRepository.save(server);
                }
            }
            
            logger.info("Completed scheduled killfeed processing, total kills processed: {}", totalProcessed);
        } catch (Exception e) {
            logger.error("Error in scheduled killfeed processing", e);
        }
    }
    
    /**
     * Process only new killfeed data (default behavior for scheduled runs)
     */
    public void processAllServers() {
        processAllServers(false);
    }
    
    /**
     * Process all historical killfeed data
     * Should only be called when a new server is added or via admin command
     */
    public void processAllHistoricalData() {
        processAllServers(true);
    }
}
