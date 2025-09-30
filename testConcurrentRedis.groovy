// Test script - simulates concurrent execution
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

def scriptName = context.getProperty("Script Name")?.getValue() ?: "Unknown"
def operationType = context.getProperty("Operation Type")?.getValue() ?: "GET"

try {
    log.info("=== ${scriptName} STARTED ===")
    
    // This works regardless of execution order!
    RedisConnectionManager.withConnection(context, log) { jedis ->
        
        switch(operationType.toUpperCase()) {
            case "GET":
                def result = jedis.get("test-key")
                log.info("${scriptName}: GET result = ${result}")
                break
                
            case "SET":
                jedis.set("test-key", "value-from-${scriptName}")
                log.info("${scriptName}: SET completed")
                break
                
            case "DELETE":
                jedis.del("test-key")
                log.info("${scriptName}: DELETE completed")
                break
        }
        
        // Show pool stats
        def stats = RedisConnectionManager.getPoolStats(log)
        log.info("${scriptName}: Active connections = ${stats.numActive}, Idle = ${stats.numIdle}")
    }
    
    session.transfer(flowFile, REL_SUCCESS)
    log.info("=== ${scriptName} COMPLETED ===")
    
} catch (Exception e) {
    log.error("${scriptName} failed: " + e.getMessage(), e)
    session.transfer(flowFile, REL_FAILURE)
}