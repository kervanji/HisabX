package com.hisabx.database;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:hisabx.db";
    private static SessionFactory sessionFactory;
    
    public static void initialize() {
        try {
            // Initialize SQLite database
            initializeSQLite();
            
            // Configure Hibernate
            configureHibernate();
            
            logger.info("Database initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private static void initializeSQLite() throws SQLException {
        File dbFile = new File("hisabx.db");
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Create tables if they don't exist
            createTables(stmt);
            
            // Apply lightweight migrations for existing databases
            applyMigrations(stmt);
            
            logger.info("SQLite database initialized: {}", dbFile.getAbsolutePath());
        }
    }
    
    private static void applyMigrations(Statement stmt) {
        // Add project_location to sales table if missing
        try {
            stmt.execute("ALTER TABLE sales ADD COLUMN project_location TEXT");
        } catch (SQLException ignored) {
            // Column already exists or table missing; ignore
        }

        // Add paid_amount to sales table if missing
        try {
            stmt.execute("ALTER TABLE sales ADD COLUMN paid_amount REAL DEFAULT 0");
        } catch (SQLException ignored) {
            // Column already exists or table missing; ignore
        }
    }
    
    private static void createTables(Statement stmt) throws SQLException {
        // Customers table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_code TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                phone_number TEXT,
                address TEXT,
                project_location TEXT,
                email TEXT,
                tax_id TEXT,
                credit_limit REAL,
                current_balance REAL DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Products table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_code TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                category TEXT,
                unit_price REAL,
                cost_price REAL,
                quantity_in_stock INTEGER DEFAULT 0,
                minimum_stock INTEGER DEFAULT 0,
                maximum_stock INTEGER,
                unit_of_measure TEXT,
                barcode TEXT,
                is_active BOOLEAN DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Sales table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_code TEXT UNIQUE NOT NULL,
                customer_id INTEGER NOT NULL,
                sale_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                project_location TEXT,
                total_amount REAL NOT NULL,
                discount_amount REAL DEFAULT 0,
                tax_amount REAL DEFAULT 0,
                final_amount REAL NOT NULL,
                paid_amount REAL DEFAULT 0,
                payment_method TEXT,
                payment_status TEXT DEFAULT 'PENDING',
                notes TEXT,
                created_by TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (customer_id) REFERENCES customers(id)
            )
        """);
        
        // Sale items table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sale_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                unit_price REAL NOT NULL,
                total_price REAL NOT NULL,
                discount_percentage REAL DEFAULT 0,
                discount_amount REAL DEFAULT 0,
                FOREIGN KEY (sale_id) REFERENCES sales(id),
                FOREIGN KEY (product_id) REFERENCES products(id)
            )
        """);
        
        // Receipts table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                receipt_number TEXT UNIQUE NOT NULL,
                sale_id INTEGER NOT NULL,
                receipt_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                template TEXT DEFAULT 'DEFAULT',
                file_path TEXT,
                is_printed BOOLEAN DEFAULT 0,
                printed_at DATETIME,
                printed_by TEXT,
                notes TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (sale_id) REFERENCES sales(id)
            )
        """);
        
        // Categories table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                description TEXT,
                parent_id INTEGER,
                is_active BOOLEAN DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (parent_id) REFERENCES categories(id)
            )
        """);
        
        // Create indexes for better performance
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_customers_code ON customers(customer_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_code ON products(product_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products(category)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_code ON sales(sale_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_receipts_number ON receipts(receipt_number)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name)");
        
        logger.info("Database tables created successfully");
    }
    
    private static void configureHibernate() {
        try {
            Configuration configuration = new Configuration();
            
            // Hibernate properties for SQLite
            configuration.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
            configuration.setProperty("hibernate.connection.url", DB_URL);
            configuration.setProperty("hibernate.dialect", "com.hisabx.database.SQLiteDialect");
            configuration.setProperty("hibernate.hbm2ddl.auto", "update");
            configuration.setProperty("hibernate.show_sql", "false");
            configuration.setProperty("hibernate.format_sql", "true");
            configuration.setProperty("hibernate.connection.pool_size", "1");
            configuration.setProperty("hibernate.current_session_context_class", "thread");
            
            // Add entity classes
            configuration.addAnnotatedClass(com.hisabx.model.Customer.class);
            configuration.addAnnotatedClass(com.hisabx.model.Product.class);
            configuration.addAnnotatedClass(com.hisabx.model.Sale.class);
            configuration.addAnnotatedClass(com.hisabx.model.SaleItem.class);
            configuration.addAnnotatedClass(com.hisabx.model.Receipt.class);
            configuration.addAnnotatedClass(com.hisabx.model.Category.class);
            
            sessionFactory = configuration.buildSessionFactory();
            logger.info("Hibernate configured successfully");
            
        } catch (Exception e) {
            logger.error("Failed to configure Hibernate", e);
            throw new RuntimeException("Hibernate configuration failed", e);
        }
    }
    
    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            initialize();
        }
        return sessionFactory;
    }
    
    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
            logger.info("Database connection closed");
        }
    }
}
