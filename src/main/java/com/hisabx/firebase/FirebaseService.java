package com.hisabx.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class FirebaseService {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseService.class);
    private static FirebaseService instance;
    private Firestore firestore;
    private boolean initialized = false;
    
    private FirebaseService() {
        // Private constructor for singleton
    }
    
    public static synchronized FirebaseService getInstance() {
        if (instance == null) {
            instance = new FirebaseService();
        }
        return instance;
    }
    
    public void initialize(String credentialsPath) throws IOException {
        if (initialized) {
            logger.info("Firebase already initialized");
            return;
        }
        
        try {
            // Initialize Firebase with service account credentials
            FileInputStream serviceAccount = new FileInputStream(credentialsPath);
            
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl("https://hisabx-inventory.firebaseio.com")
                .build();
            
            FirebaseApp.initializeApp(options);
            
            // Initialize Firestore
            FirestoreOptions firestoreOptions = FirestoreOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
            
            firestore = firestoreOptions.getService();
            
            initialized = true;
            logger.info("Firebase initialized successfully");
            
        } catch (IOException e) {
            logger.error("Failed to initialize Firebase", e);
            throw new IOException("Firebase initialization failed", e);
        }
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public Firestore getFirestore() {
        if (!initialized) {
            throw new IllegalStateException("Firebase not initialized. Call initialize() first.");
        }
        return firestore;
    }
    
    public void testConnection() {
        if (!initialized) {
            throw new IllegalStateException("Firebase not initialized");
        }
        
        try {
            // Test connection by trying to access a collection
            firestore.collection("test").limit(1).get().get();
            logger.info("Firebase connection test successful");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Firebase connection test failed", e);
            throw new RuntimeException("Firebase connection test failed", e);
        }
    }
    
    public void shutdown() {
        if (firestore != null) {
            try {
                firestore.close();
                logger.info("Firebase connection closed");
            } catch (Exception e) {
                logger.warn("Failed to close Firebase connection", e);
            }
        }
    }
}
