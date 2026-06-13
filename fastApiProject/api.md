# 猜字游戏 API 文档

**基础路径**: `/api/v1`

---

## 1. 创建目标字

**请求**
```http
POST /api/v1/guess-words
Content-Type: application/json

{
  "word": "苹果",
  "hint": "一种水果",
  "difficulty": 1
}
```

**参数说明**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| word | string | 是 | 要猜的字/词 |
| hint | string | 否 | 提示信息 |
| difficulty | int | 否 | 难度等级: 1-简单, 2-中等, 3-困难 (默认1) |

**响应**
```json
{
  "code": "200",
  "data": {
    "id": 1,
    "word": "苹果",
    "hint": "一种水果",
    "difficulty": 1,
    "is_passed": false,
    "pass_count": 0,
    "created_at": "2026-04-23T10:00:00"
  }
}
```

---

## 2. 获取目标字

**请求**
```http
GET /api/v1/guess-words/{word_id}
```

**路径参数**
| 参数 | 类型 | 说明 |
|------|------|------|
| word_id | int | 目标字ID |

**响应**
```json
{
  "code": "200",
  "data": {
    "id": 1,
    "word": "苹果",
    "hint": "一种水果",
    "difficulty": 1,
    "is_passed": false,
    "pass_count": 0,
    "created_at": "2026-04-23T10:00:00"
  }
}
```

---

## 3. 列出目标字列表

**请求**
```http
GET /api/v1/guess-words?skip=0&limit=100
```

**查询参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| skip | int | 否 | 跳过数量 (默认0) |
| limit | int | 否 | 返回数量 (默认100) |

**响应**
```json
{
  "code": "200",
  "data": [
    {
      "id": 1,
      "word": "苹果",
      "hint": "一种水果",
      "difficulty": 1,
      "is_passed": false,
      "pass_count": 0,
      "created_at": "2026-04-23T10:00:00"
    }
  ]
}
```

---

## 4. 提交猜测

**请求**
```http
POST /api/v1/guess-words/{word_id}/guess
Content-Type: application/json

{
  "guess": "香蕉"
}
```

**路径参数**
| 参数 | 类型 | 说明 |
|------|------|------|
| word_id | int | 目标字ID |

**请求体**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| guess | string | 是 | 猜测的字/词 |

**响应**
```json
{
  "code": "200",
  "data": {
    "id": 1,
    "guess_word_id": 1,
    "guess": "香蕉",
    "similarity": 0.65,
    "is_correct": false,
    "created_at": "2026-04-23T10:05:00"
  }
}
```

**说明**
- `similarity`: 相似度 (0-1)，由 rerank API 计算
- `is_correct`: 是否猜中（当 guess 与目标词完全一致时为 true）
- 相同猜测词会命中 Redis 缓存，直接返回缓存的相似度结果

---

## 5. 获取猜测记录

**请求**
```http
GET /api/v1/guess-words/{word_id}/records?skip=0&limit=100
```

**路径参数**
| 参数 | 类型 | 说明 |
|------|------|------|
| word_id | int | 目标字ID |

**查询参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| skip | int | 否 | 跳过数量 (默认0) |
| limit | int | 否 | 返回数量 (默认100) |

**响应**
```json
{
  "code": "200",
  "data": [
    {
      "id": 1,
      "guess_word_id": 1,
      "guess": "香蕉",
      "similarity": 0.65,
      "is_correct": false,
      "created_at": "2026-04-23T10:05:00"
    },
    {
      "id": 2,
      "guess_word_id": 1,
      "guess": "苹果",
      "similarity": 1.0,
      "is_correct": true,
      "created_at": "2026-04-23T10:10:00"
    }
  ]
}
```

---

## 数据模型

### GuessWord (目标字)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 目标字唯一ID |
| word | string | 要猜的字/词 |
| hint | string | 提示信息 |
| difficulty | int | 难度等级: 1-简单, 2-中等, 3-困难 |
| is_passed | bool | 是否已通过 |
| pass_count | int | 通过次数 |
| created_at | datetime | 创建时间 |

### GuessRecord (猜测记录)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | int | 记录唯一ID |
| guess_word_id | int | 关联的目标字ID |
| guess | string | 猜测的字/词 |
| similarity | float | 相似度 (0-1) |
| is_correct | bool | 是否猜中 |
| created_at | datetime | 猜测时间 |
