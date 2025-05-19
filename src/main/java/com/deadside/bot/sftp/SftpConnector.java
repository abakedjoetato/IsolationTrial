package com.deadside.bot.sftp;

import com.deadside.bot.config.Config;
import com.deadside.bot.db.models.GameServer;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * SFTP connection handler
 */
public class SftpConnector {
    private static final Logger logger = LoggerFactory.getLogger(SftpConnector.class);
    private final int timeout;
    
    public SftpConnector() {
        Config config = Config.getInstance();
        this.timeout = config.getSftpConnectTimeout();
    }
    
    /**
     * Connect to an SFTP server
     * @param server The server config
     * @return The SFTP channel and session
     * @throws JSchException If connection fails
     */
    private SftpConnection connect(GameServer server) throws JSchException {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        
        try {
            session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            session.setPassword(server.getPassword());
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(timeout);
            
            session.connect();
            
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            return new SftpConnection(session, channel);
        } catch (JSchException e) {
            // Make sure to close session if an error occurs
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            throw e;
        }
    }
    
    /**
     * Test connection to an SFTP server
     * @param server The server config
     * @return True if connection is successful
     */
    public boolean testConnection(GameServer server) {
        try (SftpConnection connection = connect(server)) {
            // Just test the connection, don't try to validate directories yet
            // Directory paths will be auto-constructed and we'll create them if needed
            return true;
        } catch (Exception e) {
            logger.error("Failed to connect to SFTP server: {}", server.getName(), e);
            return false;
        }
    }
    
    /**
     * Ensures the base directory exists on the SFTP server
     * @param server The server config
     * @return True if directory exists or was created
     */
    private boolean ensureDirectoryExists(SftpConnection connection, String directory) {
        try {
            try {
                // Check if directory exists
                connection.getChannel().stat(directory);
                return true;
            } catch (Exception e) {
                // Directory doesn't exist, create it
                connection.getChannel().mkdir(directory);
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to create directory: {}", directory, e);
            return false;
        }
    }
    
    /**
     * List files in a directory
     * @param server The server config
     * @param directory Directory to list
     * @return List of file names
     */
    public List<String> listFiles(GameServer server, String directory) throws Exception {
        try (SftpConnection connection = connect(server)) {
            List<String> files = new ArrayList<>();
            
            // Try to list the directory, create it if it doesn't exist
            try {
                Vector<ChannelSftp.LsEntry> entries = connection.getChannel().ls(directory);
                for (ChannelSftp.LsEntry entry : entries) {
                    String filename = entry.getFilename();
                    if (!filename.equals(".") && !filename.equals("..") && !entry.getAttrs().isDir()) {
                        files.add(filename);
                    }
                }
            } catch (Exception e) {
                // Directory might not exist yet, try to create it
                ensureDirectoryExists(connection, directory);
                // Return empty list since directory is new
                return files;
            }
            
            return files;
        }
    }
    
    /**
     * List all CSV files in the deathlogs directory and subdirectories
     * @param server The server config
     * @return List of CSV file paths
     */
    public List<String> findDeathlogFiles(GameServer server) throws Exception {
        try (SftpConnection connection = connect(server)) {
            String baseDir = server.getDeathlogsDirectory();
            List<String> csvFiles = new ArrayList<>();
            
            // Ensure base directory exists
            try {
                ensureDirectoryExists(connection, baseDir);
                
                // Find all csv files in the directory and subdirectories
                findCsvFilesRecursively(connection, baseDir, "", csvFiles);
            } catch (Exception e) {
                logger.warn("Could not search for deathlog files: {}", e.getMessage());
            }
            
            return csvFiles;
        }
    }
    
    /**
     * List recent CSV files in the deathlogs directory by date
     * This method is used internally by findRecentDeathlogFiles(server, count)
     */
    private List<String> findRecentCsvFilesByDate(GameServer server, int daysToInclude) throws Exception {
        try (SftpConnection connection = connect(server)) {
            String baseDir = server.getDeathlogsDirectory();
            List<String> recentCsvFiles = new ArrayList<>();
            
            // Calculate cutoff date (default to 7 days if not specified)
            if (daysToInclude <= 0) {
                daysToInclude = 7;
            }
            
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -daysToInclude);
            Date cutoffDate = calendar.getTime();
            
            // Format to match filename date format
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd");
            String cutoffDateStr = dateFormat.format(cutoffDate);
            
            logger.info("Finding recent deathlogs for server {} since {}", server.getName(), cutoffDateStr);
            
            // Ensure base directory exists
            try {
                ensureDirectoryExists(connection, baseDir);
                
                // Find recent csv files in the directory and subdirectories (implementation below)
                findRecentCsvFilesByDate(connection, baseDir, "", recentCsvFiles, cutoffDateStr);
            } catch (Exception e) {
                logger.warn("Could not search for recent deathlog files: {}", e.getMessage());
            }
            
            logger.info("Found {} recent CSV files for server {}", recentCsvFiles.size(), server.getName());
            return recentCsvFiles;
        }
    }
    
