package com.hisabx.service.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.GeneralSecurityException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleDriveService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APPLICATION_NAME = "HisabX Inventory Management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.home") + "/.hisabx/drive_tokens";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String BACKUP_FOLDER_NAME = "HisabX Backups";

    private Drive driveService;
    private String backupFolderId;

    public GoogleDriveService() {
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = GoogleDriveService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            // Fallback to trying to find it in the current directory or a config folder if
            // not in resources
            java.io.File specificFile = new java.io.File("credentials.json");
            if (specificFile.exists()) {
                in = new FileInputStream(specificFile);
            } else {
                throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
            }
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public void initialize() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Initialize backup folder
        getOrCreateBackupFolder();
    }

    public boolean isConnected() {
        return driveService != null;
    }

    private String getOrCreateBackupFolder() throws IOException {
        if (backupFolderId != null)
            return backupFolderId;

        // Check if folder exists
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + BACKUP_FOLDER_NAME
                + "' and trashed=false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        for (File file : result.getFiles()) {
            logger.info("Found backup folder: " + file.getName() + " (" + file.getId() + ")");
            backupFolderId = file.getId();
            return backupFolderId;
        }

        // Create folder if not exists
        File fileMetadata = new File();
        fileMetadata.setName(BACKUP_FOLDER_NAME);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File file = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();

        logger.info("Created backup folder: " + BACKUP_FOLDER_NAME + " (" + file.getId() + ")");
        backupFolderId = file.getId();
        return backupFolderId;
    }

    public String uploadFile(java.io.File uploadFile, String description) throws IOException {
        if (driveService == null)
            throw new IOException("Drive service not initialized");

        String folderId = getOrCreateBackupFolder();

        File fileMetadata = new File();
        fileMetadata.setName(uploadFile.getName());
        fileMetadata.setParents(Collections.singletonList(folderId));
        fileMetadata.setDescription(description);

        FileContent mediaContent = new FileContent("application/zip", uploadFile);

        File file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        logger.info("File uploaded: " + file.getId());
        return file.getId();
    }

    public void cleanupOldBackups() {
        if (driveService == null)
            return;

        try {
            String folderId = getOrCreateBackupFolder();

            // List all backup files
            List<File> allBackups = new ArrayList<>();
            String pageToken = null;
            do {
                FileList result = driveService.files().list()
                        .setQ("name contains 'hisabx_backup_' and '" + folderId + "' in parents and trashed=false")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name, createdTime)")
                        .setPageToken(pageToken)
                        .execute();

                allBackups.addAll(result.getFiles());
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            if (allBackups.isEmpty())
                return;

            logger.info("Found " + allBackups.size() + " backups to check for retention.");

            // Parse timestamps and sort
            Pattern pattern = Pattern.compile("hisabx_backup_(\\d{8}_\\d{6})");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

            List<BackupFile> parsedBackups = new ArrayList<>();
            for (File file : allBackups) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.find()) {
                    try {
                        LocalDateTime timestamp = LocalDateTime.parse(matcher.group(1), formatter);
                        parsedBackups.add(new BackupFile(file, timestamp));
                    } catch (Exception e) {
                        logger.warn("Skipping file with unparseable timestamp: " + file.getName());
                    }
                }
            }

            // Sort descending (newest first)
            parsedBackups.sort((b1, b2) -> b2.timestamp.compareTo(b1.timestamp));

            List<File> toDelete = new ArrayList<>();
            List<BackupFile> toKeep = new ArrayList<>();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime seventyTwoHoursAgo = now.minusHours(72);
            LocalDateTime thirtyDaysAgo = now.minusDays(30);

            Map<String, BackupFile> dailyBackups = new HashMap<>(); // Key: yyyyMMdd

            for (BackupFile backup : parsedBackups) {
                if (backup.timestamp.isAfter(seventyTwoHoursAgo)) {
                    // Keep all valid backups from last 72 hours
                    toKeep.add(backup);
                } else if (backup.timestamp.isAfter(thirtyDaysAgo)) {
                    // Keep one per day for last 30 days (fast-forwarding logic: keep the latest for
                    // that day)
                    String dayKey = backup.timestamp.format(DateTimeFormatter.BASIC_ISO_DATE);
                    if (!dailyBackups.containsKey(dayKey)) {
                        dailyBackups.put(dayKey, backup);
                        toKeep.add(backup);
                    } else {
                        toDelete.add(backup.file);
                    }
                } else {
                    // Delete backups older than 30 days
                    toDelete.add(backup.file);
                }
            }

            // Execute deletion
            logger.info(" retention policy: Keeping " + toKeep.size() + ", Deleting " + toDelete.size());

            for (File file : toDelete) {
                try {
                    driveService.files().delete(file.getId()).execute();
                    logger.debug("Deleted old backup: " + file.getName());
                } catch (IOException e) {
                    logger.error("Failed to delete file: " + file.getName(), e);
                }
            }

        } catch (IOException e) {
            logger.error("Failed to execute backup cleanup", e);
        }
    }

    private static class BackupFile {
        File file;
        LocalDateTime timestamp;

        BackupFile(File file, LocalDateTime timestamp) {
            this.file = file;
            this.timestamp = timestamp;
        }
    }
}
