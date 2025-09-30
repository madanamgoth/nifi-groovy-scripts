# How to Verify Single Shared Connection Pool

## 🔍 **Ways to Verify Pool Sharing:**

### **Method 1: Check Pool Hash Code**
The easiest way is to check if all scripts use the same pool instance:

1. Run `checkSharedPool.groovy` - it will show the pool hash
2. Run any of your Redis scripts (getRedisJSON, putRedisJSON, etc.)
3. Look in NiFi logs for the **Pool Instance Hash**
4. **If the hash is SAME across all scripts = Single Shared Pool** ✅

### **Method 2: Monitor Pool Statistics**
Watch the pool statistics change as different scripts run:

```
First Script:  Active: 1, Idle: 2  (pool created)
Second Script: Active: 1, Idle: 2  (reused same pool)
Third Script:  Active: 1, Idle: 2  (still same pool)
```

### **Method 3: Check NiFi Logs**
Look for these log messages:

```
✅ FIRST SCRIPT (creates pool):
"Redis connection pool successfully initialized"

✅ OTHER SCRIPTS (reuse pool):
"Pool Instance Hash: 123456789" (same number)
```

### **Method 4: Connection Count Test**
1. Configure pool with `Pool Max Total = 5`
2. Run 5 different scripts simultaneously
3. All should work fine (sharing the same 5 connections)
4. If each script had its own pool, you'd need 25 connections!

## 🎯 **What to Look For:**

### **✅ GOOD (Single Pool):**
- Same pool hash across all scripts
- Pool statistics change as scripts run
- Only ONE "pool initialized" message in logs
- Active connections increase/decrease together

### **❌ BAD (Multiple Pools):**
- Different pool hashes in different scripts
- Multiple "pool initialized" messages
- Each script creates its own connections

## 🚀 **Quick Verification Steps:**

1. **Restart NiFi** (fresh start)
2. **Run `checkSharedPool.groovy`** - note the hash
3. **Run `getRedisJSON.groovy`** - check if hash is same
4. **Run `putRedisJSON.groovy`** - check if hash is same
5. **All hashes should be IDENTICAL** = Single Pool! ✅

## 📊 **Pool Statistics Meaning:**
- **Active**: Connections currently being used by scripts
- **Idle**: Connections available in pool (not being used)
- **Total**: Active + Idle = Pool size
- **Hash**: Unique identifier for this pool instance

**If all your scripts show the same hash code, you have ONE shared pool!** 🎉