    /**
     * Recursively find CSV files in a directory and its subdirectories
     */
    private void findCsvFilesRecursively(SftpConnection connection, String baseDir, String currentPath, List<String> csvFiles) throws Exception {
        String currentDir = currentPath.isEmpty() ? baseDir : baseDir + "/" + currentPath;
        
        Vector<ChannelSftp.LsEntry> entries = connection.getChannel().ls(currentDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String filename = entry.getFilename();
            
            // Skip parent directory entries
            if (filename.equals(".") || filename.equals("..")) {
                continue;
            }
            
            String relativePath = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
            
            if (entry.getAttrs().isDir()) {
                // Recursively search subdirectory
                findCsvFilesRecursively(connection, baseDir, relativePath, csvFiles);
            } else if (filename.toLowerCase().endsWith(".csv")) {
                // Add CSV file to the list
                csvFiles.add(relativePath);
            }
        }
    }
    
    /**
     * Recursively find recent CSV files in a directory and its subdirectories
     * Filters files based on date in the filename (format: yyyy.MM.dd-HH.mm.ss.csv)
     */
    private void findRecentCsvFilesByDate(SftpConnection connection, String baseDir, String currentPath, 
                                            List<String> csvFiles, String cutoffDateStr) throws Exception {
        String currentDir = currentPath.isEmpty() ? baseDir : baseDir + "/" + currentPath;
        
        Vector<ChannelSftp.LsEntry> entries = connection.getChannel().ls(currentDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String filename = entry.getFilename();
            
            // Skip parent directory entries
            if (filename.equals(".") || filename.equals("..")) {
                continue;
            }
            
            String relativePath = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
            
            if (entry.getAttrs().isDir()) {
                // Recursively search subdirectory
                findRecentCsvFilesByDate(connection, baseDir, relativePath, csvFiles, cutoffDateStr);
            } else if (filename.toLowerCase().endsWith(".csv")) {
                // Check if this is a date-formatted CSV file and if it's recent enough
                if (isRecentCsvFile(filename, cutoffDateStr)) {
                    csvFiles.add(relativePath);
                }
            }
        }
    }
    
    /**
     * Check if a CSV file is recent enough based on its filename date format
     * Expected format: yyyy.MM.dd-HH.mm.ss.csv
     * 
     * @param filename The filename to check
     * @param cutoffDateStr The cutoff date in yyyy.MM.dd format
     * @return true if the file is recent enough to include
     */
    private boolean isRecentCsvFile(String filename, String cutoffDateStr) {
        try {
            // Extract date from filename (expected format: yyyy.MM.dd-HH.mm.ss.csv)
            if (filename.length() < 10) {
                return false; // Not long enough to contain a date
            }
            
            // Try to get the date part (expected at start of filename)
            String datePartOfFilename = filename.substring(0, 10); // yyyy.MM.dd
            
            // If date part is before cutoff, it's too old
            return datePartOfFilename.compareTo(cutoffDateStr) >= 0;
        } catch (Exception e) {
            // If we can't parse the date, include the file to be safe
            return true;
        }
    }
    
    /**
     * Read a file from SFTP
     * @param server The server config
     * @param filePath Path to the file
     * @return The file content as a string
     */
    public String readFile(GameServer server, String filePath) throws Exception {
        try (SftpConnection connection = connect(server)) {
            try (InputStream inputStream = connection.getChannel().get(filePath);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                IOUtils.copy(inputStream, outputStream);
                return outputStream.toString(StandardCharsets.UTF_8);
            }
        }
    }
    
