package com.xiafan.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Mirrors redisService.py (sessions, identity-set membership check, guess-word caches). */
@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);
    private static final String GUESS_WORDS_LIST_KEY = "/wordGame/guess_words/list";

    private final AppProperties props;
    private final ObjectMapper om;
    private volatile JedisPool pool;

    public RedisService(AppProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        tryConnect();
    }

    private void tryConnect() {
        try {
            AppProperties.RedisConfig c = props.getRedis();
            JedisPoolConfig cfg = new JedisPoolConfig();
            cfg.setMaxTotal(16);
            cfg.setTestOnBorrow(true);
            String password = (c.getPassword() == null || c.getPassword().isEmpty()) ? null : c.getPassword();
            pool = new JedisPool(cfg, c.getHost(), c.getPort(), 5000, password, c.getDatabase());
            try (Jedis j = pool.getResource()) {
                j.ping();
            }
            log.info("Redis connected at {}:{}", c.getHost(), c.getPort());
        } catch (Exception e) {
            log.warn("Redis connection failed: {}", e.getMessage());
            pool = null;
        }
    }

    public boolean isConnected() {
        if (pool == null) {
            return false;
        }
        try (Jedis j = pool.getResource()) {
            j.ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @PreDestroy
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    public Optional<String> createSession(int userId, Map<String, Object> userData) {
        if (!isConnected()) {
            return Optional.empty();
        }
        try {
            String sessionId = UUID.randomUUID().toString();
            String sessionKey = "session:" + sessionId;
            String userKey = "user_session:" + userId;
            Map<String, Object> sessionData = new java.util.HashMap<>();
            sessionData.put("user_id", userId);
            sessionData.put("created_at", LocalDateTime.now().toString());
            if (userData != null) {
                sessionData.putAll(userData);
            }
            try (Jedis j = pool.getResource()) {
                j.setex(sessionKey, props.getRedis().getSessionTtl(), om.writeValueAsString(sessionData));
                j.sadd(userKey, sessionId);
                j.expire(userKey, props.getRedis().getSessionTtl());
            }
            return Optional.of(sessionId);
        } catch (Exception e) {
            log.warn("create session failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> getSession(String sessionId) {
        if (!isConnected()) {
            return Optional.empty();
        }
        try {
            String sessionKey = "session:" + sessionId;
            try (Jedis j = pool.getResource()) {
                String data = j.get(sessionKey);
                if (data == null) {
                    return Optional.empty();
                }
                j.expire(sessionKey, props.getRedis().getSessionTtl());
                return Optional.of(om.readValue(data, new TypeReference<Map<String, Object>>() {
                }));
            }
        } catch (Exception e) {
            log.warn("get session failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean deleteSession(String sessionId) {
        if (!isConnected()) {
            return false;
        }
        try {
            String sessionKey = "session:" + sessionId;
            try (Jedis j = pool.getResource()) {
                String data = j.get(sessionKey);
                if (data != null) {
                    Map<String, Object> sessionData = om.readValue(data, new TypeReference<Map<String, Object>>() {
                    });
                    Object userId = sessionData.get("user_id");
                    j.del(sessionKey);
                    if (userId != null) {
                        j.srem("user_session:" + userId, sessionId);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("delete session failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean deleteUserSessions(int userId) {
        if (!isConnected()) {
            return false;
        }
        try {
            String userKey = "user_session:" + userId;
            try (Jedis j = pool.getResource()) {
                Set<String> sessionIds = j.smembers(userKey);
                for (String sessionId : sessionIds) {
                    j.del("session:" + sessionId);
                }
                j.del(userKey);
            }
            return true;
        } catch (Exception e) {
            log.warn("delete user sessions failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean check(String token) {
        if (pool == null) {
            return false;
        }
        try (Jedis j = pool.getResource()) {
            return j.exists("token:" + token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkSetMember(String setName, String member) {
        if (pool == null) {
            return false;
        }
        try (Jedis j = pool.getResource()) {
            if (j.sismember(setName, member)) {
                return true;
            }
            String quotedMember = "\"" + member + "\"";
            String quotedSetName = "\"" + setName + "\"";
            return j.sismember(quotedSetName, quotedMember);
        } catch (Exception e) {
            return false;
        }
    }

    public void cacheGuessSimilarity(int wordId, String guess, double similarity, int ttl) {
        if (!isConnected()) {
            return;
        }
        try {
            String cacheKey = "/wordGame/" + wordId + "/guesses";
            String similarityKey = "/wordGame/" + wordId + "/similarity/" + guess;
            try (Jedis j = pool.getResource()) {
                j.sadd(cacheKey, guess);
                j.setex(similarityKey, ttl, String.valueOf(similarity));
                j.expire(cacheKey, ttl);
            }
        } catch (Exception e) {
            log.warn("Redis guess-cache write failed: {}", e.getMessage());
        }
    }

    public Optional<Double> getCachedSimilarity(int wordId, String guess) {
        if (!isConnected()) {
            return Optional.empty();
        }
        try {
            String cacheKey = "/wordGame/" + wordId + "/guesses";
            try (Jedis j = pool.getResource()) {
                if (!j.sismember(cacheKey, guess)) {
                    return Optional.empty();
                }
                String similarityKey = "/wordGame/" + wordId + "/similarity/" + guess;
                String s = j.get(similarityKey);
                if (s != null && !s.isEmpty()) {
                    return Optional.of(Double.parseDouble(s));
                }
            }
        } catch (Exception e) {
            log.warn("Redis guess-cache read failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public Set<String> getCachedGuesses(int wordId) {
        if (!isConnected()) {
            return Set.of();
        }
        try {
            String cacheKey = "/wordGame/" + wordId + "/guesses";
            try (Jedis j = pool.getResource()) {
                return new HashSet<>(j.smembers(cacheKey));
            }
        } catch (Exception e) {
            log.warn("Redis guess-cache read failed: {}", e.getMessage());
            return Set.of();
        }
    }

    public void clearGuessCache(int wordId) {
        if (!isConnected()) {
            return;
        }
        try {
            String cacheKey = "/wordGame/" + wordId + "/guesses";
            try (Jedis j = pool.getResource()) {
                Set<String> guesses = j.smembers(cacheKey);
                for (String guess : guesses) {
                    j.del("/wordGame/" + wordId + "/similarity/" + guess);
                }
                j.del(cacheKey);
            }
        } catch (Exception e) {
            log.warn("Redis guess-cache clear failed: {}", e.getMessage());
        }
    }

    public void cacheGuessWordsList(String wordsData, int ttl) {
        if (!isConnected()) {
            return;
        }
        try (Jedis j = pool.getResource()) {
            j.setex(GUESS_WORDS_LIST_KEY, ttl, wordsData);
        } catch (Exception e) {
            log.warn("Redis words-list cache write failed: {}", e.getMessage());
        }
    }

    public Optional<String> getCachedGuessWordsList() {
        if (!isConnected()) {
            return Optional.empty();
        }
        try (Jedis j = pool.getResource()) {
            String v = j.get(GUESS_WORDS_LIST_KEY);
            return v == null ? Optional.empty() : Optional.of(v);
        } catch (Exception e) {
            log.warn("Redis words-list cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void invalidateGuessWordsCache() {
        if (!isConnected()) {
            return;
        }
        try (Jedis j = pool.getResource()) {
            j.del(GUESS_WORDS_LIST_KEY);
        } catch (Exception e) {
            log.warn("Redis words-list cache clear failed: {}", e.getMessage());
        }
    }
}