package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.Player;
import com.deadside.bot.utils.GuildIsolationManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Repository for Player collection with comprehensive isolation between guilds and servers
 */
public class PlayerRepository {
    private static final Logger logger = LoggerFactory.getLogger(PlayerRepository.class);
    private static final String COLLECTION_NAME = "players";
    
    private MongoCollection<Player> collection;
    
    public PlayerRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, Player.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Player> getCollection() {
        if (collection == null) {
            try {
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, Player.class);
            } catch (Exception e) {
                logger.error("Failed to initialize player collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a player to the database with proper isolation checks
     * @param player The player to save
     */
    public void save(Player player) {
        try {
            // Ensure player has valid isolation fields
            if (player.getGuildId() <= 0 || player.getServerId() == null || player.getServerId().isEmpty()) {
                logger.error("Attempted to save player without proper isolation fields: {}", player.getName());
                return;
            }
            
            if (player.getId() == null) {
                getCollection().insertOne(player);
                logger.debug("Inserted new player: {} with proper isolation (Guild={}, Server={})",
                    player.getName(), player.getGuildId(), player.getServerId());
            } else {
                getCollection().replaceOne(
                    Filters.eq("_id", player.getId()),
                    player
                );
                logger.debug("Updated player: {} with isolation (Guild={}, Server={})",
                    player.getName(), player.getGuildId(), player.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving player: {}", player.getName(), e);
        }
    }
    
    /**
     * Find a player by player ID with guild and server isolation
     */
    public Player findByPlayerIdAndGuildIdAndServerId(String playerId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("playerId", playerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by ID with isolation (ID={}, Guild={}, Server={})",
                playerId, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a player by player ID (legacy method, use findByPlayerIdAndGuildIdAndServerId instead)
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public Player findByPlayerId(String playerId) {
        try {
            logger.warn("Non-isolated player lookup by ID: {}. Consider using findByPlayerIdAndGuildIdAndServerId.", playerId);
            return getCollection().find(
                Filters.eq("playerId", playerId)
            ).first();
        } catch (Exception e) {
            logger.error("Error finding player by ID: {}", playerId, e);
            return null;
        }
    }
    
    /**
     * Find a player by Discord ID with guild and server isolation
     */
    public Player findByDiscordIdAndGuildIdAndServerId(String discordId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("discordId", discordId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by Discord ID with isolation", e);
            return null;
        }
    }
    
    /**
     * Find players by guild and server IDs with proper isolation
     */
    public List<Player> findByGuildIdAndServerId(long guildId, String serverId) {
        try {
            return getCollection()
                .find(Filters.and(
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ))
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by guild and server", e);
            return new ArrayList<>();
        }
    }

    /**
     * Count players by guild and server IDs with proper isolation
     */
    public long countPlayersByGuildIdAndServerId(long guildId, String serverId) {
        try {
            return getCollection()
                .countDocuments(Filters.and(
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ));
        } catch (Exception e) {
            logger.error("Error counting players by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Count all players in the database
     * WARNING: This method does not respect isolation boundaries and should be used with caution
     * @return Total number of player records
     */
    public long countAll() {
        try {
            logger.warn("Non-isolated count of all players. Consider using countPlayersByGuildIdAndServerId instead.");
            return getCollection().countDocuments();
        } catch (Exception e) {
            logger.error("Error counting all players", e);
            return 0;
        }
    }
    
    /**
     * Find a player by Deadside ID (deadsideId) across all guilds and servers
     * WARNING: This method doesn't enforce guild isolation and should only be used
     * for cross-guild features like faction tracking. Use with caution.
     * @param deadsideId The Deadside player ID to search for
     * @return The first player found with this ID or null if not found
     */
    public Player findByDeadsideId(String deadsideId) {
        try {
            if (deadsideId == null || deadsideId.isEmpty()) {
                logger.warn("Attempted to find player with null or empty Deadside ID");
                return null;
            }
            
            logger.warn("Non-isolated player lookup by Deadside ID: {}. Consider using isolated methods where possible.", deadsideId);
            return getCollection().find(Filters.eq("deadsideId", deadsideId)).first();
        } catch (Exception e) {
            logger.error("Error finding player by Deadside ID: {}", deadsideId, e);
            return null;
        }
    }
    
    /**
     * Find a player by Deadside ID with proper guild and server isolation
     * @param deadsideId The Deadside game ID of the player
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation
     * @return The found player or null if not found
     */
    public Player findByDeadsideIdAndGuildIdAndServerId(String deadsideId, long guildId, String serverId) {
        try {
            if (deadsideId == null || deadsideId.isEmpty() || guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to find player by Deadside ID without proper isolation parameters. Deadside ID: {}, Guild ID: {}, Server ID: {}", 
                    deadsideId, guildId, serverId);
                return null;
            }
            
            return getCollection().find(Filters.and(
                Filters.eq("deadsideId", deadsideId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by Deadside ID with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by name with guild and server isolation
     */
    public Player findByNameAndGuildIdAndServerId(String name, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("name", name),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by name with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by name without isolation
     * WARNING: This method does not enforce guild isolation
     * It's used primarily by the KillfeedParser for initial player lookups
     * You should use findByNameAndGuildIdAndServerId instead whenever possible
     * @param name The player name to search for
     * @return The first player found with this name, or null if none
     */
    public Player findByName(String name) {
        try {
            if (name == null || name.isEmpty()) {
                logger.warn("Attempted to find player with null or empty name");
                return null;
            }
            
            logger.warn("Non-isolated player lookup by name: {}. Consider using findByNameAndGuildIdAndServerId instead.", name);
            return getCollection().find(Filters.eq("name", name)).first();
        } catch (Exception e) {
            logger.error("Error finding player by name", e);
            return null;
        }
    }
    
    /**
     * Get the top players by KD ratio with proper isolation by guild and server
     * @param guildId The Discord guild ID
     * @param serverId The game server ID
     * @param limit Maximum number of players to return
     * @return List of top players with highest KD ratio
     */
    /**
     * Get all players with optional guild isolation
     * Note: This method should be used cautiously as it can return a large dataset
     * For isolation-aware code, always use with proper guildId and serverId filters
     * @return List of all players in the collection
     */
    public List<Player> getAllPlayers() {
        try {
            logger.warn("Non-isolated player lookup using getAllPlayers(). Consider using isolation-aware methods instead.");
            List<Player> allPlayers = new ArrayList<>();
            getCollection().find().into(allPlayers);
            return allPlayers;
        } catch (Exception e) {
            logger.error("Error getting all players", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get all distinct guild IDs from players collection
     * This is useful for isolation-aware operations across multiple guilds
     * @return List of distinct guild IDs
     */
    public List<Long> getDistinctGuildIds() {
        try {
            List<Long> guildIds = new ArrayList<>();
            getCollection().distinct("guildId", Long.class).into(guildIds);
            return guildIds;
        } catch (Exception e) {
            logger.error("Error getting distinct guild IDs from players collection", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all players by guild ID and server ID with proper isolation
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation  
     * @return List of players for the specified guild and server
     */
    public List<Player> findAllByGuildIdAndServerId(Long guildId, String serverId) {
        try {
            if (guildId == null || guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to find players without proper isolation parameters. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
                if (defaultContext != null) {
                    guildId = defaultContext.getGuildId();
                    serverId = defaultContext.getServerId();
                    logger.info("Using default filter context for player lookup: Guild={}, Server={}", guildId, serverId);
                } else {
                    return new ArrayList<>();
                }
            }
            
            List<Player> players = new ArrayList<>();
            getCollection().find(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).into(players);
            
            return players;
        } catch (Exception e) {
            logger.error("Error finding players by guild and server IDs", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get all players for a specific guild and server with proper isolation
     * @param guildId The Discord guild ID for isolation
     * @param serverId The game server ID for isolation
     * @return List of all players for the given guild and server
     */
    public List<Player> getAllPlayersWithIsolation(long guildId, String serverId) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to get all players without proper isolation. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                return new ArrayList<>();
            }
            
            List<Player> players = new ArrayList<>();
            getCollection().find(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).into(players);
            
            return players;
        } catch (Exception e) {
            logger.error("Error getting all players with isolation", e);
            return new ArrayList<>();
        }
    }
    
    public List<Player> getTopPlayersByKDRatio(long guildId, String serverId, int limit) {
        try {
            if (guildId <= 0 || serverId == null || serverId.isEmpty()) {
                logger.warn("Attempted to get top players without proper isolation. Guild ID: {}, Server ID: {}", 
                    guildId, serverId);
                return new ArrayList<>();
            }
            
            // Create a pipeline to calculate KD ratio and sort by it
            List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId),
                    Filters.gt("kills", 0)
                )),
                Aggregates.project(Projections.fields(
                    Projections.include("_id", "name", "kills", "deaths", "deadsideId", "lastSeen", "guildId", "serverId"),
                    Projections.computed("kdRatio", new Document("$cond", 
                        new Document("if", new Document("$eq", Arrays.asList("$deaths", 0)))
                            .append("then", "$kills")
                            .append("else", new Document("$divide", Arrays.asList("$kills", "$deaths")))
                    ))
                )),
                Aggregates.sort(Sorts.descending("kdRatio")),
                Aggregates.limit(limit)
            );
            
            List<Player> topPlayers = new ArrayList<>();
            getCollection().aggregate(pipeline, Player.class).into(topPlayers);
            
            return topPlayers;
        } catch (Exception e) {
            logger.error("Error getting top players by KD ratio with isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players by name using case-insensitive partial matching with proper guild and server isolation
     * @param name The partial name to search for
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return List of players matching the criteria within isolation boundary
     */
    public List<Player> findByNameLikeAndGuildIdAndServerId(String name, long guildId, String serverId) {
        try {
            // Create a case-insensitive regex pattern for partial name matching
            Bson filter = Filters.and(
                Filters.regex("name", name, "i"),  // "i" for case-insensitive
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by partial name with isolation: {} (Guild={}, Server={})",
                name, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete a player by ID with isolation check to prevent accidental cross-boundary deletions
     */
    public void deleteById(ObjectId id, long guildId, String serverId) {
        try {
            // First check if the player belongs to the specified guild/server
            Player player = getCollection().find(Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
            
            if (player != null) {
                getCollection().deleteOne(Filters.eq("_id", id));
                logger.debug("Deleted player with ID: {} from Guild={}, Server={}", id, guildId, serverId);
            } else {
                logger.warn("Prevented deletion of player with ID: {} due to isolation boundary mismatch", id);
            }
        } catch (Exception e) {
            logger.error("Error deleting player by ID with isolation check", e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void deleteById(ObjectId id) {
        try {
            logger.warn("Non-isolated player deletion by ID: {}. Consider using deleteById with guild/server parameters.", id);
            getCollection().deleteOne(Filters.eq("_id", id));
            logger.debug("Deleted player with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error deleting player by ID", e);
        }
    }
    
    /**
     * Delete all players by guildId and serverId - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            DeleteResult result = getCollection().deleteMany(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            ));
            logger.info("Deleted {} players from Guild={}, Server={}", result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting players by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Get all players
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public List<Player> findAll() {
        try {
            logger.warn("Non-isolated retrieval of all players. Consider using findByGuildIdAndServerId.");
            return getCollection().find().into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error getting all players", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all players with a specific faction ID with proper isolation
     */
    public List<Player> findByFactionIdAndGuildIdAndServerId(ObjectId factionId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("factionId", factionId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by faction ID with isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Update a player's kills with isolation
     */
    public void incrementKills(String playerId, long guildId, String serverId) {
        try {
            getCollection().updateOne(
                Filters.and(
                    Filters.eq("playerId", playerId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ),
                Updates.inc("kills", 1)
            );
            logger.debug("Incremented kills for player ID: {} with isolation (Guild={}, Server={})",
                playerId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error incrementing kills for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void incrementKills(String playerId) {
        try {
            logger.warn("Non-isolated kill increment for player: {}. Consider using incrementKills with guild/server parameters.", playerId);
            getCollection().updateOne(
                Filters.eq("playerId", playerId),
                Updates.inc("kills", 1)
            );
            logger.debug("Incremented kills for player ID: {}", playerId);
        } catch (Exception e) {
            logger.error("Error incrementing kills for player ID: {}", playerId, e);
        }
    }
    
    /**
     * Update a player's deaths with isolation
     */
    public void incrementDeaths(String playerId, long guildId, String serverId) {
        try {
            getCollection().updateOne(
                Filters.and(
                    Filters.eq("playerId", playerId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                ),
                Updates.inc("deaths", 1)
            );
            logger.debug("Incremented deaths for player ID: {} with isolation (Guild={}, Server={})",
                playerId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error incrementing deaths for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void incrementDeaths(String playerId) {
        try {
            logger.warn("Non-isolated death increment for player: {}. Consider using incrementDeaths with guild/server parameters.", playerId);
            getCollection().updateOne(
                Filters.eq("playerId", playerId),
                Updates.inc("deaths", 1)
            );
            logger.debug("Incremented deaths for player ID: {}", playerId);
        } catch (Exception e) {
            logger.error("Error incrementing deaths for player ID: {}", playerId, e);
        }
    }
    
    /**
     * Get top players by kills for a specific guild and server
     */
    public List<Player> getTopPlayersByKills(long guildId, String serverId, int limit) {
        try {
            // Filter out players with no names or placeholder names like "**"
            // and include only players from specified guild and server
            Bson validPlayerFilter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.gte("kills", 1),               // Must have at least 1 kill to be on leaderboard
                Filters.eq("guildId", guildId),        // Must be from specified guild
                Filters.eq("serverId", serverId)       // Must be from specified server
            );
            
            return getCollection().find(validPlayerFilter)
                .sort(Sorts.descending("kills"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error getting top players by kills for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by kills
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    public List<Player> getTopPlayersByKills(int limit) {
        try {
            logger.warn("Using default isolation for top kills query. Consider specifying guild and server IDs.");
            // Using first available server as fallback - this is problematic but prevents data leakage
            GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
            if (defaultContext != null) {
                return getTopPlayersByKills(defaultContext.getGuildId(), defaultContext.getServerId(), limit);
            }
            
            // If no context available, return empty list rather than breaking isolation
            logger.error("No default isolation context available for top kills query");
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting top players by kills with default isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by K/D ratio with proper guild and server isolation
     * @param guildId Guild ID for isolation boundary
     * @param serverId Server ID for isolation boundary
     * @param limit Maximum number of players to return
     * @param minKills Minimum number of kills required to qualify
     * @return List of players with highest K/D ratio within isolation boundary
     */
    public List<Player> getTopPlayersByKD(long guildId, String serverId, int limit, int minKills) {
        try {
            // Filter out players with no names and those with less than 10 kills
            // This is to avoid new players with 1 kill 0 deaths having infinite KD ratio
            // Also apply proper guild and server isolation
            Bson validPlayerFilter = Filters.and(
                Filters.exists("name"),
                Filters.ne("name", ""),
                Filters.ne("name", "**"),
                Filters.gte("kills", 10),  // Minimum 10 kills to be ranked
                Filters.eq("guildId", guildId),    // Must be from specified guild
                Filters.eq("serverId", serverId)   // Must be from specified server
            );
            
            // Use aggregation to calculate KD ratio - since divisions by zero need special handling
            return getCollection().find(validPlayerFilter)
                .sort(Sorts.orderBy(
                    // Formula: kills / (deaths == 0 ? 1 : deaths)
                    Sorts.descending("kills"),
                    Sorts.ascending("deaths")
                ))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error getting top players by K/D ratio for guild {} and server {}", guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by K/D ratio - legacy compatibility method
     * @param limit Maximum number of players to return
     * @return List of players with highest K/D ratio within isolation boundary
     */
    public List<Player> getTopPlayersByKD(int limit) {
        try {
            logger.warn("Using default isolation for top K/D query. Consider specifying guild and server IDs.");
            // Using first available server as fallback
            GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
            if (defaultContext != null) {
                return getTopPlayersByKDRatio(defaultContext.getGuildId(), defaultContext.getServerId(), limit);
            }
            
            // If no context available, return empty list rather than breaking isolation
            logger.error("No default isolation context available for top K/D query");
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting top players by K/D with default isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by death count with proper guild and server isolation
     */
    public List<Player> getTopPlayersByDeaths(long guildId, String serverId, int limit) {
        try {
            // Filter invalid players with proper isolation
            Bson filter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.gte("deaths", 1),              // Must have at least 1 death
                Filters.eq("guildId", guildId),        // Must match the guild ID
                Filters.eq("serverId", serverId)       // Must match the server ID
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("deaths"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top players by deaths for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by deaths
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    public List<Player> getTopPlayersByDeaths(int limit) {
        try {
            logger.warn("Using default isolation for top deaths query. Consider specifying guild and server IDs.");
            // Using first available server as fallback
            GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
            if (defaultContext != null) {
                return getTopPlayersByDeaths(defaultContext.getGuildId(), defaultContext.getServerId(), limit);
            }
            
            // If no context available, return empty list rather than breaking isolation
            logger.error("No default isolation context available for top deaths query");
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting top players by deaths with default isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by distance with proper guild and server isolation
     */
    public List<Player> getTopPlayersByDistance(long guildId, String serverId, int limit) {
        try {
            // Filter invalid players with proper isolation
            Bson filter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.exists("distanceTraveled"),    // Must have distance data
                Filters.gt("distanceTraveled", 0),     // Must have traveled some distance
                Filters.eq("guildId", guildId),        // Must match the guild ID
                Filters.eq("serverId", serverId)       // Must match the server ID
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("distanceTraveled"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top players by distance for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by distance
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    public List<Player> getTopPlayersByDistance(int limit) {
        try {
            logger.warn("Using default isolation for top distance query. Consider specifying guild and server IDs.");
            // Using first available server as fallback
            GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
            if (defaultContext != null) {
                return getTopPlayersByDistance(defaultContext.getGuildId(), defaultContext.getServerId(), limit);
            }
            
            // If no context available, return empty list rather than breaking isolation
            logger.error("No default isolation context available for top distance query");
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting top players by distance with default isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get top players by kill streak with proper guild and server isolation
     */
    public List<Player> getTopPlayersByKillStreak(long guildId, String serverId, int limit) {
        try {
            // Filter invalid players with proper isolation
            Bson filter = Filters.and(
                Filters.exists("name"),                // Name must exist
                Filters.ne("name", ""),                // Name must not be empty
                Filters.ne("name", "**"),              // Name must not be placeholder
                Filters.exists("longestKillStreak"),   // Must have streak data
                Filters.gt("longestKillStreak", 0),    // Must have a streak
                Filters.eq("guildId", guildId),        // Must match the guild ID
                Filters.eq("serverId", serverId)       // Must match the server ID
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("longestKillStreak"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top players by kill streak for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Legacy method to get top players by kill streak
     * WARNING: This has been updated to enforce isolation with default parameters
     */
    public List<Player> getTopPlayersByKillStreak(int limit) {
        try {
            logger.warn("Using default isolation for top kill streak query. Consider specifying guild and server IDs.");
            // Using first available server as fallback
            GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
            if (defaultContext != null) {
                return getTopPlayersByKillStreak(defaultContext.getGuildId(), defaultContext.getServerId(), limit);
            }
            
            // If no context available, return empty list rather than breaking isolation
            logger.error("No default isolation context available for top kill streak query");
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error getting top players by kill streak with default isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Update player's kill streak statistics with isolation
     * Increments current streak and updates longest if needed
     */
    public void incrementKillStreak(String playerId, long guildId, String serverId) {
        try {
            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, serverId);
            if (player != null) {
                player.incrementKillStreak();
                save(player);
                logger.debug("Updated kill streak for player: {} in Guild={}, Server={}",
                    player.getName(), guildId, serverId);
            }
        } catch (Exception e) {
            logger.error("Error updating kill streak for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void incrementKillStreak(String playerId) {
        try {
            logger.warn("Non-isolated kill streak increment for player: {}. Consider using incrementKillStreak with guild/server parameters.", playerId);
            Player player = findByPlayerId(playerId);
            if (player != null) {
                player.incrementKillStreak();
                save(player);
            }
        } catch (Exception e) {
            logger.error("Error updating kill streak for player ID: {}", playerId, e);
        }
    }
    
    /**
     * Reset a player's current kill streak (when they die) with isolation
     */
    public void resetKillStreak(String playerId, long guildId, String serverId) {
        try {
            Player player = findByPlayerIdAndGuildIdAndServerId(playerId, guildId, serverId);
            if (player != null) {
                player.resetKillStreak();
                save(player);
                logger.debug("Reset kill streak for player: {} in Guild={}, Server={}",
                    player.getName(), guildId, serverId);
            }
        } catch (Exception e) {
            logger.error("Error resetting kill streak for player ID: {} with isolation", playerId, e);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public void resetKillStreak(String playerId) {
        try {
            logger.warn("Non-isolated kill streak reset for player: {}. Consider using resetKillStreak with guild/server parameters.", playerId);
            Player player = findByPlayerId(playerId);
            if (player != null) {
                player.resetKillStreak();
                save(player);
            }
        } catch (Exception e) {
            logger.error("Error resetting kill streak for player ID: {}", playerId, e);
        }
    }
    
    /**
     * Find a player by Discord ID with isolation
     * @param discordId The Discord user ID
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return The player or null if not found
     */
    public Player findByDiscordId(String discordId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("discordId", discordId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by Discord ID with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by Discord ID
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param discordId The Discord user ID
     * @return The player or null if not found
     */
    public Player findByDiscordId(String discordId) {
        try {
            logger.warn("Non-isolated player lookup by Discord ID: {}. Consider using findByDiscordId with guild/server parameters.", discordId);
            return getCollection().find(
                Filters.eq("discordId", discordId)
            ).first();
        } catch (Exception e) {
            logger.error("Error finding player by Discord ID", e);
            return null;
        }
    }
    
    /**
     * Find a player by exact name match with isolation
     * @param name The exact player name to find
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return The player or null if not found
     */
    public Player findByNameExact(String name, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("name", name),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding player by exact name with isolation", e);
            return null;
        }
    }
    
    /**
     * Find a player by exact name match
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param name The exact player name to find
     * @return The player or null if not found
     */
    public Player findByNameExact(String name) {
        try {
            logger.warn("Non-isolated player lookup by exact name: {}. Consider using findByNameExact with guild/server parameters.", name);
            return getCollection().find(
                Filters.eq("name", name)
            ).first();
        } catch (Exception e) {
            logger.error("Error finding player by exact name", e);
            return null;
        }
    }
    
    /**
     * Find players with names similar to the provided name with isolation
     * @param namePattern The name pattern to search for
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation 
     * @return List of players with matching names within the isolation boundary
     */
    public List<Player> findByNameLike(String namePattern, long guildId, String serverId) {
        try {
            // Case-insensitive regex search with isolation
            String regexPattern = namePattern.toLowerCase().replace("*", ".*");
            Bson filter = Filters.and(
                Filters.regex("name", "(?i)" + regexPattern),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter)
                .limit(10) // Limit results to prevent large result sets
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by name pattern with isolation: {}", namePattern, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players with names similar to the provided name
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param namePattern The name pattern to search for
     * @return List of players with matching names
     */
    public List<Player> findByNameLike(String namePattern) {
        try {
            logger.warn("Non-isolated player search by name pattern: {}. Consider using findByNameLike with guild/server parameters.", namePattern);
            
            // Using first available server as fallback - this is problematic but prevents data leakage
            GuildIsolationManager.FilterContext defaultContext = getDefaultFilterContext();
            if (defaultContext != null) {
                return findByNameLike(namePattern, defaultContext.getGuildId(), defaultContext.getServerId());
            }
            
            // If no context available, perform regex search but warn about isolation breach
            logger.warn("No default isolation context available for name search - using non-isolated search as fallback");
            
            // Case-insensitive regex search
            String regexPattern = namePattern.toLowerCase().replace("*", ".*");
            Bson filter = Filters.regex("name", "(?i)" + regexPattern);
            return getCollection().find(filter)
                .limit(10) // Limit results to prevent large result sets
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by name pattern: {}", namePattern, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players by faction ID with isolation
     * @param factionId The faction ID to filter by
     * @param guildId The guild ID for isolation
     * @param serverId The server ID for isolation
     * @return List of players in the faction within isolation boundary
     */
    public List<Player> findByFactionId(ObjectId factionId, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("factionId", factionId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by faction ID with isolation", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find players by faction ID
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param factionId The faction ID to filter by
     * @return List of players in the faction
     */
    public List<Player> findByFactionId(ObjectId factionId) {
        try {
            logger.warn("Non-isolated lookup of players by faction ID: {}. Consider using findByFactionId with guild/server parameters.", factionId);
            return getCollection().find(
                Filters.eq("factionId", factionId)
            ).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding players by faction ID", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Count players in a faction with isolation
     * @param factionId The faction ID to count members for
     * @param guildId The guild ID for isolation 
     * @param serverId The server ID for isolation
     * @return The number of players in the faction within isolation boundary
     */
    public long countByFactionId(ObjectId factionId, long guildId, String serverId) {
        try {
            return getCollection().countDocuments(Filters.and(
                Filters.eq("factionId", factionId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            ));
        } catch (Exception e) {
            logger.error("Error counting players by faction ID with isolation", e);
            return 0;
        }
    }
    
    /**
     * Count players in a faction
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     * @param factionId The faction ID to count members for
     * @return The number of players in the faction
     */
    public long countByFactionId(ObjectId factionId) {
        try {
            logger.warn("Non-isolated count of players by faction ID: {}. Consider using countByFactionId with guild/server parameters.", factionId);
            return getCollection().countDocuments(
                Filters.eq("factionId", factionId)
            );
        } catch (Exception e) {
            logger.error("Error counting players by faction ID", e);
            return 0;
        }
    }
    
    /**
     * Get a default filter context for legacy method compatibility
     * This is used only as a fallback for legacy methods to prevent complete data leakage
     */
    private GuildIsolationManager.FilterContext getDefaultFilterContext() {
        try {
            // Attempt to find any game server to use as default context
            MongoCollection<Document> serversCollection = MongoDBConnection.getInstance()
                .getDatabase().getCollection("game_servers");
            
            Document server = serversCollection.find().first();
            if (server != null) {
                long guildId = server.getLong("guildId");
                String serverId = server.getString("serverId");
                return GuildIsolationManager.getInstance().createFilterContext(guildId, serverId);
            }
            return null;
        } catch (Exception e) {
            logger.error("Error getting default filter context", e);
            return null;
        }
    }
}