    /**
     * Write content to a file on the server
     * @param server The server config
     * @param filePath Path to the file
     * @param content Content to write
     * @throws Exception If an error occurs
     */
    public void writeFile(GameServer server, String filePath, String content) throws Exception {
        try (SftpConnection connection = connect(server)) {
            // Ensure parent directory exists
            String parentPath = new java.io.File(filePath).getParent().replace("\\", "/");
            try {
                connection.getChannel().mkdir(parentPath);
            } catch (Exception e) {
                // Directory may already exist, or we need to create parent directories
                if (e.getMessage() != null && e.getMessage().contains("No such file")) {
                    // Try to create parent directories recursively
                    String[] pathParts = parentPath.split("/");
                    StringBuilder currentPath = new StringBuilder();
                    for (String part : pathParts) {
                        if (!part.isEmpty()) {
                            currentPath.append("/").append(part);
                            try {
                                connection.getChannel().mkdir(currentPath.toString());
                            } catch (Exception ex) {
                                // Ignore if directory already exists
                            }
                        }
                    }
                }
            }
            
            // Write the file
            try (InputStream inputStream = new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                connection.getChannel().put(inputStream, filePath);
            }
        }
    }
    
    /**
     * Read a file from the logs directory
     * @param server The server config
     * @param filename Name of the log file
     * @return The file content as a string
     */
    public String readLogFile(GameServer server, String filename) throws Exception {
        String filePath = server.getLogDirectory() + "/" + filename;
        return readFile(server, filePath);
    }
    
    /**
     * Read a deathlog CSV file
     * @param server The server config
     * @param filename Name of the CSV file (including subdirectory path)
     * @return The file content as a string
     */
    public String readDeathlogFile(GameServer server, String filename) throws Exception {
        String filePath = server.getDeathlogsDirectory() + "/" + filename;
        return readFile(server, filePath);
    }
    
    /**
     * Read lines from a file after a specific line number
     * @param server The server config
     * @param filePath Path to the file
     * @param afterLine Only read lines after this line number
     * @return The new lines
     */
    public List<String> readLinesAfter(GameServer server, String filePath, long afterLine) throws Exception {
        String content = readFile(server, filePath);
        String[] allLines = content.split("\n");
        
        List<String> newLines = new ArrayList<>();
        for (int i = 0; i < allLines.length; i++) {
            if (i > afterLine) {
                newLines.add(allLines[i]);
            }
        }
        
        return newLines;
    }
    
    /**
     * Read lines from a log file after a specific line number
     * @param server The server config
     * @param filename Name of the log file
     * @param afterLine Only read lines after this line number
     * @return The new lines
     */
    public List<String> readLogLinesAfter(GameServer server, String filename, long afterLine) throws Exception {
        String filePath = server.getLogDirectory() + "/" + filename;
        return readLinesAfter(server, filePath, afterLine);
    }
    
    /**
     * List only the most recent CSV files in the deathlogs directory and subdirectories
     * This can filter by count (most recent N files) or by date range
     * 
     * @param server The server config
     * @param count Number of most recent files to return, or if negative, the number of days to include
     * @return List of CSV file paths
     */
    public List<String> findRecentDeathlogFiles(GameServer server, int count) throws Exception {
        // If count is negative, use date-based approach (days back)
        if (count < 0) {
            return findRecentCsvFilesByDate(server, Math.abs(count));
        }
        
        // Otherwise use count-based approach (most recent N files)
        List<String> allFiles = findDeathlogFiles(server);
        if (allFiles.isEmpty() || count >= allFiles.size()) {
            return allFiles;
        }
        
        // Sort all files (should be date-based names)
        Collections.sort(allFiles);
        
        // Return only the most recent files
        return allFiles.subList(allFiles.size() - count, allFiles.size());
    }
    
    /**
     * Read lines from a deathlog CSV file after a specific line number
     * @param server The server config
     * @param filename Name of the CSV file (including subdirectory path)
     * @param afterLine Only read lines after this line number
     * @return The new lines
     */
    public List<String> readDeathlogLinesAfter(GameServer server, String filename, long afterLine) throws Exception {
        String filePath = server.getDeathlogsDirectory() + "/" + filename;
        return readLinesAfter(server, filePath, afterLine);
    }
    
    /**
     * Holder class for SFTP session and channel
     */
    private static class SftpConnection implements AutoCloseable {
        private final Session session;
        private final ChannelSftp channel;
        
        public SftpConnection(Session session, ChannelSftp channel) {
            this.session = session;
            this.channel = channel;
        }
        
        public Session getSession() {
            return session;
        }
        
        public ChannelSftp getChannel() {
            return channel;
        }
        
        @Override
        public void close() {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
