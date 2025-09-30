import org.apache.nifi.processor.io.InputStreamCallback
import org.apache.nifi.processor.io.OutputStreamCallback
import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import com.example.utils.RedisConnectionManager

// --- Main script logic ---

def flowFile = session.get()
if (!flowFile) return

// Determine operation from attribute (e.g., 'SET', 'GET', 'DELETE', 'MERGE', 'GETBYQRY')
def operation = flowFile.getAttribute('redis.operation')?.toUpperCase()
if (!operation) {
    log.error("Missing 'redis.operation' attribute.")
    session.transfer(flowFile, REL_FAILURE)
    return
}

def redisKey = flowFile.getAttribute("document.id") ?: "defaultKey"

try {
    // Define the custom RedisJSON commands
    def jsonSetCommand = [getRaw: { "JSON.SET".bytes }] as redis.clients.jedis.commands.ProtocolCommand
    def jsonGetCommand = [getRaw: { "JSON.GET".bytes }] as redis.clients.jedis.commands.ProtocolCommand
    def jsonDelCommand = [getRaw: { "JSON.DEL".bytes }] as redis.clients.jedis.commands.ProtocolCommand
    def jsonMergeCommand = [getRaw: { "JSON.MERGE".bytes }] as redis.clients.jedis.commands.ProtocolCommand

    switch (operation) {
        case 'SET':
            RedisConnectionManager.withConnection(context, log) { jedis ->
                session.read(flowFile, new InputStreamCallback() {
                    @Override
                    void process(InputStream inputStream) throws IOException {
                        byte[] jsonBytes = inputStream.readAllBytes()
                        jedis.sendCommand(jsonSetCommand, redisKey, "\$", new String(jsonBytes, StandardCharsets.UTF_8))
                    }
                })
            }
            session.transfer(flowFile, REL_SUCCESS)
            log.info("Successfully set Redis JSON for key: ${redisKey}")
            break

        case 'GET':
            def jsonResult = RedisConnectionManager.withConnection(context, log) { jedis ->
                return jedis.sendCommand(jsonGetCommand, redisKey, "\$")
            }
            
            if (jsonResult) {
                flowFile = session.write(flowFile, new OutputStreamCallback() {
                    @Override
                    void process(OutputStream outputStream) throws IOException {
                        if (jsonResult instanceof byte[]) {
                            outputStream.write(jsonResult)
                        } else {
                            outputStream.write(jsonResult.toString().getBytes(StandardCharsets.UTF_8))
                        }
                    }
                })
                session.transfer(flowFile, REL_SUCCESS)
                log.info("Successfully retrieved Redis JSON for key: ${redisKey}")
            } else {
                log.warn("No data found in Redis for key: ${redisKey}")
                session.transfer(flowFile, REL_FAILURE)
            }
            break

        case 'GETBYQRY':
            def jsonPath = flowFile.getAttribute("redis.json.path")
            if (!jsonPath) {
                log.error("Missing 'redis.json.path' attribute for GETBYQRY operation.")
                session.transfer(flowFile, REL_FAILURE)
                break
            }
            
            def jsonResult = RedisConnectionManager.withConnection(context, log) { jedis ->
                return jedis.sendCommand(jsonGetCommand, redisKey, jsonPath)
            }
            
            if (jsonResult) {
                flowFile = session.write(flowFile, new OutputStreamCallback() {
                    @Override
                    void process(OutputStream outputStream) throws IOException {
                        if (jsonResult instanceof byte[]) {
                            outputStream.write(jsonResult)
                        } else {
                            outputStream.write(jsonResult.toString().getBytes(StandardCharsets.UTF_8))
                        }
                    }
                })
                session.transfer(flowFile, REL_SUCCESS)
                log.info("Successfully retrieved Redis JSON for key: ${redisKey} at path: ${jsonPath}")
            } else {
                log.warn("No data found in Redis for key: ${redisKey} at path: ${jsonPath}")
                session.transfer(flowFile, REL_FAILURE)
            }
            break

        case 'DELETE':
            RedisConnectionManager.withConnection(context, log) { jedis ->
                jedis.sendCommand(jsonDelCommand, redisKey)
            }
            log.info("Successfully deleted key '${redisKey}' from Redis.")
            session.transfer(flowFile, REL_SUCCESS)
            break
            
        case 'MERGE':
            RedisConnectionManager.withConnection(context, log) { jedis ->
                session.read(flowFile, new InputStreamCallback() {
                    @Override
                    void process(InputStream inputStream) throws IOException {
                        byte[] jsonBytes = inputStream.readAllBytes()
                        jedis.sendCommand(jsonMergeCommand, redisKey, "\$", new String(jsonBytes, StandardCharsets.UTF_8))
                    }
                })
            }
            log.info("Successfully merged data into key '${redisKey}' in Redis.")
            session.transfer(flowFile, REL_SUCCESS)
            break

        default:
            log.error("Unsupported redis.operation: ${operation}")
            session.transfer(flowFile, REL_FAILURE)
            break
    }
    
} catch (Exception e) {
    log.error("Error during Redis operation '${operation}': " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
