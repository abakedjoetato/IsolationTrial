package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.Alert;
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
 * Repository for managing alerts with proper data isolation
 */
public class AlertRepository {
    private static final Logger logger = LoggerFactory.getLogger(AlertRepository.class);
    private static final String COLLECTION_NAME = "alerts";
    
    private MongoCollection<Alert> collection;
    
    public AlertRepository() {
        try {
            this.collection = MongoDBConnection.getInstance().getDatabase()
                .getCollection(COLLECTION_NAME, Alert.class);
        } catch (IllegalStateException e) {
            // This can happen during early initialization - handle gracefully
            logger.warn("MongoDB connection not initialized yet. Usage will be deferred until initialization.");
        }
    }
    
    /**
     * Get the MongoDB collection, initializing if needed
     */
    private MongoCollection<Alert> getCollection() {
        if (collection == null) {
            try {
                // Try to get the collection now that MongoDB should be initialized
                this.collection = MongoDBConnection.getInstance().getDatabase()
                    .getCollection(COLLECTION_NAME, Alert.class);
            } catch (Exception e) {
                logger.error("Failed to initialize alert collection", e);
            }
        }
        return collection;
    }
    
    /**
     * Save or update an alert with proper isolation
     */
    public void save(Alert alert) {
        try {
            // Ensure alert has valid isolation fields
            if (alert.getGuildId() <= 0 || alert.getServerId() == null || alert.getServerId().isEmpty()) {
                logger.error("Attempted to save alert without proper isolation fields");
                return;
            }
            
            if (alert.getId() == null) {
                getCollection().insertOne(alert);
                logger.debug("Inserted new alert with proper isolation (Guild={}, Server={})",
                    alert.getGuildId(), alert.getServerId());
            } else {
                getCollection().replaceOne(
                    Filters.eq("_id", alert.getId()),
                    alert
                );
                logger.debug("Updated alert with isolation (Guild={}, Server={})",
                    alert.getGuildId(), alert.getServerId());
            }
        } catch (Exception e) {
            logger.error("Error saving alert", e);
        }
    }
    
    /**
     * Find alerts by user ID with proper isolation
     */
    public List<Alert> findByUserIdAndGuildIdAndServerId(long userId, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding alerts by user ID with isolation: {} (Guild={}, Server={})",
                userId, guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find an alert by ID with proper isolation
     */
    public Alert findByIdWithIsolation(ObjectId id, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).first();
        } catch (Exception e) {
            logger.error("Error finding alert by ID with isolation: {} (Guild={}, Server={})",
                id, guildId, serverId, e);
            return null;
        }
    }
    
    /**
     * Delete an alert by ID with proper isolation
     */
    public boolean deleteByIdWithIsolation(ObjectId id, long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("_id", id),
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            DeleteResult result = getCollection().deleteOne(filter);
            return result.getDeletedCount() > 0;
        } catch (Exception e) {
            logger.error("Error deleting alert by ID with isolation: {} (Guild={}, Server={})",
                id, guildId, serverId, e);
            return false;
        }
    }
    
    /**
     * Find all alerts for a guild and server
     */
    public List<Alert> findAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            return getCollection().find(filter).into(new ArrayList<>());
        } catch (Exception e) {
            logger.error("Error finding all alerts by guild and server: Guild={}, Server={}",
                guildId, serverId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete all alerts by guild and server - used for data cleanup
     */
    public long deleteAllByGuildIdAndServerId(long guildId, String serverId) {
        try {
            Bson filter = Filters.and(
                Filters.eq("guildId", guildId),
                Filters.eq("serverId", serverId)
            );
            DeleteResult result = getCollection().deleteMany(filter);
            logger.info("Deleted {} alerts from Guild={}, Server={}", 
                result.getDeletedCount(), guildId, serverId);
            return result.getDeletedCount();
        } catch (Exception e) {
            logger.error("Error deleting alerts by guild and server", e);
            return 0;
        }
    }
}