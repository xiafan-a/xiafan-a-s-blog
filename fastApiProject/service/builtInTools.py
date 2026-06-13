"""
Built-in tools definition and implementation.
Simplified version - only file_read and web_search.
"""
import os
import logging
from typing import Dict, Any

from entity.Tool import ToolDefinition, ToolParameter
from service.toolRegistryService import tool_registry

logger = logging.getLogger(__name__)

# ============================================
# Tool Definitions
# ============================================

# 1. File Read Tool
file_read_tool = ToolDefinition(
    name="file_read",
    display_name="读取本地文件",
    description="读取本地文件的内容。接收文件完整路径作为参数，返回文件文本内容。支持的格式：txt, md, pdf, docx。",
    category="file",
    parameters=[
        ToolParameter(
            name="file_path",
            type="string",
            description="要读取的文件完整路径，例如：C:/Users/test/document.txt 或 /home/user/file.txt",
            required=True
        ),
        ToolParameter(
            name="max_chars",
            type="integer",
            description="最大读取字符数（可选），防止读取过大文件",
            required=False,
            default=10000
        )
    ]
)

# 2. Web Search Tool
web_search_tool = ToolDefinition(
    name="web_search",
    display_name="网页搜索",
    description="搜索互联网获取最新信息。当用户问题涉及实时信息、新闻或需要网络搜索时使用此工具。",
    category="web",
    parameters=[
        ToolParameter(
            name="query",
            type="string",
            description="搜索关键词或问题",
            required=True
        ),
        ToolParameter(
            name="num_results",
            type="integer",
            description="返回结果数量（可选）",
            required=False,
            default=5
        )
    ]
)

# 3. File Write Tool
file_write_tool = ToolDefinition(
    name="file_write",
    display_name="写入本地文件",
    description="将内容写入本地文件。如果文件已存在，会覆盖原内容。支持创建 txt, md, json, csv, xml, html 等文本文件。",
    category="file",
    parameters=[
        ToolParameter(
            name="file_path",
            type="string",
            description="要写入的文件完整路径，例如：C:/Users/test/output.txt 或 /home/user/output.txt",
            required=True
        ),
        ToolParameter(
            name="content",
            type="string",
            description="要写入的文件内容",
            required=True
        ),
        ToolParameter(
            name="encoding",
            type="string",
            description="文件编码（可选），默认 utf-8",
            required=False,
            default="utf-8"
        )
    ]
)

# 4. Knowledge Retrieve Tool
knowledge_retrieve_tool = ToolDefinition(
    name="knowledge_retrieve",
    display_name="知识库检索",
    description="根据chunk索引号检索知识库中相邻的知识块内容。当需要获取某个知识点前面或后面的相关知识时使用。",
    category="knowledge",
    parameters=[
        ToolParameter(
            name="knowledge_base_id",
            type="integer",
            description="知识库ID",
            required=True
        ),
        ToolParameter(
            name="chunk_index",
            type="integer",
            description="参考的chunk索引号",
            required=True
        ),
        ToolParameter(
            name="direction",
            type="string",
            description="检索方向: before(前面), after(后面), both(两者)",
            required=False,
            default="after"
        ),
        ToolParameter(
            name="limit",
            type="integer",
            description="返回数量限制",
            required=False,
            default=5
        )
    ]
)

# 5. Web Open Tool
web_open_tool = ToolDefinition(
    name="web_open",
    display_name="打开网页",
    description="导航到指定URL并在浏览器中打开网页。用于访问特定网站或页面。",
    category="web",
    parameters=[
        ToolParameter(
            name="url",
            type="string",
            description="要打开的网页URL，必须以http://或https://开头",
            required=True
        )
    ]
)

# 6. Web Scrape Tool
web_scrape_tool = ToolDefinition(
    name="web_scrape",
    display_name="获取页面内容",
    description="获取网页的文本内容。可选择性地使用CSS选择器提取特定部分。",
    category="web",
    parameters=[
        ToolParameter(
            name="url",
            type="string",
            description="要获取内容的网页URL",
            required=True
        ),
        ToolParameter(
            name="selector",
            type="string",
            description="可选的CSS选择器，用于提取页面特定部分",
            required=False
        )
    ]
)

