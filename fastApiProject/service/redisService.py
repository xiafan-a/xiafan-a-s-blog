import os
import json
import uuid
from typing import Optional, Dict, Any
import redis
from datetime import datetime, timedelta


class RedisService:
    def __init__(self):
        self.host = os.getenv("REDIS_HOST", "localhost")
        self.port = int(os.getenv("REDIS_PORT", 6379))
        self.db = int(os.getenv("REDIS_DB", 0))
        self.password = os.getenv("REDIS_PASSWORD", None)
        self.session_ttl = int(os.getenv("REDIS_SESSION_TTL", 86400))

        self.client = None
        self._connect()

    def _connect(self):
        """连接Redis服务器"""
        try:
            self.client = redis.Redis(
                host=self.host,
                port=self.port,
                db=self.db,
                password=self.password if self.password else None,
                decode_responses=True,  # 自动解码为字符串
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True
            )
            # 测试连接
            self.client.ping()
        except Exception as e:
            self.client = None

    def is_connected(self) -> bool:
        """检查Redis连接状态"""
        if not self.client:
            return False
        try:
            self.client.ping()
            return True
        except:
            return False

    def create_session(self, user_id: int, user_data: Dict[str, Any]) -> Optional[str]:
        """创建用户会话"""
        if not self.is_connected():
            return None

        try:
            session_id = str(uuid.uuid4())
            session_key = f"session:{session_id}"
            user_key = f"user_session:{user_id}"

            # 存储会话数据
            session_data = {
                "user_id": user_id,
                "created_at": datetime.now().isoformat(),
                **user_data
            }

            # 设置会话
            self.client.setex(
                session_key,
                self.session_ttl,
                json.dumps(session_data)
            )

            # 关联用户ID和会话ID
            self.client.sadd(user_key, session_id)
            self.client.expire(user_key, self.session_ttl)

            return session_id
        except Exception:
            return None

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        """获取会话数据"""
        if not self.is_connected():
            return None

        try:
            session_key = f"session:{session_id}"
            data = self.client.get(session_key)
            if data:
                # 更新会话过期时间
                self.client.expire(session_key, self.session_ttl)
                return json.loads(data)
            return None
        except Exception as e:
            print(f"获取会话失败: {e}")
            return None

    def delete_session(self, session_id: str) -> bool:
        """删除会话"""
        if not self.is_connected():
            return False

        try:
            session_key = f"session:{session_id}"
            data = self.client.get(session_key)
            if data:
                session_data = json.loads(data)
                user_id = session_data.get("user_id")

                # 删除会话
                self.client.delete(session_key)

                # 从用户会话集合中移除
                if user_id:
                    user_key = f"user_session:{user_id}"
                    self.client.srem(user_key, session_id)

            return True
        except Exception as e:
            print(f"删除会话失败: {e}")
            return False

    def delete_user_sessions(self, user_id: int) -> bool:
        """删除用户的所有会话"""
        if not self.is_connected():
            return False

        try:
            user_key = f"user_session:{user_id}"
            session_ids = self.client.smembers(user_key)

            # 删除所有会话
            for session_id in session_ids:
                session_key = f"session:{session_id}"
                self.client.delete(session_key)

            # 删除用户会话集合
            self.client.delete(user_key)
            return True
        except Exception as e:
            print(f"删除用户会话失败: {e}")
            return False

    def check(self, token: str = None) -> bool:
        # 检查token是否存在
        if not self.client:
            return False
        return self.client.exists(f"token:{token}")
    
    def check_set_member(self, set_name: str, member: str) -> bool:
        # 检查集合中是否包含指定成员（处理带引号的情况）
        if not self.client:
            return False
        # 检查原始值
        if self.client.sismember(set_name, member):
            return True
        # 检查带双引号的值
        quoted_member = f'"{member}"'
        quoted_set_name = f'"{set_name}"'
        return self.client.sismember(quoted_set_name, quoted_member)

    def cache_guess_similarity(self, word_id: int, guess: str, similarity: float, ttl: int = 86400):
        """
        缓存猜词相似度
        Args:
            word_id: 关卡/目标词ID
            guess: 猜测词
            similarity: 相似度值
            ttl: 过期时间（秒），默认24小时
        """
        if not self.is_connected():
            return

        try:
            cache_key = f"/wordGame/{word_id}/guesses"
            similarity_key = f"/wordGame/{word_id}/similarity/{guess}"

            self.client.sadd(cache_key, guess)
            self.client.setex(similarity_key, ttl, str(similarity))
            self.client.expire(cache_key, ttl)
        except Exception as e:
            print(f"Redis缓存写入失败: {e}")

    def get_cached_similarity(self, word_id: int, guess: str) -> Optional[float]:
        """
        获取缓存的猜词相似度
        Args:
            word_id: 关卡/目标词ID
            guess: 猜测词
        Returns:
            缓存的相似度值，如果没有缓存则返回None
        """
        if not self.is_connected():
            return None

        try:
            cache_key = f"/wordGame/{word_id}/guesses"
            if not self.client.sismember(cache_key, guess):
                return None

            similarity_key = f"/wordGame/{word_id}/similarity/{guess}"
            similarity_str = self.client.get(similarity_key)
            if similarity_str:
                return float(similarity_str)
        except Exception as e:
            print(f"Redis缓存读取失败: {e}")
        return None

    def get_cached_guesses(self, word_id: int) -> list:
        """
        获取某关卡所有缓存的猜测词
        Args:
            word_id: 关卡/目标词ID
        Returns:
            猜测词列表
        """
        if not self.is_connected():
            return []

        try:
            cache_key = f"/wordGame/{word_id}/guesses"
            return list(self.client.smembers(cache_key))
        except Exception as e:
            print(f"Redis缓存读取失败: {e}")
        return []

    def clear_guess_cache(self, word_id: int):
        """
        清除某关卡的猜词缓存
        Args:
            word_id: 关卡/目标词ID
        """
        if not self.is_connected():
            return

        try:
            cache_key = f"/wordGame/{word_id}/guesses"
            guesses = self.client.smembers(cache_key)

            for guess in guesses:
                similarity_key = f"/wordGame/{word_id}/similarity/{guess}"
                self.client.delete(similarity_key)

            self.client.delete(cache_key)
        except Exception as e:
            print(f"Redis缓存清除失败: {e}")

    def cache_guess_words_list(self, words_data: str, ttl: int = 10800):
        """
        缓存猜字列表
        Args:
            words_data: JSON序列化的猜字列表数据
            ttl: 过期时间（秒），默认3小时
        """
        if not self.is_connected():
            return

        try:
            cache_key = "/wordGame/guess_words/list"
            self.client.setex(cache_key, ttl, words_data)
        except Exception as e:
            print(f"Redis缓存写入失败: {e}")

    def get_cached_guess_words_list(self) -> Optional[str]:
        """
        获取缓存的猜字列表
        Returns:
            JSON序列化的猜字列表数据，如果没有缓存则返回None
        """
        if not self.is_connected():
            return None

        try:
            cache_key = "/wordGame/guess_words/list"
            return self.client.get(cache_key)
        except Exception as e:
            print(f"Redis缓存读取失败: {e}")
        return None

    def invalidate_guess_words_cache(self):
        """
        使猜字列表缓存失效
        """
        if not self.is_connected():
            return

        try:
            cache_key = "/wordGame/guess_words/list"
            self.client.delete(cache_key)
        except Exception as e:
            print(f"Redis缓存清除失败: {e}")


# 全局Redis服务实例
redis_service = RedisService()
