import org.apache.nifi.processor.io.InputStreamCallback
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.io.InputStream
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

def redisKey = flowFile.getAttribute("sessionId") ?: "defaultKey"

try {
    // Use withConnection for automatic resource management
    RedisConnectionManager.withConnection(context, log) { jedis ->
        def jsonMergeCommand = [getRaw: { "JSON.MERGE".bytes }] as redis.clients.jedis.commands.ProtocolCommand

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

} catch (Exception e) {
    log.error("Error during Redis MERGE operation: " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