# 7. Web Click Tool
web_click_tool = ToolDefinition(
    name="web_click",
    display_name="点击元素",
    description="点击页面上的指定元素。使用CSS选择器定位元素。",
    category="web",
    parameters=[
        ToolParameter(
            name="selector",
            type="string",
            description="元素的CSS选择器（如 #button-id, .class-name, button）",
            required=True
        )
    ]
)

# 8. Web Input Tool
web_input_tool = ToolDefinition(
    name="web_input",
    display_name="填写表单",
    description="向页面上的输入框或文本域填写内容。",
    category="web",
    parameters=[
        ToolParameter(
            name="selector",
            type="string",
            description="输入框的CSS选择器",
            required=True
        ),
        ToolParameter(
            name="value",
            type="string",
            description="要填写的文本内容",
            required=True
        )
    ]
)

# 9. Web Scroll Tool
web_scroll_tool = ToolDefinition(
    name="web_scroll",
    display_name="滚动页面",
    description="向上或向下滚动页面。",
    category="web",
    parameters=[
        ToolParameter(
            name="direction",
            type="string",
            description="滚动方向：up（向上）或 down（向下）",
            required=True,
            enum=["up", "down"]
        ),
        ToolParameter(
            name="pixels",
            type="integer",
            description="滚动的像素距离（默认500）",
            required=False,
            default=500
        )
    ]
)


# ============================================
# Tool Executors
# ============================================

async def execute_file_read(file_path: str, max_chars: int = 10000) -> Dict[str, Any]:
    """Execute file reading - reads local file content"""
    import mimetypes

    # Validate file path
    if not file_path:
        raise ValueError("文件路径不能为空")

    # Normalize path for Windows
    file_path = os.path.normpath(file_path)

    # Check file exists
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"文件不存在: {file_path}")

    # Check if it's a file (not directory)
    if not os.path.isfile(file_path):
        raise ValueError(f"路径不是文件: {file_path}")

    # Get file info
    file_size = os.path.getsize(file_path)
    file_name = os.path.basename(file_path)
    file_ext = os.path.splitext(file_path)[1].lower()

    # Supported file types
    supported_types = ['.txt', '.md', '.pdf', '.docx', '.csv', '.json', '.xml', '.html']

    # Determine how to read the file
    if file_ext in ['.txt', '.md', '.csv', '.json', '.xml', '.html']:
        # Text-based files - read directly
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
        except UnicodeDecodeError:
            # Try other encodings
            encodings = ['gbk', 'gb2312', 'utf-16']
            content = None
            for enc in encodings:
                try:
                    with open(file_path, 'r', encoding=enc) as f:
                        content = f.read()
                    break
                except UnicodeDecodeError:
                    continue

            if content is None:
                raise ValueError(f"无法解码文件，请确认文件编码为 UTF-8 或 GBK")

    elif file_ext == '.pdf':
        try:
            import pypdf
        except ImportError:
            raise ImportError("请安装 pypdf 库: pip install pypdf")

        content_parts = []
        with open(file_path, 'rb') as f:
            reader = pypdf.PdfReader(f)
            for page in reader.pages:
                page_text = page.extract_text()
                if page_text:
                    content_parts.append(page_text)

        content = "\n".join(content_parts)

    elif file_ext == '.docx':
        try:
            from docx import Document
        except ImportError:
            raise ImportError("请安装 python-docx 库: pip install python-docx")

        doc = Document(file_path)
        paragraphs = [para.text for para in doc.paragraphs if para.text.strip()]
        content = "\n".join(paragraphs)

    else:
        raise ValueError(f"不支持的文件类型: {file_ext}。支持的类型: {', '.join(supported_types)}")

    # Truncate if too long
    if len(content) > max_chars:
        content = content[:max_chars] + f"\n\n... (内容已截断，原文件共 {len(content)} 字符)"

    return {
        "file_name": file_name,
        "file_path": file_path,
        "file_size": file_size,
        "file_type": file_ext,
        "content_length": len(content),
        "content": content
    }


