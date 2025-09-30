import org.apache.nifi.processor.io.InputStreamCallback
import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.io.IOException
import java.io.InputStream
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

def redisKey = flowFile.getAttribute("sessionId") ?: "defaultKey"

try {
    // Reuses the SAME connection pool created by other scripts!
    RedisConnectionManager.withConnection(context, log) { jedis ->
        def jsonSetCommand = [getRaw: { "JSON.SET".bytes }] as redis.clients.jedis.commands.ProtocolCommand

        session.read(flowFile, new InputStreamCallback() {
            @Override
            void process(InputStream inputStream) throws IOException {
                byte[] jsonBytes = inputStream.readAllBytes()
                jedis.sendCommand(jsonSetCommand, redisKey, "\$", new String(jsonBytes, StandardCharsets.UTF_8))
            }
        })
    }
    
    session.transfer(flowFile, REL_SUCCESS)
    log.info("Successfully stored Redis JSON for key: ${redisKey}")

} catch (Exception e) {
    log.error("Error during Redis SET operation: " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}