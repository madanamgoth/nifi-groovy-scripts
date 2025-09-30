import org.apache.nifi.processor.io.OutputStreamCallback
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.io.OutputStream
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

def redisKey = flowFile.getAttribute("document.id") ?: "defaultKey"
def jsonPath = flowFile.getAttribute("redis.json.path")

if (!jsonPath) {
    log.error("Missing 'redis.json.path' attribute for GETBYQRY operation.")
    session.transfer(flowFile, REL_FAILURE)
    return
}

try {
    // Use withConnection for automatic resource management
    def jsonResult = RedisConnectionManager.withConnection(context, log) { jedis ->
        def jsonGetCommand = [getRaw: { "JSON.GET".bytes }] as redis.clients.jedis.commands.ProtocolCommand
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
    
} catch (Exception e) {
    log.error("Error during Redis GETBYQRY operation: " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
