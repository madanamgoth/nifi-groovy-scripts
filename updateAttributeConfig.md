# UpdateAttribute Configuration for Code Extraction

## Current Issue
The expression `${literal(${longCode}):substringBefore('#'):split('\*'):get(${index})}` fails due to syntax errors.

## Solution Options

### Option 1: Fixed Expression Language
```
Property Name: userInput
Property Value: ${longCode:substringBefore('#'):split('\\*'):get(${index})}
```

### Option 2: With Error Handling
```
Property Name: userInput  
Property Value: ${longCode:substringBefore('#'):split('\\*'):getDelimited(${index}, 'DEFAULT_VALUE')}
```

### Option 3: Using Groovy Script (Recommended)
Instead of UpdateAttribute, use ExecuteScript processor with our `extractCodeByIndex.groovy`:

1. **Processor**: ExecuteScript
2. **Script Engine**: Groovy
3. **Script File**: `/path/to/extractCodeByIndex.groovy`
4. **Input**: FlowFile with `longCode` and `index` attributes
5. **Output**: FlowFile with extracted value in `userInput` attribute

## Key Fixes Made:
1. **Removed `literal()`**: Not needed for direct attribute reference
2. **Fixed escaping**: `\\*` instead of `\*` for splitting on asterisk
3. **Proper quoting**: Removed unnecessary quotes around `#`

## Test Cases:
- Input: `longCode = "123*456*789*012#"`, `index = 0` → Output: `userInput = "123"`
- Input: `longCode = "123*456*789*012#"`, `index = 2` → Output: `userInput = "789"`
- Input: `longCode = "123*456*789*012#"`, `index = 5` → Output: Error or default value

## Recommended Configuration:
```
UpdateAttribute Properties:
- index: ${index:plus(1)}  // This looks correct
- userInput: ${longCode:substringBefore('#'):split('\\*'):get(${index})}
```

## Alternative with Bounds Checking:
```
userInput: ${longCode:substringBefore('#'):split('\\*'):get(${index:toNumber():mod(${longCode:substringBefore('#'):split('\\*'):count()})})}
```