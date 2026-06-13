from typing import List, Dict, Any, Optional
from pathlib import Path
import os


class FileProcessor:
    """处理不同格式文件的读取"""

    def read_file(self, file_path: str) -> str:
        """
        根据文件扩展名选择读取方式
        Args:
            file_path: 文件路径
        Returns:
            文件文本内容
        """
        ext = Path(file_path).suffix.lower()

        if ext in [".txt", ".md"]:
            return self._read_text(file_path)
        elif ext == ".pdf":
            return self._read_pdf(file_path)
        elif ext == ".docx":
            return self._read_docx(file_path)
        else:
            raise ValueError(f"不支持的文件类型: {ext}")

    def _read_text(self, file_path: str) -> str:
        """读取纯文本文件"""
        encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']
        for encoding in encodings:
            try:
                with open(file_path, 'r', encoding=encoding) as f:
                    return f.read()
            except UnicodeDecodeError:
                continue
        raise ValueError(f"无法解码文件: {file_path}")

    def _read_pdf(self, file_path: str) -> str:
        """读取PDF文件"""
        try:
            import pypdf
        except ImportError:
            raise ImportError("请安装pypdf: pip install pypdf")

        text = []
        with open(file_path, 'rb') as f:
            reader = pypdf.PdfReader(f)
            for page in reader.pages:
                page_text = page.extract_text()
                if page_text:
                    text.append(page_text)
        return "\n".join(text)

    def _read_docx(self, file_path: str) -> str:
        """读取DOCX文件"""
        try:
            from docx import Document
        except ImportError:
            raise ImportError("请安装python-docx: pip install python-docx")

        doc = Document(file_path)
        paragraphs = [para.text for para in doc.paragraphs if para.text.strip()]
        return "\n".join(paragraphs)


class TextChunker:
    """文本分块处理"""

    def __init__(self, chunk_size: int = None, overlap: int = None):
        self.chunk_size = chunk_size or int(os.getenv("CHUNK_SIZE", "500"))
        self.overlap = overlap or int(os.getenv("CHUNK_OVERLAP", "50"))

    def chunk_text(self, text: str, metadata: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """
        将文本分割成重叠的块
        Args:
            text: 原始文本
            metadata: 附加元数据
        Returns:
            分块列表，每个分块包含 content, chunk_index, metadata
        """
        chunks = []
        start = 0
        chunk_index = 0

        # 预处理：移除多余空白
        text = self._clean_text(text)

        while start < len(text):
            end = start + self.chunk_size
            chunk_content = text[start:end]

            # 尝试在句子边界处分割
            if end < len(text):
                # 向后查找句子边界
                boundary = self._find_sentence_boundary(chunk_content)
                if boundary > self.chunk_size // 2:
                    chunk_content = chunk_content[:boundary]
                    end = start + boundary

            if chunk_content.strip():
                chunks.append({
                    "content": chunk_content.strip(),
                    "chunk_index": chunk_index,
                    "metadata": {
                        **(metadata or {}),
                        "start_char": start,
                        "end_char": min(end, len(text))
                    }
                })
                chunk_index += 1

            # 下一个块的起始位置（考虑重叠）
            start = end - self.overlap
            if start < 0:
                start = 0
            if start >= len(text):
                break

        return chunks

    def _clean_text(self, text: str) -> str:
        """清理文本"""
        # 移除多余的空白字符
        import re
        text = re.sub(r'\s+', ' ', text)
        return text.strip()

    def _find_sentence_boundary(self, text: str) -> int:
        """查找句子边界"""
        # 中英文句子结束标点
        sentence_endings = ['。', '！', '？', '；', '.', '!', '?', ';', '\n']

        # 从后向前查找最近的句子边界
        for i in range(len(text) - 1, max(0, len(text) - 100), -1):
            if text[i] in sentence_endings:
                return i + 1

        return len(text)


# 全局实例
file_processor = FileProcessor()
text_chunker = TextChunker()
