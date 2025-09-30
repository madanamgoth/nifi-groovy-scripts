import com.example.utils.RedisConnectionManager
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.Relationship

def flowFile = session.get()
if (!flowFile) return

try {
    // 1. Get the field for HGET from the 'longCode' attribute.
    // This is the value like '*123*777#'
    def hgetField = flowFile.getAttribute('longCode')
    if (!hgetField) {
        throw new Exception("Attribute 'longCode' is missing or empty.")
    }

    // 2. Define the static HASH key
    def hgetKey = "composite_long_codes"
    def result = null

    // 3. Use the RedisConnectionManager to get a connection and execute the command
    RedisConnectionManager.withConnection(context, log) { jedis ->
        log.info("Executing HGET on key='{}' with field='{}'", hgetKey, hgetField)
        result = jedis.hget(hgetKey, hgetField)
    }

    // 4. Process the result from Redis
    if (result != null) {
        // A value was found
        flowFile = session.putAttribute(flowFile, 'hgetResult', result)
        log.info("HGET successful. Found value: '{}'", result)
        session.transfer(flowFile, REL_SUCCESS)
    } else {
        log.warn("No data found in LongCode for key: ${hgetField}")
        session.transfer(flowFile, REL_FAILURE)
    }

} catch (Exception e) {
    log.error("Error executing HGET command for longCode '{}': {}", flowFile.getAttribute('longCode'), e.getMessage(), e)
    flowFile = session.putAttribute(flowFile, "redis.error", e.getMessage())
    session.transfer(flowFile, REL_FAILURE)
}