async def execute_web_search(query: str, num_results: int = 5) -> Dict[str, Any]:
    """Execute web search - searches the internet using DuckDuckGo"""
    try:
        from ddgs import DDGS

        with DDGS() as ddgs:
            results = list(ddgs.text(query, max_results=num_results))

        return {
            "query": query,
            "results": [
                {
                    "title": r.get("title", ""),
                    "url": r.get("href", ""),
                    "snippet": r.get("body", "")
                }
                for r in results
            ]
        }
    except ImportError:
        raise ImportError("请安装 duckduckgo-search: pip install duckduckgo-search")
    except Exception as e:
        return {"query": query, "results": [], "error": str(e)}


async def execute_file_write(file_path: str, content: str, encoding: str = "utf-8") -> Dict[str, Any]:
    """Execute file writing - writes content to a local file"""
    # Validate inputs
    if not file_path:
        raise ValueError("文件路径不能为空")

    if content is None:
        raise ValueError("文件内容不能为空")

    # Normalize path for Windows
    file_path = os.path.normpath(file_path)

    # Get directory and ensure it exists
    directory = os.path.dirname(file_path)
    if directory and not os.path.exists(directory):
        try:
            os.makedirs(directory, exist_ok=True)
        except OSError as e:
            raise ValueError(f"无法创建目录 {directory}: {str(e)}")

    # Determine file extension
    file_ext = os.path.splitext(file_path)[1].lower()

    # Supported text file types
    supported_types = ['.txt', '.md', '.json', '.csv', '.xml', '.html', '.js', '.css', '.py', '.java', '.c', '.cpp',
                       '.h']

    # Check if file type is supported for writing
    if file_ext and file_ext not in supported_types:
        raise ValueError(f"不支持写入此文件类型: {file_ext}。支持的类型: {', '.join(supported_types)}")

    try:
        # Write content to file
        with open(file_path, 'w', encoding=encoding) as f:
            f.write(content)

        # Get file info after writing
        file_size = os.path.getsize(file_path)
        file_name = os.path.basename(file_path)

        return {
            "success": True,
            "file_name": file_name,
            "file_path": file_path,
            "file_size": file_size,
            "content_length": len(content),
            "encoding": encoding,
            "message": f"文件写入成功，共写入 {len(content)} 字符"
        }

    except PermissionError:
        raise PermissionError(f"没有写入权限: {file_path}")
    except OSError as e:
        raise OSError(f"写入文件失败: {str(e)}")


async def execute_knowledge_retrieve(
        knowledge_base_id: int,
        chunk_index: int,
        direction: str = "after",
        limit: int = 5
) -> Dict[str, Any]:
    """根据index检索知识库相邻内容"""
    from database import SessionLocal
    from service.knowledgeChunkService import knowledge_chunk_service

    db = SessionLocal()
    try:
        chunks = knowledge_chunk_service.get_chunks_by_index_range(
            db=db,
            knowledge_base_id=knowledge_base_id,
            chunk_index=chunk_index,
            direction=direction,
            limit=limit
        )
        return {
            "knowledge_base_id": knowledge_base_id,
            "chunk_index": chunk_index,
            "direction": direction,
            "count": len(chunks),
            "chunks": [
                {
                    "chunk_id": c.id,
                    "chunk_index": c.chunk_index,
                    "content": c.content,
                    "metadata": c.chunk_metadata
                }
                for c in chunks
            ]
        }
    finally:
        db.close()


# ============================================
# Browser Automation Executors
# ============================================

# Global playwright browser instance
_browser = None
_page = None


def _get_browser():
    """Get or initialize playwright browser"""
    global _browser
    if _browser is None:
        from playwright.async_api import async_playwright
        from config.settings import BROWSER_HEADLESS
        import asyncio

        async def init_browser():
            global _browser
            p = async_playwright().start()
            _browser = await p.chromium.launch(headless=BROWSER_HEADLESS)

        asyncio.run(init_browser())
    return _browser


