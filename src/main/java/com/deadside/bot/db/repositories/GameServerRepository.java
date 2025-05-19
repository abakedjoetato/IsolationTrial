package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.GameServer;
import com.deadside.bot.utils.DataBoundary;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for GameServer collection with comprehensive isolation
 * Implements methods for proper data isolation between different Discord guilds and servers
 */
public class GameServerRepository {
    private static final Logger logger = LoggerFactory.getLogger(GameServerRepository.class);
    private static final String COLLECTION_NAME = "game_servers";
    
    private MongoCollection<GameServer> collection;
    
    public GameServerRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, GameServer.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<GameServer> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, GameServer.class);
            } catch (Exception e) {
                logger.error("Failed to initialize game server collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a game server with proper isolation check
     */
    public void save(GameServer server) {
        try {
            // Ensure server has valid isolation fields
            if (server.getGuildId() <= 0) {
                logger.error("Attempted to save game server without proper guild ID: {}", server.getName());
                return;
            }
            
            // Ensure serverId is set as a unique identifier
            if (server.getServerId() == null || server.getServerId().isEmpty()) {
                logger.error("Attempted to save game server without server ID: {}", server.getName());
                return;
            }
            
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            
            if (server.getId() == null) {
                server.setId(new ObjectId());
            }
            
            getCollection().replaceOne(
                Filters.eq("_id", server.getId()), 
                server, 
                options
            );
            
            logger.debug("Saved game server: {} with proper isolation (Guild={})",
                server.getName(), server.getGuildId());
        } catch (Exception e) {
            logger.error("Error saving game server: {}", server.getName(), e);
        }
    }
    
    /**
     * Find a game server by server ID with isolation check
     * @param serverId The server ID to search for
     * @param guildId The guild ID for isolation boundary
     * @return The game server if found and belongs to the specified guild
     */
    public GameServer findByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("serverId", serverId),
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by ID: {} with guild isolation: {}", serverId, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by ID without isolation
     * WARNING: This method doesn't enforce guild isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public GameServer findById(String serverId) {
        try {
            logger.warn("Non-isolated game server lookup by ID: {}. Consider using findByServerIdAndGuildId.", serverId);
            return getCollection().find(Filters.eq("serverId", serverId)).first();
        } catch (Exception e) {
            logger.error("Error finding game server by ID: {}", serverId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by MongoDB ObjectID with isolation check
     */
    public GameServer findByObjectIdAndGuildId(ObjectId id, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by ObjectID: {} with guild isolation: {}", id, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by MongoDB ObjectID
     * WARNING: This method doesn't enforce guild isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public GameServer findByObjectId(ObjectId id) {
        try {
            logger.warn("Non-isolated game server lookup by ObjectID: {}. Consider using findByObjectIdAndGuildId.", id);
            return getCollection().find(Filters.eq("_id", id)).first();
        } catch (Exception e) {
            logger.error("Error finding game server by ObjectID: {}", id, e);
            return null;
        }
    }
    
    /**
     * Find a game server by name with proper guild isolation
     */
    public GameServer findByNameAndGuildId(String name, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.regex("name", name, "i"), // Case-insensitive name match
                Filters.eq("guildId", guildId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding game server by name: {} with guild isolation: {}", name, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a game server by name
     * WARNING: This method doesn't enforce guild isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public GameServer findByName(String name) {
        try {
            logger.warn("Non-isolated game server lookup by name: {}. Consider using findByNameAndGuildId.", name);
            return getCollection().find(
                Filters.regex("name", name, "i")
            ).first();
        } catch (Exception e) {
            logger.error("Error finding game server by name: {}", name, e);
            return null;
        }
    }
    
    /**
     * Find all game servers for a specific Discord guild
     * This ensures proper data isolation between different Discord servers
     */
    public List<GameServer> findAllByGuildId(long guildId) {
        try {
            Bson filter = Filters.eq("guildId", guildId);
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding game servers for guild: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find the default game server for a Discord guild
     * Returns the first server found for that guild, or null if none exists
     */
    public GameServer findByGuildId(long guildId) {
        try {
            Bson filter = Filters.eq("guildId", guildId);
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding default game server for guild: {}", guildId, e);
            return null;
        }
    }
    
    /**
     * Delete a game server by ID with proper guild isolation
     * Prevents accidental deletion of servers from another guild
     */
    public boolean deleteByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("serverId", serverId),
                Filters.eq("guildId", guildId)
            );
            DeleteResult result = getCollection().deleteOne(filter);
            
            if (result.getDeletedCount() > 0) {
                logger.info("Deleted game server with ID: {} from guild: {}", serverId, guildId);
                return true;
            } else {
                logger.warn("Attempted to delete game server with ID: {} from guild: {}, but server was not found or belongs to a different guild", 
                    serverId, guildId);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error deleting game server by ID: {} with guild isolation: {}", serverId, guildId, e);
            return false;
        }
    }
    
    /**
     * Delete a game server by ID
     * WARNING: This method doesn't enforce guild isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public boolean deleteById(String serverId) {
        try {
            logger.warn("Non-isolated game server deletion by ID: {}. Consider using deleteByServerIdAndGuildId.", serverId);
            return getCollection().deleteOne(
                Filters.eq("serverId", serverId)
            ).getDeletedCount() > 0;
        } catch (Exception e) {
            logger.error("Error deleting game server by ID: {}", serverId, e);
            return false;
        }
    }
    
    /**
     * Get all game servers
     * WARNING: This method doesn't enforce guild isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public List<GameServer> findAll() {
        try {
            logger.warn("Non-isolated retrieval of all game servers. Consider using findAllByGuildId.");
            return getCollection().find().into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all game servers", e);
            return new ArrayList<>();
        }
    }

    /**
     * Delete all game servers for a specific guild
     * Used primarily for data cleanup operations
     */
    public long deleteAllByGuildId(long guildId) {
        try {
            DeleteResult result = getCollection().deleteMany(
                Filters.eq("guildId", guildId)
            );
            logger.info("Deleted {} game servers from guild: {}", result.getDeletedCount(), guildId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting game servers for guild: {}", guildId, e);
            return 0;
        }
    }
    
    /**
     * Delete a game server with proper guild isolation
     * Prevents accidental deletion of servers from another guild
     */
    public boolean deleteWithIsolation(GameServer server, long guildId) {
        try {
            // Check if server belongs to specified guild before deleting
            if (server.getGuildId() == guildId) {
                Bson filter = Filters.eq("_id", server.getId());
                DeleteResult result = getCollection().deleteOne(filter);
                
                if (result.getDeletedCount() > 0) {
                    logger.info("Deleted game server: {} from guild: {}", server.getName(), guildId);
                    return true;
                } else {
                    logger.warn("Failed to delete game server: {} from guild: {}", server.getName(), guildId);
                    return false;
                }
            } else {
                logger.warn("Prevented deletion of game server: {} due to guild isolation mismatch (requested guild: {}, actual guild: {})",
                    server.getName(), guildId, server.getGuildId());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error deleting game server: {} with guild isolation: {}", server.getName(), guildId, e);
            return false;
        }
    }
    
    /**
     * Delete a game server
     * WARNING: This method doesn't enforce guild isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public boolean delete(GameServer server) {
        try {
            logger.warn("Non-isolated game server deletion: {}. Consider using deleteWithIsolation.", server.getName());
            return getCollection().deleteOne(
                Filters.eq("_id", server.getId())
            ).getDeletedCount() > 0;
        } catch (Exception e) {
            logger.error("Error deleting game server: {}", server.getName(), e);
            return false;
        }
    }
    
    /**
     * Check if a server exists with the given server ID and guild ID
     */
    public boolean existsByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("serverId", serverId),
                Filters.eq("guildId", guildId)
            );
            return getCollection().countDocuments(filter) > 0;
        } catch (Exception e) {
            logger.error("Error checking if game server exists by ID: {} with guild isolation: {}", serverId, guildId, e);
            return false;
        }
    }
}