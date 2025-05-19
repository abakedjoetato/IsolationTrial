package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.KillRecord;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for KillRecord model
 */
public class KillRecordRepository {
    private static final Logger logger = LoggerFactory.getLogger(KillRecordRepository.class);
    private static final String COLLECTION_NAME = "kill_records";
    
    private final MongoCollection<KillRecord> collection;
    
    public KillRecordRepository() {
        this.collection = MongoDBConnection.getInstance().getDatabase().getCollection(COLLECTION_NAME, KillRecord.class);
    }
    
    /**
     * Save a kill record
     */
    public void save(KillRecord killRecord) {
        try {
            collection.insertOne(killRecord);
        } catch (Exception e) {
            logger.error("Error saving kill record", e);
        }
    }
    
    /**
     * Save multiple kill records
     */
    public void saveAll(List<KillRecord> killRecords) {
        try {
            if (!killRecords.isEmpty()) {
                collection.insertMany(killRecords);
            }
        } catch (Exception e) {
            logger.error("Error saving multiple kill records", e);
        }
    }
    
    /**
     * Find recent kill records for a guild
     */
    public List<KillRecord> findRecentByGuildId(long guildId, int limit) {
        try {
            Bson filter = Filters.eq("guildId", guildId);
            FindIterable<KillRecord> results = collection.find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit);
            
            List<KillRecord> records = new ArrayList<>();
            for (KillRecord record : results) {
                records.add(record);
            }
            
            return records;
        } catch (Exception e) {
            logger.error("Error finding recent kill records for guild ID: {}", guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find recent kill records for a server
     */
    public List<KillRecord> findRecentByServerIdAndGuildId(String serverId, long guildId, int limit) {
        try {
            Bson filter = Filters.and(
                    Filters.eq("serverId", serverId),
                    Filters.eq("guildId", guildId)
            );
            FindIterable<KillRecord> results = collection.find(filter)
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit);
            
            List<KillRecord> records = new ArrayList<>();
            for (KillRecord record : results) {
                records.add(record);
            }
            
            return records;
        } catch (Exception e) {
            logger.error("Error finding recent kill records for server ID: {} and guild ID: {}", serverId, guildId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Delete all kill records for a specific server in a guild
     * @param serverId The server ID (name)
     * @param guildId The guild ID
     * @return Number of records deleted
     */
    public int deleteByServerIdAndGuildId(String serverId, long guildId) {
        try {
            Bson filter = Filters.and(
                    Filters.eq("serverId", serverId),
                    Filters.eq("guildId", guildId)
            );
            
            long deletedCount = collection.deleteMany(filter).getDeletedCount();
            logger.info("Deleted {} kill records for server ID: {} in guild ID: {}", deletedCount, serverId, guildId);
            return (int) deletedCount;
        } catch (Exception e) {
            logger.error("Error deleting kill records for server ID: {} and guild ID: {}", serverId, guildId, e);
            return 0;
        }
    }
}
