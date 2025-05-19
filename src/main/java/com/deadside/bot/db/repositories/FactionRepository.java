package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.Faction;
import com.deadside.bot.utils.GuildIsolationManager;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for Faction collection with comprehensive isolation between guilds and servers
 */
public class FactionRepository {
    private static final Logger logger = LoggerFactory.getLogger(FactionRepository.class);
    private static final String COLLECTION_NAME = "factions";
    
    private MongoCollection<Faction> collection;
    
    public FactionRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, Faction.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Faction> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, Faction.class);
            } catch (Exception e) {
                logger.error("Failed to initialize faction collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Find a faction by ID with isolation check
     * @param id The faction ID
     * @param guildId The guild ID for isolation boundary
     * @param serverId The server ID for isolation boundary
     * @return The faction if it exists and belongs to the specified guild/server
     */
    public Faction findByIdWithIsolation(ObjectId id, long guildId, String serverId) {
        try {
            return getCollection().find(Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            )).first();
        } catch (Exception e) {
            logger.error("Error finding faction by ID with isolation: {} (Guild={}, Server={})",
                id, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a faction by ID (legacy method)
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public Faction findById(ObjectId id) {
        try {
            logger.warn("Non-isolated faction lookup by ID: {}. Consider using findByIdWithIsolation.", id);
            return getCollection().find(Filters.eq("_id", id)).first();
        } catch (Exception e) {
            logger.error("Error finding faction by ID: {}", id, e);
            return null;
        }
    }
    
    /**
     * Find a faction by name in a guild (partial isolation)
     * @param guildId The guild ID
     * @param name The faction name
     * @return The faction if found
     */
    public Faction findByNameInGuild(long guildId, String name) {
        try {
            logger.warn("Partial isolation lookup by guild only for faction: {}. Consider using findByNameAndGuildIdAndServerId.", name);
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.regex("name", "^" + name + "$", "i")  // Case-insensitive exact match
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by name: {} in guild: {}", name, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a faction by name (generic version, not guild-specific)
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public Faction findByName(String name) {
        try {
            logger.warn("Non-isolated faction lookup by name: {}. Consider using findByNameAndGuildIdAndServerId.", name);
            Bson filter = Filters.regex("name", "^" + name + "$", "i");  // Case-insensitive exact match
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by name: {}", name, e);
            return null;
        }
    }
    
    /**
     * Find a faction by name with proper guild and server isolation
     */
    public Faction findByNameAndGuildIdAndServerId(String name, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.regex("name", "^" + name + "$", "i"),  // Case-insensitive exact match
                Filters.eq("guildId", guildId),                // Must match guild ID
                Filters.eq("serverId", serverId)               // Must match server ID
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by name: {} for guild: {} and server: {}", 
                name, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a faction by tag in a guild (partial isolation)
     */
    public Faction findByTagInGuild(long guildId, String tag) {
        try {
            logger.warn("Partial isolation lookup by guild only for faction tag: {}. Consider using findByTagInGuildAndServerId.", tag);
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.regex("tag", "^" + tag + "$", "i")  // Case-insensitive exact match
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by tag: {} in guild: {}", tag, guildId, e);
            return null;
        }
    }
    
    /**
     * Find a faction by tag in a guild with proper server isolation
     */
    public Faction findByTagInGuildAndServerId(long guildId, String serverId, String tag) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),                // Must match guild ID
                Filters.eq("serverId", serverId),              // Must match server ID
                Filters.regex("tag", "^" + tag + "$", "i")     // Case-insensitive exact match
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by tag: {} in guild: {} and server: {}", 
                tag, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find a faction by tag (not guild-specific)
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public Faction findByTag(String tag) {
        try {
            logger.warn("Non-isolated faction lookup by tag: {}. Consider using findByTagAndGuildIdAndServerId.", tag);
            Bson filter = Filters.regex("tag", "^" + tag + "$", "i");  // Case-insensitive exact match
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by tag: {}", tag, e);
            return null;
        }
    }
    
    /**
     * Find a faction by tag with proper guild and server isolation
     */
    public Faction findByTagAndGuildIdAndServerId(String tag, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.regex("tag", "^" + tag + "$", "i"),    // Case-insensitive exact match
                Filters.eq("guildId", guildId),                // Must match guild ID
                Filters.eq("serverId", serverId)               // Must match server ID
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding faction by tag: {} for guild: {} and server: {}", 
                tag, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Find factions by owner ID
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public List<Faction> findByOwner(long ownerId) {
        try {
            logger.warn("Non-isolated faction lookup by owner ID: {}. Consider using findByOwnerAndGuildIdAndServerId.", ownerId);
            return getCollection().find(Filters.eq("ownerId", ownerId))
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding factions by owner: {}", ownerId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find factions by owner ID with proper guild and server isolation
     */
    public List<Faction> findByOwnerAndGuildIdAndServerId(long ownerId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("ownerId", ownerId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding factions by owner: {} for guild: {} and server: {}", 
                ownerId, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find factions where a user is a member (owner, officer or regular member)
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public List<Faction> findByMember(long memberId) {
        try {
            logger.warn("Non-isolated faction lookup by member ID: {}. Consider using findByMemberAndGuildIdAndServerId.", memberId);
            Bson filter = Filters.or(
                Filters.eq("ownerId", memberId),
                Filters.in("officerIds", memberId),
                Filters.in("memberIds", memberId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding factions by member: {}", memberId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find factions where a user is a member with proper guild and server isolation
     */
    public List<Faction> findByMemberAndGuildIdAndServerId(long memberId, long guildId, String serverId) {
        try {
            Bson memberFilter = Filters.or(
                Filters.eq("ownerId", memberId),
                Filters.in("officerIds", memberId),
                Filters.in("memberIds", memberId)
            );
            
            Bson filter = Filters.and(
                memberFilter,
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding factions by member: {} for guild: {} and server: {}", 
                memberId, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all factions in a guild (partial isolation)
     */
    public List<Faction> findByGuild(long guildId) {
        try {
            logger.warn("Partial isolation lookup by guild only for factions. Consider using findByGuildIdAndServerId.", guildId);
            return getCollection().find(Filters.eq("guildId", guildId))
                .sort(Sorts.descending("level", "experience"))
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding factions by guild: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all factions with proper guild and server isolation
     */
    public List<Faction> findByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("level", "experience"))
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding factions by guild: {} and server: {}", guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find all factions in a guild (alias method) (partial isolation)
     */
    public List<Faction> findAllByGuildId(long guildId) {
        logger.warn("Partial isolation lookup by guild only for factions. Consider using findByGuildIdAndServerId.", guildId);
        return findByGuild(guildId);
    }
    
    /**
     * Find all factions in a guild and server (alias method with full isolation)
     */
    public List<Faction> findAllByGuildIdAndServerId(long guildId, String serverId) {
        return findByGuildIdAndServerId(guildId, serverId);
    }
    
    /**
     * Find all factions
     * WARNING: This method doesn't enforce guild or server isolation
     * and should only be used in contexts where isolation is already enforced
     */
    public List<Faction> findAll() {
        try {
            logger.warn("Non-isolated retrieval of all factions. Consider using findByGuildIdAndServerId.");
            return getCollection().find().into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all factions", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find top factions by level in a guild (partial isolation)
     */
    public List<Faction> findTopFactionsByLevel(long guildId, int limit) {
        try {
            logger.warn("Partial isolation lookup by guild only for top factions. Consider using findTopFactionsByLevelAndServerId.", guildId);
            return getCollection().find(Filters.eq("guildId", guildId))
                .sort(Sorts.descending("level", "experience"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top factions by level for guild: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find top factions by level with proper guild and server isolation
     */
    public List<Faction> findTopFactionsByLevelAndServerId(long guildId, String serverId, int limit) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("level", "experience"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding top factions by level for guild: {} and server: {}", guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find top factions by member count in a guild (partial isolation)
     */
    public List<Faction> findTopFactionsByMemberCount(long guildId, int limit) {
        try {
            logger.warn("Partial isolation lookup by guild only for top factions by member count. Consider using findTopFactionsByMemberCountAndServerId.", guildId);
            // This is a bit tricky because we need to calculate total members
            // MongoDB aggregation would be more efficient, but for simplicity we'll fetch all and sort in-memory
            List<Faction> factions = getCollection().find(Filters.eq("guildId", guildId))
                .into(new ArrayList<>());
            
            return factions.stream()
                .sorted((f1, f2) -> Integer.compare(f2.getTotalMemberCount(), f1.getTotalMemberCount()))
                .limit(limit)
                .toList();
        } catch (Exception e) {
            logger.error("Error finding top factions by member count for guild: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find top factions by member count with proper guild and server isolation
     */
    public List<Faction> findTopFactionsByMemberCountAndServerId(long guildId, String serverId, int limit) {
        try {
            // This is a bit tricky because we need to calculate total members
            // MongoDB aggregation would be more efficient, but for simplicity we'll fetch all and sort in-memory
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            List<Faction> factions = getCollection().find(filter)
                .into(new ArrayList<>());
            
            return factions.stream()
                .sorted((f1, f2) -> Integer.compare(f2.getTotalMemberCount(), f1.getTotalMemberCount()))
                .limit(limit)
                .toList();
        } catch (Exception e) {
            logger.error("Error finding top factions by member count for guild: {} and server: {}", 
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Save or update a faction with proper isolation checks
     */
    public void save(Faction faction) {
        try {
            // Ensure faction has valid isolation fields
            if (faction.getGuildId() <= 0 || faction.getServerId() == null || faction.getServerId().isEmpty()) {
                logger.error("Attempted to save faction without proper isolation fields: {}", faction.getName());
                return;
            }
            
            if (faction.getId() == null) {
                getCollection().insertOne(faction);
                logger.debug("Inserted new faction: {} with proper isolation (Guild={}, Server={})",
                    faction.getName(), faction.getGuildId(), faction.getServerId());
            } else {
                Bson filter = Filters.eq("_id", faction.getId());
                getCollection().replaceOne(filter, faction);
                logger.debug("Updated faction: {} with isolation (Guild={}, Server={})",
                    faction.getName(), faction.getGuildId(), faction.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving faction: {}", faction.getName(), e);
        }
    }
    
    /**
     * Delete a faction with isolation check
     */
    public boolean deleteWithIsolation(Faction faction, long guildId, String serverId) {
        try {
            if (faction.getId() != null) {
                // Check if faction belongs to specified guild/server before deleting
                if (faction.getGuildId() == guildId && faction.getServerId().equals(serverId)) {
                    Bson filter = Filters.eq("_id", faction.getId());
                    getCollection().deleteOne(filter);
                    logger.debug("Deleted faction: {} from Guild={}, Server={}", 
                        faction.getName(), guildId, serverId);
                    return true;
                } else {
                    logger.warn("Prevented deletion of faction {} due to isolation boundary mismatch", 
                        faction.getName());
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Error deleting faction: {} with isolation check", faction.getName(), e);
            return false;
        }
    }
    
    /**
     * Delete a faction (legacy method)
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public boolean delete(Faction faction) {
        try {
            logger.warn("Non-isolated faction deletion for: {}. Consider using deleteWithIsolation.", 
                faction.getName());
            if (faction.getId() != null) {
                Bson filter = Filters.eq("_id", faction.getId());
                getCollection().deleteOne(filter);
                logger.debug("Deleted faction: {}", faction.getName());
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error deleting faction: {}", faction.getName(), e);
            return false;
        }
    }
    
    /**
     * Delete all factions by guildId and serverId - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            DeleteResult result = getCollection().deleteMany(Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            ));
            logger.info("Deleted {} factions from Guild={}, Server={}", result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting factions by guild and server", e);
            return 0;
        }
    }
    
    /**
     * Add experience to a faction with isolation check
     */
    public boolean addExperienceWithIsolation(ObjectId factionId, int amount, long guildId, String serverId) {
        try {
            // Find faction with isolation check
            Faction faction = findByIdWithIsolation(factionId, guildId, serverId);
            if (faction == null) {
                logger.warn("Prevented adding experience to faction {} due to isolation boundary mismatch or non-existence", 
                    factionId);
                return false;
            }
            
            boolean leveledUp = faction.addExperience(amount);
            save(faction);
            return leveledUp;
        } catch (Exception e) {
            logger.error("Error adding experience to faction: {} with isolation check", factionId, e);
            return false;
        }
    }
    
    /**
     * Add experience to a faction (legacy method)
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public boolean addExperience(ObjectId factionId, int amount) {
        try {
            logger.warn("Non-isolated faction experience update for: {}. Consider using addExperienceWithIsolation.", 
                factionId);
            Faction faction = findById(factionId);
            if (faction == null) {
                return false;
            }
            
            boolean leveledUp = faction.addExperience(amount);
            save(faction);
            return leveledUp;
        } catch (Exception e) {
            logger.error("Error adding experience to faction: {}", factionId, e);
            return false;
        }
    }
    
    /**
     * Update faction bank balance with isolation check
     */
    public boolean updateBalanceWithIsolation(ObjectId factionId, long amount, long guildId, String serverId) {
        try {
            // Find faction with isolation check
            Faction faction = findByIdWithIsolation(factionId, guildId, serverId);
            if (faction == null) {
                logger.warn("Prevented updating balance of faction {} due to isolation boundary mismatch or non-existence", 
                    factionId);
                return false;
            }
            
            if (amount >= 0) {
                faction.deposit(amount);
            } else {
                faction.withdraw(-amount);
            }
            
            save(faction);
            return true;
        } catch (Exception e) {
            logger.error("Error updating balance for faction: {} with isolation check", factionId, e);
            return false;
        }
    }
    
    /**
     * Update faction bank balance (legacy method)
     * WARNING: This method does not respect isolation boundaries and may lead to data leakage
     */
    public boolean updateBalance(ObjectId factionId, long amount) {
        try {
            logger.warn("Non-isolated faction balance update for: {}. Consider using updateBalanceWithIsolation.", 
                factionId);
            Faction faction = findById(factionId);
            if (faction == null) {
                return false;
            }
            
            if (amount >= 0) {
                faction.deposit(amount);
            } else {
                faction.withdraw(-amount);
            }
            
            save(faction);
            return true;
        } catch (Exception e) {
            logger.error("Error updating balance for faction: {}", factionId, e);
            return false;
        }
    }
}