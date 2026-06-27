import re

with open('app/src/main/java/com/aicodeeditor/feature/agent/presentation/component/AIChatPanel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# We need to strictly match functions to not screw up {}
def find_block(start_str):
    idx = content.find(start_str)
    if idx == -1: return None, None
    
    # find matching brace
    brace_count = 0
    start_brace = content.find('{', idx)
    if start_brace == -1: return None, None
    
    brace_count = 1
    end_idx = start_brace + 1
    
    while brace_count > 0 and end_idx < len(content):
        if content[end_idx] == '{':
            brace_count += 1
        elif content[end_idx] == '}':
            brace_count -= 1
        end_idx += 1
        
    return idx, end_idx

blocks_to_remove = [
    "fun MarkdownContent(",
    "private fun CodeBlock(",
    "private sealed interface MdBlock",
    "private data class MdListItem",
    "private val ORDERED_ITEM",
    "private val UNORDERED_ITEM",
    "private val HEADING",
    "private fun parseMarkdownBlocks(",
    "private fun renderInlineMarkdown(",
    "private fun ToolMessageBody(",
    "private fun formatToolResult(",
    "private fun formatJsonData(",
    "private fun formatToolArgs(",
    "private fun ToolStatusDot(",
    "private fun ToolSection(",
    "private fun DiffStat(",
    "private fun DiffView(",
    "private fun DiffExpandToggle(",
    "private data class EditDiff",
    "private data class EditHunk",
    "private fun parseEditDiff(",
    "val DiffAddBg",
    "val DiffAddText",
    "val DiffRemoveBg",
    "val DiffRemoveText"
]

to_delete_ranges = []
for block in blocks_to_remove:
    start_idx, end_idx = find_block(block)
    if start_idx is not None and end_idx is not None:
        # Include any documentation comment above it
        doc_idx = content.rfind('/**', 0, start_idx)
        if doc_idx != -1 and content[doc_idx:start_idx].strip().startswith('/**'):
            start_idx = doc_idx
        to_delete_ranges.append((start_idx, end_idx))
    elif block.startswith("private val") or block.startswith("val ") or block.startswith("private data class"):
        # Match single line or simple block
        start_idx = content.find(block)
        if start_idx != -1:
            end_idx = content.find('\n', start_idx)
            if "data class" in block:
                # might span multiple lines if no braces
                pass
            to_delete_ranges.append((start_idx, end_idx))

# Hardcode the line ranges since text parsing gets complicated with Kotlin's syntax
# Instead of Python string search, let's just delete the exact lines since we already know them from our previous grep/head.
