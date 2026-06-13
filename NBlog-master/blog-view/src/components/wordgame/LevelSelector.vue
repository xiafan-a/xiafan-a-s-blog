<template>
    <div class="m-level-selector">
        <div class="m-level-info">
            <span class="m-total-count">共 {{ totalLevels }} 关</span>
            <span class="m-passed-count">已通过: {{ passedLevels.size }} 关</span>
        </div>
        <div class="m-level-nav">
            <div class="m-level-buttons">
                <button v-for="item in displayedLevels" :key="item.id"
                        class="ui button m-level-btn"
                        :class="{
                            'active': item.id === currentLevel,
                            'passed': passedLevels.has(item.id)
                        }"
                        @click="$emit('select-level', item.id)">
                    第{{ item.id }}关
                    <i v-if="passedLevels.has(item.id)" class="check icon"></i>
                </button>
                <button v-if="hasMore" class="ui button m-load-more-btn" @click="loadMore">
                    <i class="plus icon"></i> 加载更多
                </button>
            </div>
        </div>
        <div class="m-level-jump">
            <span>跳转至:</span>
            <input type="number" v-model.number="jumpLevel" :min="1" :max="totalLevels" @keyup.enter="handleJump" />
            <button class="ui button primary" @click="handleJump">跳转</button>
        </div>
    </div>
</template>

<script>
export default {
    name: "LevelSelector",
    props: {
        currentLevel: {
            type: Number,
            required: true
        },
        totalLevels: {
            type: Number,
            default: 0
        },
        passedLevels: {
            type: Set,
            default: () => new Set()
        },
        allLevels: {
            type: Array,
            default: () => []
        }
    },
    data() {
        return {
            jumpLevel: this.currentLevel,
            displayCount: 10
        }
    },
    computed: {
        displayedLevels() {
            return this.allLevels.slice(0, this.displayCount)
        },
        hasMore() {
            return this.displayCount < this.totalLevels
        }
    },
    watch: {
        currentLevel(val) {
            this.jumpLevel = val
        }
    },
    methods: {
        loadMore() {
            this.displayCount = Math.min(this.displayCount + 10, this.totalLevels)
        },
        handleJump() {
            const level = this.jumpLevel
            if (level >= 1 && level <= this.totalLevels) {
                this.$emit('select-level', level)
            } else {
                this.$message.warning(`关卡号必须在 1 ~ ${this.totalLevels} 之间`)
            }
        }
    }
}
</script>

<style scoped>
.m-level-selector {
    padding: 16px 20px;
    border-top: 1px solid #e1e5e9;
    background-color: #f8f9fa;
}

.m-level-info {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
    font-size: 14px;
    color: #666;
}

.m-total-count {
    font-size: 16px;
    font-weight: 600;
    color: #333;
}

.m-passed-count {
    color: #28a745;
}

.m-level-nav {
    display: flex;
    align-items: center;
    gap: 12px;
}

.m-level-buttons {
    display: flex;
    gap: 6px;
    flex: 1;
    flex-wrap: wrap;
}

.m-level-btn {
    min-width: 44px;
    padding: 8px 12px;
    border: 1px solid #e1e5e9;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.2s;
    background: #fff;
    position: relative;
}

.m-level-btn:hover {
    border-color: #2196f3;
}

.m-level-btn.active {
    background-color: #2196f3;
    color: white;
    border-color: #2196f3;
}

.m-level-btn.passed {
    background-color: #d4edda;
    border-color: #28a745;
    color: #155724;
}

.m-level-btn.passed.active {
    background-color: #2196f3;
    color: white;
    border-color: #2196f3;
}

.m-level-btn i.check {
    position: absolute;
    top: -6px;
    right: -6px;
    font-size: 12px;
    background: #28a745;
    color: white;
    border-radius: 50%;
    padding: 2px;
}

.m-load-more-btn {
    background: #e8f4fd;
    border: 1px dashed #2196f3;
    color: #2196f3;
}

.m-load-more-btn:hover {
    background: #2196f3;
    color: white;
    border-style: solid;
}

.m-level-jump {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 12px;
}

.m-level-jump input {
    width: 100px;
    padding: 8px 12px;
    border: 1px solid #e1e5e9;
    border-radius: 4px;
    text-align: center;
}

.m-level-jump input:focus {
    outline: none;
    border-color: #2196f3;
}
</style>
