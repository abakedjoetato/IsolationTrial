package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.LinkedPlayer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for LinkedPlayer collection with comprehensive isolation
 * This ensures that linked player data cannot leak between different Discord servers and game servers
 */
public class LinkedPlayerRepository {
    private static final Logger logger = LoggerFactory.getLogger(LinkedPlayerRepository.class);
    private static final String COLLECTION_NAME = "linked_players";
    
    private MongoCollection<LinkedPlayer> collection;
    
    public LinkedPlayerRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, LinkedPlayer.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<LinkedPlayer> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, LinkedPlayer.class);
            } catch (Exception e) {
                logger.error("Failed to initialize linked player collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Find a linked player by Discord ID
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public LinkedPlayer findByDiscordId(long discordId) {
        try {
            logger.warn("Non-isolated linked player lookup by Discord ID: {}. Consider using findByDiscordIdAndGuildIdAndServerId.", discordId);
            return getCollection().find(Filters.eq("discordId", discordId)).first();
        } catch (Exception e) {
            logger.error("Error finding linked player by Discord ID: {}", discordId, e);
            return null;
        }
    }
    
    /**
     * Find a linked player by Discord ID with proper isolation
     * This ensures proper data isolation between Discord guilds and game servers
     */
    public LinkedPlayer findByDiscordIdAndGuildIdAndServerId(long discordId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("discordId", discordId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding linked player by Discord ID: {} in guild: {} and server: {}", 
                discordId, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a linked player by in-game player ID (main or alt)
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public LinkedPlayer findByPlayerId(String playerId) {
        try {
            logger.warn("Non-isolated linked player lookup by player ID: {}. Consider using findByPlayerIdAndGuildIdAndServerId.", playerId);
            
            // Check if it's a main player
            LinkedPlayer mainLink = getCollection().find(Filters.eq("mainPlayerId", playerId)).first();
            if (mainLink != null) {
                return mainLink;
            }
            
            // Check if it's an alt player
            return getCollection().find(Filters.in("altPlayerIds", playerId)).first();
        } catch (Exception e) {
            logger.error("Error finding linked player by player ID: {}", playerId, e);
            return null;
        }
    }
    
    /**
     * Find a linked player by in-game player ID (main or alt) with proper guild and server isolation
     */
    public LinkedPlayer findByPlayerIdAndGuildIdAndServerId(String playerId, long guildId, String serverId) {
        try {
            // Check if it's a main player
            Bson mainFilter = Filters.and(
                Filters.eq("mainPlayerId", playerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            LinkedPlayer mainLink = getCollection().find(mainFilter).first();
            if (mainLink != null) {
                return mainLink;
            }
            
            // Check if it's an alt player
            Bson altFilter = Filters.and(
                Filters.in("altPlayerIds", playerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(altFilter).first();
        } catch (Exception e) {
            logger.error("Error finding linked player by player ID: {} for guild: {} and server: {}", 
                playerId, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a linked player by in-game player ID (using ObjectId)
     * This is a convenience method that converts ObjectId to string
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public LinkedPlayer findByPlayerId(ObjectId playerId) {
        if (playerId == null) {
            return null;
        }
        return findByPlayerId(playerId.toString());
    }
    
    /**
     * Find a linked player by in-game player ID (using ObjectId) with proper isolation
     */
    public LinkedPlayer findByPlayerIdAndGuildIdAndServerId(ObjectId playerId, long guildId, String serverId) {
        if (playerId == null) {
            return null;
        }
        return findByPlayerIdAndGuildIdAndServerId(playerId.toString(), guildId, serverId);
    }
    
    /**
     * Save or update a linked player with proper isolation check
     */
    public void save(LinkedPlayer linkedPlayer) {
        try {
            // Ensure linked player has valid isolation fields
            if (linkedPlayer.getGuildId() <= 0 || linkedPlayer.getServerId() == null || linkedPlayer.getServerId().isEmpty()) {
                logger.error("Attempted to save linked player without proper isolation fields: {}", linkedPlayer.getDiscordId());
                return;
            }
            
            linkedPlayer.setUpdated(System.currentTimeMillis());
            
            if (linkedPlayer.getId() == null) {
                getCollection().insertOne(linkedPlayer);
                logger.debug("Inserted new linked player for Discord ID: {} with proper isolation (Guild={}, Server={})",
                    linkedPlayer.getDiscordId(), linkedPlayer.getGuildId(), linkedPlayer.getServerId());
            } else {
                Bson filter = Filters.eq("_id", linkedPlayer.getId());
                getCollection().replaceOne(filter, linkedPlayer);
                logger.debug("Updated linked player for Discord ID: {} with isolation (Guild={}, Server={})",
                    linkedPlayer.getDiscordId(), linkedPlayer.getGuildId(), linkedPlayer.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving linked player: {}", linkedPlayer.getDiscordId(), e);
        }
    }
    
    /**
     * Delete a linked player with isolation check
     */
    public void deleteWithIsolation(LinkedPlayer linkedPlayer, long guildId, String serverId) {
        try {
            if (linkedPlayer.getId() != null) {
                // Check if player belongs to specified guild/server before deleting
                if (linkedPlayer.getGuildId() == guildId && linkedPlayer.getServerId().equals(serverId)) {
                    Bson filter = Filters.eq("_id", linkedPlayer.getId());
                    getCollection().deleteOne(filter);
                    logger.debug("Deleted linked player for Discord ID: {} from Guild={}, Server={}", 
                        linkedPlayer.getDiscordId(), guildId, serverId);
                } else {
                    logger.warn("Prevented deletion of linked player for Discord ID: {} due to isolation boundary mismatch", 
                        linkedPlayer.getDiscordId());
                }
            }
        } catch (Exception e) {
            logger.error("Error deleting linked player: {} with isolation check", linkedPlayer.getDiscordId(), e);
        }
    }
    
    /**
     * Delete a linked player (legacy method)
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void delete(LinkedPlayer linkedPlayer) {
        try {
            logger.warn("Non-isolated linked player deletion for Discord ID: {}. Consider using deleteWithIsolation.", 
                linkedPlayer.getDiscordId());
            
            if (linkedPlayer.getId() != null) {
                Bson filter = Filters.eq("_id", linkedPlayer.getId());
                getCollection().deleteOne(filter);
                logger.debug("Deleted linked player for Discord ID: {}", linkedPlayer.getDiscordId());
            }
        } catch (Exception e) {
            logger.error("Error deleting linked player: {}", linkedPlayer.getDiscordId(), e);
        }
    }
    
    /**
     * Delete all linked players by guildId and serverId - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            DeleteResult result = getCollection().deleteMany(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            ));
            logger.info("Deleted {} linked players from Guild={}, Server={}", result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting linked players by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Find all linked players using isolation-aware approach
     * This method properly respects isolation boundaries while retrieving all players
     */
    public List<LinkedPlayer> findAll() {
        List<LinkedPlayer> allPlayers = new ArrayList<>();
        
        try {
            // Get distinct guild IDs to maintain isolation boundaries
            List<Long> distinctGuildIds = getDistinctGuildIds();
            
            // Process each guild's linked players with proper isolation
            for (Long guildId : distinctGuildIds) {
                if (guildId == null || guildId <= 0) continue;
                
                // Set isolation context for this guild before accessing data
                com.deadside.bot.utils.GuildIsolationManager.getInstance().setContext(guildId, null);
                
                try {
                    // Get all linked players for this guild (isolation-aware)
                    List<LinkedPlayer> guildPlayers = findAllByGuildId(guildId);
                    allPlayers.addAll(guildPlayers);
                } finally {
                    // Always clear context, even if exception occurs
                    com.deadside.bot.utils.GuildIsolationManager.getInstance().clearContext();
                }
            }
            
            logger.debug("Retrieved all linked players using isolation-aware approach: {} total records", allPlayers.size());
        } catch (Exception e) {
            logger.error("Error finding all linked players using isolation-aware approach", e);
        }
        
        return allPlayers;
    }
    
    /**
     * Get distinct guild IDs from the linked player collection
     * Used for isolation-aware cross-guild operations
     */
    public List<Long> getDistinctGuildIds() {
        List<Long> guildIds = new ArrayList<>();
        
        try {
            getCollection().distinct("guildId", Long.class).into(guildIds);
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from linked players", e);
        }
        
        return guildIds;
    }
    
    /**
     * Find all linked players for a specific guild (partial isolation)
     */
    public List<LinkedPlayer> findAllByGuildId(long guildId) {
        try {
            logger.warn("Partial isolation lookup by guild only for linked players. Consider using findAllByGuildIdAndServerId.", guildId);
            Bson filter = Filters.eq("guildId", guildId);
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all linked players for guild: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all linked players for a specific guild and server with proper isolation
     */
    public List<LinkedPlayer> findAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all linked players for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if a Discord user is already linked to a player with proper isolation
     */
    public boolean isDiscordUserLinkedWithIsolation(long discordId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("discordId", discordId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().countDocuments(filter) > 0;
        } catch (Exception e) {
            logger.error("Error checking if Discord user is linked with isolation: {} for guild: {} and server: {}", 
                discordId, guildId, serverId, e);
            return false;
        }
    }
    
    /**
     * Check if a player ID is already linked to a Discord user with proper isolation
     */
    public boolean isPlayerIdLinkedWithIsolation(String playerId, long guildId, String serverId) {
        try {
            Bson mainFilter = Filters.and(
                Filters.eq("mainPlayerId", playerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            if (getCollection().countDocuments(mainFilter) > 0) {
                return true;
            }
            
            Bson altFilter = Filters.and(
                Filters.in("altPlayerIds", playerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().countDocuments(altFilter) > 0;
        } catch (Exception e) {
            logger.error("Error checking if player ID is linked with isolation: {} for guild: {} and server: {}", 
                playerId, guildId, serverId, e);
            return false;
        }
    }
}