import com.example.utils.RedisConnectionManager
import redis.clients.jedis.commands.ProtocolCommand

def flowFile = session.get()
if (!flowFile) return

def redisKey = flowFile.getAttribute("sessionId") ?: "defaultKey"

try {
    // Reuses the SAME connection pool as all other scripts!
    RedisConnectionManager.withConnection(context, log) { jedis ->
        def jsonDelCommand = [getRaw: { "JSON.DEL".bytes }] as redis.clients.jedis.commands.ProtocolCommand
        jedis.sendCommand(jsonDelCommand, redisKey)
    }
    
    log.info("Deleted key '${redisKey}' from Redis.")
    session.transfer(flowFile, REL_SUCCESS)

} catch (Exception e) {
    log.error("Error during Redis DELETE operation: " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
