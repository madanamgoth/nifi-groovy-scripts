package com.example.utils

/**
 * RedisConnectionManager - A utility class for managing Redis connections with connection pooling
 * This class provides thread-safe connection management for Redis operations in NiFi Groovy scripts
 */
class RedisConnectionManager {
    static def jedisPool = null
    static def poolInitialized = false
    static final Object poolLock = new Object()
    
    /**
     * Get a Redis connection from the connection pool
     * @param context - NiFi processor context
     * @param log - NiFi logger
     * @return Jedis connection instance
     */
    static def getConnection(context, log) {
        if (!poolInitialized) {
            synchronized(poolLock) {
                if (!poolInitialized) {
                    initializePool(context, log)
                }
            }
        }
        
        try {
            return jedisPool.getResource()
        } catch (Exception e) {
            log.error("Failed to get Redis connection from pool: ${e.getMessage()}", e)
            throw e
        }
    }
    
    /**
     * Initialize the Redis connection pool
     * @param context - NiFi processor context
     * @param log - NiFi logger
     */
    private static def initializePool(context, log) {
        try {
            // Get Redis connection parameters from NiFi properties
            def redisHost = context.getProperty("Redis Host")?.getValue() ?: "localhost"
            def redisPort = context.getProperty("Redis Port")?.asInteger() ?: 6379
            def redisPassword = context.getProperty("Redis Password")?.getValue()
            def redisDatabase = context.getProperty("Redis Database")?.asInteger() ?: 0
            def connectionTimeout = context.getProperty("Connection Timeout")?.asInteger() ?: 2000
            def socketTimeout = context.getProperty("Socket Timeout")?.asInteger() ?: 2000
            
            // Pool configuration parameters
            def maxTotal = context.getProperty("Pool Max Total")?.asInteger() ?: 20
            def maxIdle = context.getProperty("Pool Max Idle")?.asInteger() ?: 10
            def minIdle = context.getProperty("Pool Min Idle")?.asInteger() ?: 2
            def maxWaitMillis = context.getProperty("Pool Max Wait")?.asInteger() ?: 5000
            
            log.info("Initializing Redis connection pool for ${redisHost}:${redisPort}, database: ${redisDatabase}")
            
            // Load Jedis classes dynamically
            def jedisPoolClass = Class.forName("redis.clients.jedis.JedisPool")
            def jedisPoolConfigClass = Class.forName("redis.clients.jedis.JedisPoolConfig")
            
            // Configure connection pool
            def poolConfig = jedisPoolConfigClass.newInstance()
            poolConfig.setMaxTotal(maxTotal)
            poolConfig.setMaxIdle(maxIdle)
            poolConfig.setMinIdle(minIdle)
            poolConfig.setBlockWhenExhausted(true)
            poolConfig.setMaxWaitMillis(maxWaitMillis)
            poolConfig.setTestOnBorrow(true)
            poolConfig.setTestOnReturn(true)
            poolConfig.setTestWhileIdle(true)
            poolConfig.setMinEvictableIdleTimeMillis(60000)
            poolConfig.setTimeBetweenEvictionRunsMillis(30000)
            poolConfig.setNumTestsPerEvictionRun(3)
            
            // Create connection pool using available constructors for Jedis 6.2.0
            // Try different constructor combinations based on what's available
            def constructors = jedisPoolClass.getDeclaredConstructors()
            log.debug("Available JedisPool constructors: ${constructors.length}")
            
            def poolCreated = false
            
            // Try the most common constructors for Jedis 6.2.0
            if (!poolCreated) {
                try {
                    // Try: JedisPool(JedisPoolConfig poolConfig, String host, int port)
                    jedisPool = jedisPoolClass.getDeclaredConstructor(
                        jedisPoolConfigClass, String.class, int.class
                    ).newInstance(poolConfig, redisHost, redisPort)
                    poolCreated = true
                    log.info("Redis connection pool initialized with basic constructor (host:port)")
                } catch (NoSuchMethodException e) {
                    log.debug("Basic constructor not available: ${e.getMessage()}")
                }
            }
            
            // If basic constructor failed, try with URI-based approach
            if (!poolCreated) {
                try {
                    def jedisClientConfigClass = Class.forName("redis.clients.jedis.JedisClientConfig")
                    def defaultJedisClientConfigClass = Class.forName("redis.clients.jedis.DefaultJedisClientConfig")
                    
                    // Create JedisClientConfig
                    def configBuilder = defaultJedisClientConfigClass.newInstance()
                    if (redisPassword && !redisPassword.trim().isEmpty()) {
                        configBuilder = configBuilder.password(redisPassword)
                    }
                    if (redisDatabase > 0) {
                        configBuilder = configBuilder.database(redisDatabase)
                    }
                    configBuilder = configBuilder.connectionTimeoutMillis(connectionTimeout)
                                                 .socketTimeoutMillis(socketTimeout)
                    
                    def clientConfig = configBuilder.build()
                    
                    // Try: JedisPool(JedisPoolConfig poolConfig, String host, int port, JedisClientConfig clientConfig)
                    jedisPool = jedisPoolClass.getDeclaredConstructor(
                        jedisPoolConfigClass, String.class, int.class, jedisClientConfigClass
                    ).newInstance(poolConfig, redisHost, redisPort, clientConfig)
                    poolCreated = true
                    log.info("Redis connection pool initialized with JedisClientConfig")
                } catch (Exception e) {
                    log.debug("JedisClientConfig constructor failed: ${e.getMessage()}")
                }
            }
            
            // Fallback: try the simplest possible constructor
            if (!poolCreated) {
                try {
                    jedisPool = jedisPoolClass.newInstance(redisHost, redisPort)
                    poolCreated = true
                    log.info("Redis connection pool initialized with simplest constructor")
                } catch (Exception e) {
                    log.error("All constructor attempts failed", e)
                    throw new RuntimeException("Unable to create JedisPool with any available constructor", e)
                }
            }
            
            poolInitialized = true
            log.info("Redis connection pool successfully initialized - Max Total: ${maxTotal}, Max Idle: ${maxIdle}, Min Idle: ${minIdle}")
            
        } catch (Exception poolException) {
            log.error("Failed to initialize Redis connection pool: ${poolException.getMessage()}", poolException)
            poolInitialized = false
            jedisPool = null
            throw poolException
        }
    }
    
