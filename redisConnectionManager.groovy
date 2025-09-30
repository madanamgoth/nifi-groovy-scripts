// Singleton connection pool fields for this processor instance
@groovy.transform.Field static def jedisPool = null
@groovy.transform.Field static def poolInitialized = false

// Helper class to manage the singleton pool
class RedisConnectionManager {
    static def getConnection(context, log) {
        if (!poolInitialized) {
            synchronized(RedisConnectionManager.class) {
                if (!poolInitialized) {
                    try {
                        // Get Redis config from processor properties
                        def redisHost = context.getProperty("Redis Host").getValue() ?: "localhost"
                        def redisPort = context.getProperty("Redis Port").asInteger() ?: 6379

                        def jedisPoolClass = Class.forName("redis.clients.jedis.JedisPool")
                        def jedisPoolConfigClass = Class.forName("redis.clients.jedis.JedisPoolConfig")
                        
                        def poolConfig = jedisPoolConfigClass.newInstance()
                        poolConfig.setMaxTotal(20)
                        poolConfig.setMaxIdle(10)
                        poolConfig.setMinIdle(2)
                        // Add a timeout for high TPS scenarios
                        poolConfig.setBlockWhenExhausted(true)
                        poolConfig.setMaxWaitMillis(5000) // Wait 5 seconds for a connection
                        
                        jedisPool = jedisPoolClass.newInstance(poolConfig, redisHost, redisPort)
                        poolInitialized = true
                        log.info("Redis connection pool initialized for ${redisHost}:${redisPort}")
                    } catch (Exception poolException) {
                        log.error("Failed to initialize Redis pool: " + poolException.getMessage(), poolException)
                        throw poolException
                    }
                }
            }
        }
        return jedisPool.getResource()
    }
    
    static def closeConnection(def jedis) {
        if (jedis) {
            jedis.close() // Returns connection to the pool
        }
    }
}
