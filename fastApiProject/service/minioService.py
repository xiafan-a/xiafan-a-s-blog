import io
import os
from typing import Optional
from datetime import timedelta
import hashlib

from fastapi import File, UploadFile
from minio import Minio
from minio.error import S3Error

from config.settings import MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_SECURE, MINIO_BUCKET, SUPPORTED_FILE_TYPES


class MinioService:
    def __init__(self):
        self.client = Minio(
            endpoint=MINIO_ENDPOINT,
            access_key=MINIO_ACCESS_KEY,
            secret_key=MINIO_SECRET_KEY,
            secure=MINIO_SECURE
        )
        self.bucket_name = MINIO_BUCKET

    def _ensure_bucket_exists(self):
        """确保存储桶存在，不存在则创建"""
        if not self.client.bucket_exists(self.bucket_name):
            self.client.make_bucket(self.bucket_name)

    def upload_file(self, file_data: bytes, object_name: str, content_type: str = "application/octet-stream") -> bool:
        """上传文件到MinIO

        Args:
            file_data: 文件二进制数据
            object_name: 存储对象名称（建议使用文件哈希命名）
            content_type: 文件MIME类型

        Returns:
            上传成功返回True，失败返回False
        """
        try:
            self._ensure_bucket_exists()
            file_size = len(file_data)
            self.client.put_object(
                bucket_name=self.bucket_name,
                object_name=object_name,
                data=io.BytesIO(file_data),
                length=file_size,
                content_type=content_type
            )
            return True
        except S3Error as e:
            print(f"MinIO上传失败: {e}")
            return False

    def upload_file_by_path(self, file_path: str, object_name: str, content_type: str = "application/octet-stream") -> bool:
        """从本地路径上传文件到MinIO

        Args:
            file_path: 本地文件路径
            object_name: 存储对象名称
            content_type: 文件MIME类型

        Returns:
            上传成功返回True，失败返回False
        """
        try:
            self._ensure_bucket_exists()
            self.client.fput_object(
                bucket_name=self.bucket_name,
                object_name=object_name,
                file_path=file_path,
                content_type=content_type
            )
            return True
        except S3Error as e:
            print(f"MinIO上传失败: {e}")
            return False

    def download_file(self, object_name: str) -> Optional[bytes]:
        """从MinIO下载文件

        Args:
            object_name: 存储对象名称

        Returns:
            文件二进制数据，失败返回None
        """
        try:
            response = self.client.get_object(
                bucket_name=self.bucket_name,
                object_name=object_name
            )
            data = response.read()
            response.close()
            response.release_conn()
            return data
        except S3Error as e:
            print(f"MinIO下载失败: {e}")
            return None

    def delete_file(self, object_name: str) -> bool:
        """从MinIO删除文件

        Args:
            object_name: 存储对象名称

        Returns:
            删除成功返回True，失败返回False
        """
        try:
            self.client.remove_object(
                bucket_name=self.bucket_name,
                object_name=object_name
            )
            return True
        except S3Error as e:
            print(f"MinIO删除失败: {e}")
            return False

    def get_presigned_url(self, object_name: str, expires: timedelta = timedelta(hours=1)) -> Optional[str]:
        """获取文件的预签名URL（用于临时访问）

        Args:
            object_name: 存储对象名称
            expires: URL过期时间

        Returns:
            预签名URL，失败返回None
        """
        try:
            url = self.client.presigned_get_object(
                bucket_name=self.bucket_name,
                object_name=object_name,
                expires=expires
            )
            return url
        except S3Error as e:
            print(f"MinIO获取预签名URL失败: {e}")
            return None

    def get_presigned_put_url(self, object_name: str, expires: timedelta = timedelta(hours=1),
                              content_type: str = "application/octet-stream") -> Optional[str]:
        """获取文件上传的预签名URL（用于客户端直传）

        Args:
            object_name: 存储对象名称
            expires: URL过期时间
            content_type: 文件MIME类型

        Returns:
            预签名上传URL，失败返回None
        """
        try:
            url = self.client.presigned_put_object(
                bucket_name=self.bucket_name,
                object_name=object_name,
                expires=expires,
                extra_query_params={"content-type": content_type}
            )
            return url
        except S3Error as e:
            print(f"MinIO获取预签名上传URL失败: {e}")
            return None

    def file_exists(self, object_name: str) -> bool:
        """检查文件是否存在

        Args:
            object_name: 存储对象名称

        Returns:
            存在返回True，不存在或失败返回False
        """
        try:
            self.client.stat_object(
                bucket_name=self.bucket_name,
                object_name=object_name
            )
            return True
        except S3Error:
            return False

    @staticmethod
    def generate_file_hash(file_data: bytes) -> str:
        """生成文件MD5哈希

        Args:
            file_data: 文件二进制数据

        Returns:
            文件哈希值（32位十六进制字符串）
        """
        return hashlib.md5(file_data).hexdigest()

    @staticmethod
    def generate_object_name(original_filename: str, file_hash: str) -> str:
        """生成存储对象名称

        Args:
            original_filename: 原始文件名
            file_hash: 文件哈希值

        Returns:
            对象名称，格式: files/{hash}/{original_filename}
        """
        ext = os.path.splitext(original_filename)[1] if original_filename else ""
        return f"{file_hash}{ext}"

    async def upload_file_with_original_format(self, file: UploadFile = File(...)) -> tuple[bool, str, Optional[str]]:
        """按文件原本格式上传到MinIO（支持FastAPI UploadFile）

        Args:
            file: FastAPI上传的文件对象

        Returns:
            (是否成功, 对象名称, 预签名URL或None)
        """
        ext = os.path.splitext(file.filename)[1].lower() if file.filename else ""
        content_type = SUPPORTED_FILE_TYPES.get(ext, "application/octet-stream")

        file_data = await file.read()
        file_hash = self.generate_file_hash(file_data)
        object_name = self.generate_object_name(file.filename, file_hash)
        success = self.upload_file(file_data, object_name, content_type)
        if not success:
            return False, object_name, None

        url = self.get_presigned_url(object_name)
        return True, object_name, url

    async def upload_file_with_original_byte(self, file_data: bytes,file_name: str,file_type: str) -> tuple[bool, str, Optional[str]]:
        """按文件原本格式上传到MinIO（支持FastAPI UploadFile）

        Args:
            file: FastAPI上传的文件对象

        Returns:
            (是否成功, 对象名称, 预签名URL或None)
        """
        ext = os.path.splitext(file_name)[1].lower() if file_name else ""
        content_type = SUPPORTED_FILE_TYPES.get(ext, "application/octet-stream")

        file_hash = self.generate_file_hash(file_data)
        object_name = self.generate_object_name(file_name, file_hash)
        success = self.upload_file(file_data, object_name, file_type)
        if not success:
            return False, object_name, None

        url = self.get_presigned_url(object_name)
        return True, object_name, url


# 全局MinioService实例
minio_service = MinioService()
