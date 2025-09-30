// Monitor script - shows pool stats shared by ALL scripts
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

try {
    // Get pool statistics (shared across all scripts)
    def stats = RedisConnectionManager.getPoolStats(log)
    
    log.info("=== Redis Connection Pool Stats ===")
    log.info("Pool Initialized: ${stats.initialized}")
    if (stats.initialized && !stats.error) {
        log.info("Active Connections: ${stats.numActive}")
        log.info("Idle Connections: ${stats.numIdle}")  
        log.info("Waiting Threads: ${stats.numWaiters}")
        log.info("Max Total: ${stats.maxTotal}")
        log.info("Max Idle: ${stats.maxIdle}")
        log.info("Min Idle: ${stats.minIdle}")
    }
    
    session.transfer(flowFile, REL_SUCCESS)
    
} catch (Exception e) {
    log.error("Error getting pool stats: " + e.getMessage(), e)
    session.transfer(flowFile, REL_FAILURE)
}