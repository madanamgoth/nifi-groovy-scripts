# Redis Utils JAR for NiFi Groovy Scripts

This JAR contains the `RedisConnectionManager` utility class for managing Redis connections with connection pooling in NiFi Groovy scripts.

## Files Created

- `redis-utils-1.0.0.jar` - The utility JAR containing RedisConnectionManager
- `getRedisJSON-improved.groovy` - Updated script using the JAR with improved resource management

## Usage Options

### Option 1: Using @Grab (Recommended for Development)

Add this line at the top of your Groovy script:

```groovy
@Grab('file:///c:/Users/amgoth.naik1/Downloads/nifi-2.5.0-bin/nifi-2.5.0/script/redis-utils/build/libs/redis-utils-1.0.0.jar')
import com.example.utils.RedisConnectionManager
```

### Option 2: Add to NiFi Classpath (Recommended for Production)

1. Copy `redis-utils-1.0.0.jar` to NiFi's `lib` directory:
   ```
   cp build/libs/redis-utils-1.0.0.jar /path/to/nifi/lib/
   ```

2. Restart NiFi

3. In your Groovy scripts, simply import:
   ```groovy
   import com.example.utils.RedisConnectionManager
   ```

### Option 3: Module Path Configuration

Add the JAR to NiFi's module path by editing `nifi.properties`:

```properties
nifi.nar.library.directory.modules=./lib,./redis-utils
```

## Using RedisConnectionManager

### Basic Usage (Manual Connection Management)
```groovy
def jedis = RedisConnectionManager.getConnection(context, log)
try {
    // Your Redis operations
    jedis.set("key", "value")
    def result = jedis.get("key")
} finally {
    RedisConnectionManager.closeConnection(jedis)
}
```

### Recommended Usage (Automatic Resource Management)
```groovy
def result = RedisConnectionManager.withConnection(context, log) { jedis ->
    jedis.set("key", "value")
    return jedis.get("key")
}
```

### Monitor Pool Health
```groovy
def stats = RedisConnectionManager.getPoolStats(log)
log.info("Pool stats: ${stats}")
```

## Required NiFi Processor Properties

Add these properties to your ExecuteScript processor:

- `Redis Host` (default: localhost)
- `Redis Port` (default: 6379)  
- `Redis Password` (optional)
- `Redis Database` (default: 0)
- `Connection Timeout` (default: 2000ms)
- `Socket Timeout` (default: 2000ms)
- `Pool Max Total` (default: 20)
- `Pool Max Idle` (default: 10)
- `Pool Min Idle` (default: 2)
- `Pool Max Wait` (default: 5000ms)

## Benefits of Using the JAR

1. **Performance**: No need to load and compile the class from file every time
2. **Reusability**: One JAR can be used across all your Groovy scripts
3. **Maintenance**: Update the utility class in one place
4. **Version Control**: JAR files can be versioned and deployed consistently
5. **Connection Pooling**: Efficient Redis connection management
6. **Thread Safety**: Proper synchronization for concurrent access

## Rebuilding the JAR

If you need to modify the RedisConnectionManager:

1. Edit `src/main/groovy/com/example/utils/RedisConnectionManager.groovy`
2. Run `build-simple.bat` to rebuild the JAR
3. Update the JAR in your NiFi deployment

## Example Scripts

- `getRedisJSON.groovy` - Original script updated to use JAR
- `getRedisJSON-improved.groovy` - Improved version using `withConnection` method

Both scripts demonstrate how to use the JAR-based RedisConnectionManager for Redis JSON operations.