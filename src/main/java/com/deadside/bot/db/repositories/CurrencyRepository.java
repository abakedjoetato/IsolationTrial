package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.Currency;
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
 * Repository for managing currency with proper data isolation between guilds and servers
 */
public class CurrencyRepository {
    private static final Logger logger = LoggerFactory.getLogger(CurrencyRepository.class);
    private static final String COLLECTION_NAME = "currencies";
    
    private MongoCollection<Currency> collection;
    
    public CurrencyRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, Currency.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Currency> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, Currency.class);
            } catch (Exception e) {
                logger.error("Failed to initialize currency collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save a currency with proper isolation checks
     */
    public void save(Currency currency) {
        try {
            // Ensure currency has valid isolation fields
            if (currency.getGuildId() <= 0 || currency.getServerId() == null || currency.getServerId().isEmpty()) {
                logger.error("Attempted to save currency without proper isolation fields");
                return;
            }
            
            if (currency.getId() == null) {
                getCollection().insertOne(currency);
                logger.debug("Inserted new currency with proper isolation (Guild={}, Server={})",
                    currency.getGuildId(), currency.getServerId());
            } else {
                getCollection().replaceOne(
                    Filters.eq("_id", currency.getId()),
                    currency
                );
                logger.debug("Updated currency with isolation (Guild={}, Server={})",
                    currency.getGuildId(), currency.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving currency", e);
        }
    }
    
    /**
     * Find currency by user ID with proper isolation
     */
    public Currency findByUserIdAndGuildIdAndServerId(long userId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding currency by user ID with isolation: {} (Guild={}, Server={})",
                userId, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Add coins to a user's currency with proper isolation
     */
    public void addCoins(long userId, long amount, long guildId, String serverId) {
        try {
            Currency currency = findByUserIdAndGuildIdAndServerId(userId, guildId, serverId);
            
            if (currency == null) {
                // Create new currency for this user
                currency = new Currency();
                currency.setUserId(userId);
                currency.setGuildId(guildId);
                currency.setServerId(serverId);
                currency.setCoins(amount);
                currency.setLastUpdated(System.currentTimeMillis());
                save(currency);
            } else {
                // Update existing currency
                Bson filter = Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                );
                
                getCollection().updateOne(filter, 
                    Updates.combine(
                        Updates.inc("coins", amount),
                        Updates.set("lastUpdated", System.currentTimeMillis())
                    )
                );
            }
            
            logger.debug("Added {} coins to user {} with isolation (Guild={}, Server={})",
                amount, userId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error adding coins to user with isolation: {} (Guild={}, Server={})",
                userId, guildId, serverId, e);
        }
    }
    
    /**
     * Set coins for a user's currency with proper isolation
     */
    public void setCoins(long userId, long amount, long guildId, String serverId) {
        try {
            Currency currency = findByUserIdAndGuildIdAndServerId(userId, guildId, serverId);
            
            if (currency == null) {
                // Create new currency for this user
                currency = new Currency();
                currency.setUserId(userId);
                currency.setGuildId(guildId);
                currency.setServerId(serverId);
                currency.setCoins(amount);
                currency.setLastUpdated(System.currentTimeMillis());
                save(currency);
            } else {
                // Update existing currency
                Bson filter = Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                );
                
                getCollection().updateOne(filter, 
                    Updates.combine(
                        Updates.set("coins", amount),
                        Updates.set("lastUpdated", System.currentTimeMillis())
                    )
                );
            }
            
            logger.debug("Set coins to {} for user {} with isolation (Guild={}, Server={})",
                amount, userId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error setting coins for user with isolation: {} (Guild={}, Server={})",
                userId, guildId, serverId, e);
        }
    }
    
    /**
     * Get top users by coin balance with proper isolation
     */
    public List<Currency> getTopUsersByCoins(long guildId, String serverId, int limit) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            
            return getCollection().find(filter)
                .sort(Sorts.descending("coins"))
                .limit(limit)
                .into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error getting top users by coins with isolation: (Guild={}, Server={})",
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Update last daily claim timestamp with proper isolation
     */
    public void updateLastDailyClaim(long userId, long timestamp, long guildId, String serverId) {
        try {
            Currency currency = findByUserIdAndGuildIdAndServerId(userId, guildId, serverId);
            
            if (currency == null) {
                // Create new currency for this user
                currency = new Currency();
                currency.setUserId(userId);
                currency.setGuildId(guildId);
                currency.setServerId(serverId);
                currency.setLastDailyClaim(timestamp);
                currency.setLastUpdated(System.currentTimeMillis());
                save(currency);
            } else {
                // Update existing currency
                Bson filter = Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                );
                
                getCollection().updateOne(filter, 
                    Updates.combine(
                        Updates.set("lastDailyClaim", timestamp),
                        Updates.set("lastUpdated", System.currentTimeMillis())
                    )
                );
            }
            
            logger.debug("Updated last daily claim for user {} with isolation (Guild={}, Server={})",
                userId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error updating last daily claim for user with isolation: {} (Guild={}, Server={})",
                userId, guildId, serverId, e);
        }
    }
    
    /**
     * Update last work timestamp with proper isolation
     */
    public void updateLastWork(long userId, long timestamp, long guildId, String serverId) {
        try {
            Currency currency = findByUserIdAndGuildIdAndServerId(userId, guildId, serverId);
            
            if (currency == null) {
                // Create new currency for this user
                currency = new Currency();
                currency.setUserId(userId);
                currency.setGuildId(guildId);
                currency.setServerId(serverId);
                currency.setLastWork(timestamp);
                currency.setLastUpdated(System.currentTimeMillis());
                save(currency);
            } else {
                // Update existing currency
                Bson filter = Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("guildId", guildId),
                    Filters.eq("serverId", serverId)
                );
                
                getCollection().updateOne(filter, 
                    Updates.combine(
                        Updates.set("lastWork", timestamp),
                        Updates.set("lastUpdated", System.currentTimeMillis())
                    )
                );
            }
            
            logger.debug("Updated last work for user {} with isolation (Guild={}, Server={})",
                userId, guildId, serverId);
        } catch (Exception e) {
            logger.error("Error updating last work for user with isolation: {} (Guild={}, Server={})",
                userId, guildId, serverId, e);
        }
    }
    
    /**
     * Delete all currencies by guild and server - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            DeleteResult result = getCollection().deleteMany(filter);
            logger.info("Deleted {} currencies from Guild={}, Server={}", 
                result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting currencies by guild and server", e);
            return 0;
        }
    }
}