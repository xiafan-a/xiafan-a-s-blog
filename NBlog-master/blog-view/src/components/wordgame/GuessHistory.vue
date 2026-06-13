<template>
    <div class="m-guess-history">
        <div class="m-history-column">
            <h4 class="m-column-title">
                <i class="star icon"></i>高相似度猜测
            </h4>
            <div class="m-guess-list">
                <div v-if="highSimilarityGuesses.length === 0" class="m-empty">
                    暂无高相似度猜测
                </div>
                <div v-for="(guess, index) in highSimilarityGuesses" :key="index"
                     class="m-guess-item m-high-similarity" :class="{'m-highlight': guess.isNew}">
                    <span class="m-word">{{ guess.word }}</span>
                    <span class="m-similarity">{{ (guess.similarity * 100).toFixed(3) }}%</span>
                </div>
            </div>
        </div>
        <div class="m-history-column">
            <h4 class="m-column-title">
                <i class="list icon"></i>猜测历史
            </h4>
            <div class="m-guess-list">
                <div v-if="otherGuesses.length === 0" class="m-empty">
                    暂无其他猜测
                </div>
                <div v-for="(guess, index) in otherGuesses" :key="index"
                     class="m-guess-item m-other">
                    <span class="m-word">{{ guess.word }}</span>
                    <span class="m-similarity">{{ (guess.similarity * 100).toFixed(3) }}%</span>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
export default {
    name: "GuessHistory",
    props: {
        highSimilarityGuesses: {
            type: Array,
            default: () => []
        },
        otherGuesses: {
            type: Array,
            default: () => []
        }
    }
}
</script>

<style scoped>
.m-guess-history {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
    padding: 20px;
    flex: 1;
    overflow-y: auto;
}

.m-history-column {
    background: #fff;
    border: 1px solid #e1e5e9;
    border-radius: 8px;
    padding: 16px;
    display: flex;
    flex-direction: column;
}

.m-column-title {
    margin: 0 0 12px 0;
    font-size: 14px;
    color: #333;
    display: flex;
    align-items: center;
    gap: 6px;
}

.m-column-title i {
    color: #ffc107;
}

.m-guess-list {
    flex: 1;
    overflow-y: auto;
}

.m-empty {
    color: #999;
    text-align: center;
    padding: 20px;
    font-size: 14px;
}

.m-guess-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 12px;
    border-radius: 4px;
    margin-bottom: 8px;
    font-size: 14px;
}

.m-high-similarity {
    background-color: #f8f9fa;
    border: 1px solid #e1e5e9;
    color: #666;
}

.m-other {
    background-color: #f8f9fa;
    border: 1px solid #e1e5e9;
    color: #666;
}

.m-word {
    font-weight: 500;
}

.m-highlight {
    animation: highlight-pulse 2s ease-out;
}

@keyframes highlight-pulse {
    0% {
        background-color: #fff3cd;
        border-color: #ffc107;
        transform: scale(1.05);
    }
    100% {
        background-color: #d4edda;
        border-color: #c3e6cb;
        transform: scale(1);
    }
}

.m-similarity {
    font-size: 12px;
    opacity: 0.8;
}
</style>