async def execute_web_open(url: str) -> Dict[str, Any]:
    """Execute web_open - navigate to URL"""
    from playwright.async_api import async_playwright
    from config.settings import BROWSER_HEADLESS, BROWSER_TIMEOUT

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=BROWSER_HEADLESS)
        page = await browser.new_page()
        await page.goto(url, timeout=BROWSER_TIMEOUT)

        title = await page.title()
        current_url = page.url

        await browser.close()

        return {
            "url": current_url,
            "title": title,
            "message": f"已打开网页: {title}"
        }


async def execute_web_scrape(url: str, selector: str = None) -> Dict[str, Any]:
    """Execute web_scrape - get page content"""
    from playwright.async_api import async_playwright
    from config.settings import BROWSER_HEADLESS, BROWSER_TIMEOUT

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=BROWSER_HEADLESS)
        page = await browser.new_page()
        await page.goto(url, timeout=BROWSER_TIMEOUT)

        if selector:
            content = await page.text_content(selector)
        else:
            content = await page.content()

        title = await page.title()

        await browser.close()

        return {
            "url": url,
            "title": title,
            "content": content,
            "selector_used": selector
        }


async def execute_web_click(selector: str) -> Dict[str, Any]:
    """Execute web_click - click element"""
    from playwright.async_api import async_playwright
    from config.settings import BROWSER_HEADLESS, BROWSER_TIMEOUT

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=BROWSER_HEADLESS)
        page = await browser.new_page()

        await page.click(selector, timeout=BROWSER_TIMEOUT)

        current_url = page.url
        title = await page.title()

        await browser.close()

        return {
            "selector": selector,
            "url": current_url,
            "title": title,
            "message": f"已点击元素: {selector}"
        }


async def execute_web_input(selector: str, value: str) -> Dict[str, Any]:
    """Execute web_input - fill form"""
    from playwright.async_api import async_playwright
    from config.settings import BROWSER_HEADLESS, BROWSER_TIMEOUT

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=BROWSER_HEADLESS)
        page = await browser.new_page()

        await page.fill(selector, value, timeout=BROWSER_TIMEOUT)

        return {
            "selector": selector,
            "value": value,
            "message": f"已填写内容到: {selector}"
        }


async def execute_web_scroll(direction: str, pixels: int = 500) -> Dict[str, Any]:
    """Execute web_scroll - scroll page"""
    from playwright.async_api import async_playwright
    from config.settings import BROWSER_HEADLESS, BROWSER_TIMEOUT

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=BROWSER_HEADLESS)
        page = await browser.new_page()

        if direction == "up":
            await page.evaluate(f"window.scrollBy(0, -{pixels})")
        else:
            await page.evaluate(f"window.scrollBy(0, {pixels})")

        await browser.close()

        return {
            "direction": direction,
            "pixels": pixels,
            "message": f"已{direction}滚动 {pixels} 像素"
        }


get_Date = ToolDefinition(
    name="get_Date",
    display_name="获取当前日期",
    description="获取当前日期。",
    category="date",
    parameters=[]
)


async def execute_get_Date() -> Dict[str, Any]:
    """获取当前日期"""
    from datetime import datetime
    current_date = datetime.now().strftime("%Y-%m-%d")
    return {
        "date": current_date
    }


# ============================================
# Register Built-in Tools
# ============================================

def register_builtin_tools():
    """Register all built-in tools to the tool registry"""
    tool_registry.register(file_read_tool, execute_file_read)
    tool_registry.register(file_write_tool, execute_file_write)
    tool_registry.register(web_search_tool, execute_web_search)
    tool_registry.register(knowledge_retrieve_tool, execute_knowledge_retrieve)
    tool_registry.register(web_open_tool, execute_web_open)
    tool_registry.register(web_scrape_tool, execute_web_scrape)
    tool_registry.register(web_click_tool, execute_web_click)
    tool_registry.register(web_input_tool, execute_web_input)
    tool_registry.register(web_scroll_tool, execute_web_scroll)
    tool_registry.register(get_Date, execute_get_Date)
    logger.info("Built-in tools registered: file_read, file_write, web_search, knowledge_retrieve, web_open, web_scrape, web_click, web_input, web_scroll, get_Date")


# Auto-register when module is imported
register_builtin_tools()
