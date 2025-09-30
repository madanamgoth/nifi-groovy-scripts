import org.apache.nifi.processor.io.InputStreamCallback
import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import java.io.IOException
import java.io.InputStream
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

try {
    def commandData = null
    
    // Read and parse the flow file content
    session.read(flowFile, new InputStreamCallback() {
        @Override
        void process(InputStream inputStream) throws IOException {
            def jsonText = inputStream.text
            commandData = new JsonSlurper().parseText(jsonText)
            log.info("Parsed command data: ${commandData}")
        }
    })
    
    if (!commandData) {
        throw new Exception("No command data found in flow file")
    }
    
    // Validate required fields
    def command = commandData.command
    def key = commandData.key
    
    if (!command || !key) {
        throw new Exception("Missing required fields: command and key are mandatory")
    }
    
    // Execute Redis command using connection pool
    RedisConnectionManager.withConnection(context, log) { jedis ->
        switch (command.toUpperCase()) {
            case "HSET":
                executeHSet(jedis, commandData)
                break
                
            case "JSON.SET":
                executeJsonSet(jedis, commandData)
                break
                
            default:
                throw new Exception("Unsupported command: ${command}")
        }
    }
    
    // Add execution info to flow file attributes
    flowFile = session.putAttribute(flowFile, "redis.command.executed", command)
    flowFile = session.putAttribute(flowFile, "redis.key.used", key)
    flowFile = session.putAttribute(flowFile, "redis.execution.status", "success")
    
    session.transfer(flowFile, REL_SUCCESS)
    log.info("Successfully executed Redis ${command} for key: ${key}")

} catch (Exception e) {
    log.error("Error executing Redis command: " + e.getMessage(), e)
    flowFile = session.putAttribute(flowFile, "redis.execution.status", "failed")
    flowFile = session.putAttribute(flowFile, "redis.error.message", e.getMessage())
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}

/**
 * Execute HSET command
 */
def executeHSet(jedis, commandData) {
    def key = commandData.key
    def field = commandData.field
    def value = commandData.value
    
    if (!field || !value) {
        throw new Exception("HSET command requires 'field' and 'value' parameters")
    }
    
    log.info("Executing HSET: key=${key}, field=${field}, value=${value}")
    
    // Execute HSET command
    def result = jedis.hset(key, field, value)
    
    log.info("HSET result: ${result} (1=new field, 0=updated existing)")
}

/**
 * Execute JSON.SET command
 */
def executeJsonSet(jedis, commandData) {
    def key = commandData.key
    def path = commandData.path ?: '$'  // Default to root path if not specified
    def value = commandData.value
    
    if (!value) {
        throw new Exception("JSON.SET command requires 'value' parameter")
    }
    
    log.info("Executing JSON.SET: key=${key}, path=${path}")
    
    // Create JSON.SET command
    def jsonSetCommand = [getRaw: { "JSON.SET".bytes }] as redis.clients.jedis.commands.ProtocolCommand
    
    // Execute JSON.SET command
    def result = jedis.sendCommand(jsonSetCommand, key, path, value)
    
    log.info("JSON.SET executed successfully")
}