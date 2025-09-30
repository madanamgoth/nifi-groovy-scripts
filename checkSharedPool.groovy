import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

try {
    log.info("=== REDIS CONNECTION POOL VERIFICATION ===")
    
    // Get pool statistics
    def stats = RedisConnectionManager.getPoolStats(log)
    
    if (stats.initialized) {
        if (!stats.error) {
            log.info("✅ Pool Status: ACTIVE and SHARED")
            log.info("📊 Active Connections: ${stats.numActive}")
            log.info("💤 Idle Connections: ${stats.numIdle}")
            log.info("⏳ Waiting Threads: ${stats.numWaiters}")
            log.info("🔢 Max Total: ${stats.maxTotal}")
            log.info("🔄 Max Idle: ${stats.maxIdle}")
            log.info("⚡ Min Idle: ${stats.minIdle}")
            
            // Get pool instance hash to verify it's the same across scripts
            def poolHash = RedisConnectionManager.jedisPool?.hashCode()
            log.info("🔍 Pool Instance Hash: ${poolHash}")
            log.info("💡 This hash should be SAME across all your scripts!")
            
        } else {
            log.warn("⚠️ Pool Status: ERROR - ${stats.error}")
        }
    } else {
        log.warn("❌ Pool Status: NOT INITIALIZED")
        log.info("ℹ️  Pool will be created when first script runs")
    }
    
    // Test a connection to see pool in action
    RedisConnectionManager.withConnection(context, log) { jedis ->
        log.info("✅ Successfully got connection from shared pool")
        return "test-connection"
    }
    
    log.info("⏰ Check Time: ${new Date()}")
    log.info("=" * 50)
    
    session.transfer(flowFile, REL_SUCCESS)
    
} catch (Exception e) {
    log.error("Error checking Redis pool: " + e.getMessage(), e)
    session.transfer(flowFile, REL_FAILURE)
}