    /**
     * Close and return a Redis connection to the pool
     * @param jedis - Jedis connection instance to close
     */
    static def closeConnection(def jedis) {
        if (jedis) {
            try {
                jedis.close()
            } catch (Exception e) {
                // Log but don't throw - connection might already be closed
                // The pool will handle cleanup
            }
        }
    }
    
    /**
     * Execute a Redis operation with automatic connection management
     * @param context - NiFi processor context
     * @param log - NiFi logger
     * @param operation - Closure containing the Redis operation
     * @return Result of the operation
     */
    static def withConnection(context, log, Closure operation) {
        def jedis = null
        try {
            jedis = getConnection(context, log)
            return operation.call(jedis)
        } catch (Exception e) {
            log.error("Error executing Redis operation: ${e.getMessage()}", e)
            throw e
        } finally {
            closeConnection(jedis)
        }
    }
    
    /**
     * Get pool statistics for monitoring
     * @param log - NiFi logger
     * @return Map containing pool statistics
     */
    static def getPoolStats(log) {
        if (!poolInitialized || !jedisPool) {
            return [initialized: false]
        }
        
        try {
            return [
                initialized: true,
                numActive: jedisPool.getNumActive(),
                numIdle: jedisPool.getNumIdle(),
                numWaiters: jedisPool.getNumWaiters(),
                maxTotal: jedisPool.getMaxTotal(),
                maxIdle: jedisPool.getMaxIdle(),
                minIdle: jedisPool.getMinIdle()
            ]
        } catch (Exception e) {
            log.warn("Failed to get pool statistics: ${e.getMessage()}")
            return [initialized: true, error: e.getMessage()]
        }
    }
    
    /**
     * Shutdown the connection pool (use with caution)
     * @param log - NiFi logger
     */
    static def shutdown(log) {
        synchronized(poolLock) {
            if (jedisPool && !jedisPool.isClosed()) {
                try {
                    jedisPool.close()
                    log.info("Redis connection pool shutdown completed")
                } catch (Exception e) {
                    log.error("Error during pool shutdown: ${e.getMessage()}", e)
                }
            }
            jedisPool = null
            poolInitialized = false
        }
    }
}