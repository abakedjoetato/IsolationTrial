package com.deadside.bot.db.repositories;

import com.deadside.bot.db.MongoDBConnection;
import com.deadside.bot.db.models.GuildConfig;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for GuildConfig objects in MongoDB
 */
public class GuildConfigRepository {
    private static final Logger logger = LoggerFactory.getLogger(GuildConfigRepository.class);
    private final MongoCollection<Document> collection;
    
    /**
     * Creates a new GuildConfigRepository
     */
    public GuildConfigRepository() {
        this.collection = MongoDBConnection.getInstance().getDatabase().getCollection("guild_configs");
    }
    
    /**
     * Find a guild configuration by guild ID
     * 
     * @param guildId The Discord guild ID
     * @return The guild configuration, or null if not found
     */
    public GuildConfig findByGuildId(long guildId) {
        Document doc = collection.find(Filters.eq("guildId", guildId)).first();
        if (doc == null) {
            return null;
        }
        
        return new GuildConfig(doc);
    }
    
    /**
     * Save a guild configuration
     * 
     * @param config The guild configuration to save
     * @return The saved guild configuration
     */
    public GuildConfig save(GuildConfig config) {
        try {
            Document doc = config.toDocument();
            
            if (config.getId() == null) {
                collection.insertOne(doc);
                config.setId(doc.getObjectId("_id"));
            } else {
                collection.replaceOne(
                        Filters.eq("_id", config.getId()),
                        doc,
                        new ReplaceOptions().upsert(true)
                );
            }
            
            return config;
        } catch (Exception e) {
            logger.error("Error saving guild configuration", e);
            return config;
        }
    }
    
    /**
     * Delete a guild configuration
     * 
     * @param config The guild configuration to delete
     */
    public void delete(GuildConfig config) {
        if (config.getId() != null) {
            collection.deleteOne(Filters.eq("_id", config.getId()));
        }
    }
    
    /**
     * Delete a guild configuration by ID
     * 
     * @param id The MongoDB ObjectId
     */
    public void delete(ObjectId id) {
        collection.deleteOne(Filters.eq("_id", id));
    }
    
    /**
     * Delete a guild configuration by guild ID
     * 
     * @param guildId The Discord guild ID
     */
    public void deleteByGuildId(long guildId) {
        collection.deleteOne(Filters.eq("guildId", guildId));
    }
    
    /**
     * Find all guild configurations that have premium status
     * 
     * @return List of premium guild configurations
     */
    public List<GuildConfig> findAllPremium() {
        List<GuildConfig> result = new ArrayList<>();
        collection.find(Filters.eq("premium", true)).forEach(doc -> result.add(new GuildConfig(doc)));
        return result;
    }
    
    /**
     * Find all guild configurations
     * 
     * @return List of all guild configurations
     */
    public List<GuildConfig> findAll() {
        List<GuildConfig> result = new ArrayList<>();
        collection.find().forEach(doc -> result.add(new GuildConfig(doc)));
        return result;
    }
}