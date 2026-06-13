<template>
	<div class="ui container m-top">
		<div class="m-wordgame-container">
			<!-- Header -->
			<div class="m-wordgame-header">
				<span class="m-wordgame-title">猜字游戏</span>
				<div class="m-header-right">
					<span class="m-level-indicator">第 {{ currentLevel }} 关</span>
					<span class="m-guess-count">已猜 {{ totalGuesses }} 次</span>
				</div>
			</div>

			<!-- Input Area -->
			<div class="m-wordgame-input-area">
				<div class="m-input-wrapper">
					<input
						type="text"
						v-model="currentGuess"
						placeholder="输入词语进行猜测..."
						@keyup.enter="submitGuess"
						class="m-guess-input"
					/>
					<button class="ui button primary m-submit-btn" @click="submitGuess" :disabled="loading">
						提交猜测
					</button>
				</div>
				<div class="m-word-length-hint" v-if="wordLength > 0">
					<span class="m-hint-icon">?</span>
					<span class="m-hint-text">该词共 {{ wordLength }} 个字</span>
				</div>
				<div class="m-hint" v-if="lastGuess">
					{{ lastGuess.hint }}
				</div>
			</div>

			<!-- Guess History -->
			<GuessHistory
				:high-similarity-guesses="guessHistory.highSimilarity"
				:other-guesses="guessHistory.other"
			/>

			<!-- Level Selector -->
			<LevelSelector
				:key="totalLevels"
				:current-level="currentLevel"
				:total-levels="totalLevels"
				:passed-levels="passedLevels"
				:all-levels="wordList"
				@select-level="jumpToLevel"
			/>
		</div>

		<!-- Success Modal -->
		<div class="ui modal" :class="{active: showSuccessModal}">
			<div class="content">
				<h3>恭喜过关!</h3>
				<p>你猜到了词语: <strong>{{ targetWord }}</strong></p>
				<p>用了 {{ totalGuesses }} 次猜测</p>
			</div>
			<div class="actions">
				<button class="ui button" @click="closeModal">关闭</button>
				<button class="ui button primary" @click="goToNextLevel">下一关</button>
			</div>
		</div>
	</div>
</template>

<script>
import GuessHistory from '@/components/wordgame/GuessHistory'
import LevelSelector from '@/components/wordgame/LevelSelector'
import { getGuessWordList, submitGuess, getGuessRecords, getGuessWordStats } from '@/api/wordgame'

export default {
	name: "WordGame",
	components: {
		GuessHistory,
		LevelSelector
	},
	data() {
		return {
			currentLevel: 1,
			totalLevels: 0,
			passedLevels: new Set(),
			targetWord: '',
			targetHint: '',
			wordLength: 0,
			targetId: null,
			currentGuess: '',
			lastGuess: null,
			totalGuesses: 0,
			guessHistory: {
				highSimilarity: [],
				other: [],
				all: []
			},
			showSuccessModal: false,
			loading: false,
			wordList: [],
			wordMap: {},
			initialized: false,
			statsLoaded: false
		}
	},
	watch: {
		wordList: {
			handler(newList) {
				if (newList.length > 0 && !this.initialized) {
					// 按 id 排序，确保关卡顺序正确
					newList.sort((a, b) => a.id - b.id)
					// 构建以 id（关卡号）为 key 的映射
					this.wordMap = {}
					newList.forEach(word => {
						this.wordMap[word.id] = word
						if (word.is_passed) {
							this.passedLevels.add(word.id)
						}
					})
					// 确定默认关卡：优先选择第一个未通过的关卡
					const unpassedLevels = newList.filter(w => !w.is_passed)
					this.currentLevel = unpassedLevels.length > 0 ? unpassedLevels[0].id : newList[newList.length - 1].id
					this.$nextTick(() => {
						this.initLevel(this.currentLevel)
					})
					this.initialized = true
				}
			},
			immediate: true
		}
	},
	mounted() {
		if (this.wordList.length === 0) {
			this.loadWordList()
		}
	},
	methods: {
		async loadWordList() {
			try {
				const [statsRes, listRes] = await Promise.all([
					getGuessWordStats(),
					getGuessWordList(0, 1000)
				])
				if (statsRes.data.code === '200' || statsRes.data.code === 200) {
					this.totalLevels = statsRes.data.data.total
				}
				if (listRes.data.code === '200' || listRes.data.code === 200) {
					this.wordList = listRes.data.data
					this.initAfterLoad()
				}
			} catch (e) {
				this.$message.error('加载词库失败')
			}
		},
		initAfterLoad() {
			if (this.wordList.length === 0) return
			// 按 id 排序
			this.wordList.sort((a, b) => a.id - b.id)
			// 构建映射
			this.wordMap = {}
			this.wordList.forEach(word => {
				this.wordMap[word.id] = word
				if (word.is_passed) {
					this.passedLevels.add(word.id)
				}
			})
			// 确定默认关卡：优先选择第一个未通过的关卡
			const unpassedLevels = this.wordList.filter(w => !w.is_passed)
			this.currentLevel = unpassedLevels.length > 0 ? unpassedLevels[0].id : this.wordList[this.wordList.length - 1].id
			// 初始化目标词
			const wordData = this.wordMap[this.currentLevel]
			if (wordData) {
				this.targetId = wordData.id
				this.targetWord = wordData.word || ''
				this.targetHint = wordData.hint || ''
				this.wordLength = wordData.word
			}
			this.$nextTick(() => {
				this.loadGuessRecords()
			})
			this.initialized = true
			this.statsLoaded = true
		},
		async initLevel(level) {
			if (!this.wordMap || !this.wordMap[level]) return

			const wordData = this.wordMap[level]
			if (!wordData || wordData.id == null) {
				console.error('无效的关卡数据:', level, wordData)
				return
			}

			this.targetId = wordData.id
			this.targetWord = wordData.word || ''
			this.targetHint = wordData.hint || ''
			this.wordLength = wordData.word
			this.currentGuess = ''
			this.lastGuess = null
			this.totalGuesses = 0
			this.guessHistory.all = []
			this.guessHistory.highSimilarity = []
			this.guessHistory.other = []

			// 加载该词的猜测记录
			await this.loadGuessRecords()
		},
		async loadGuessRecords() {
			if (!this.targetId) return
			try {
				const res = await getGuessRecords(this.targetId, 0, 100)
				if (res.data.code === '200' || res.data.code === 200) {
					const records = res.data.data || []
					this.guessHistory.all = records.map(r => ({
						word: r.guess,
						similarity: r.similarity,
						isCorrect: r.is_correct || false,
						timestamp: new Date(r.created_at).getTime(),
						isNew: false
					}))

					// 左侧高相似度：按相似度从高到低排序，相同时按时间倒序
					this.guessHistory.highSimilarity = [...this.guessHistory.all].sort((a, b) => {
						if (a.similarity !== b.similarity) {
							return b.similarity - a.similarity
						}
						return b.timestamp - a.timestamp
					})
					// 右侧猜测历史：按时间倒序
					this.guessHistory.other = [...this.guessHistory.all].sort((a, b) => b.timestamp - a.timestamp)

					this.totalGuesses = records.length
				}
			} catch (e) {
				console.error('加载猜测记录失败', e)
			}
		},
		async doSubmitGuess() {
			const guess = this.currentGuess.trim()
			if (!guess || guess.length === 0) {
				return
			}
			if (!this.targetId) {
				this.$message.warning('请先选择关卡')
				return
			}

			this.loading = true
			try {
				const res = await submitGuess(this.targetId, guess)
				if (res.data.code === '200' || res.data.code === 200) {
					const data = res.data.data
					const guessRecord = {
						word: data.guess,
						similarity: data.similarity,
						timestamp: new Date(data.created_at).getTime(),
						isNew: true
					}

					let hint = `相似度: ${Math.round(data.similarity * 100)}%`
					if (data.is_correct) {
						hint = '完全正确!'
						this.targetWord = data.guess
						this.showSuccessModal = true
						// 更新本地passed状态
						this.passedLevels.add(this.currentLevel)
						// 更新映射中的is_passed
						if (this.wordMap[this.currentLevel]) {
							this.wordMap[this.currentLevel].is_passed = true
						}
					}

					this.lastGuess = { hint }

					// 检查是否重复猜测
					const isDuplicate = this.guessHistory.all.some(g => g.word === guessRecord.word)
					if (isDuplicate) {
						this.$message.warning('该词语已猜测过')
						this.currentGuess = ''
						return
					}

					this.totalGuesses++

					// 清除所有高亮标记，确保只有一个高亮（最新猜测）
					this.guessHistory.all.forEach(g => g.isNew = false)
					this.guessHistory.all.push(guessRecord)

					// 左侧高相似度：按相似度从高到低排序，相同时按时间倒序
					this.guessHistory.highSimilarity = [...this.guessHistory.all].sort((a, b) => {
						if (a.similarity !== b.similarity) {
							return b.similarity - a.similarity
						}
						return b.timestamp - a.timestamp
					})
					// 右侧猜测历史：按时间倒序
					this.guessHistory.other = [...this.guessHistory.all].sort((a, b) => b.timestamp - a.timestamp)

					this.currentGuess = ''
				}
			} catch (e) {
				this.$message.error('提交猜测失败')
			} finally {
				this.loading = false
			}
		},
		async submitGuess() {
			const guess = this.currentGuess.trim()
			if (!guess) return
			if (!this.targetId) {
				this.$message.warning('请先选择关卡')
				return
			}

			// 猜测前先刷新记录，确认该词尚未被其他用户猜出，同时同步最新记录到界面
			await this.loadGuessRecords()
			const alreadyGuessed = this.guessHistory.all.some(r => r.word && r.isCorrect)
			if (alreadyGuessed) {
				this.$message.warning('该词语已被其他用户猜出，请进入下一关')
				return
			}

			await this.doSubmitGuess()
		},
		async jumpToLevel(level) {
			if (level < 1 || level > this.totalLevels) return
			this.currentLevel = level
			await this.initLevel(level)
		},
		closeModal() {
			this.showSuccessModal = false
		},
		async goToNextLevel() {
			this.showSuccessModal = false
			if (this.currentLevel < this.totalLevels) {
				await this.jumpToLevel(this.currentLevel + 1)
			}
		}
	}
}
</script>

<style scoped>
.m-wordgame-container {
	display: flex;
	flex-direction: column;
	height: 85vh;
	border: 1px solid #e1e5e9;
	border-radius: 8px;
	overflow: hidden;
	box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
	background: #fff;
}

.m-wordgame-header {
	padding: 16px 20px;
	border-bottom: 1px solid #e1e5e9;
	background-color: #f8f9fa;
	display: flex;
	justify-content: space-between;
	align-items: center;
}

.m-wordgame-title {
	font-size: 18px;
	font-weight: 600;
	color: #333;
}

.m-header-right {
	display: flex;
	align-items: center;
	gap: 12px;
}

.m-level-indicator {
	font-size: 14px;
	color: #666;
	background: #e3f2fd;
	padding: 4px 12px;
	border-radius: 12px;
}

.m-guess-count {
	font-size: 14px;
	color: #666;
	background: #f0f0f0;
	padding: 4px 12px;
	border-radius: 12px;
}

.m-wordgame-input-area {
	padding: 20px;
	border-bottom: 1px solid #e1e5e9;
	background-color: #f8f9fa;
}

.m-input-wrapper {
	display: flex;
	gap: 12px;
}

.m-guess-input {
	flex: 1;
	padding: 12px 16px;
	font-size: 16px;
	border: 2px solid #e1e5e9;
	border-radius: 6px;
	transition: border-color 0.2s;
}

.m-guess-input:focus {
	outline: none;
	border-color: #2196f3;
}

.m-submit-btn {
	padding: 12px 24px;
	font-size: 14px;
}

.m-hint {
	margin-top: 10px;
	font-size: 13px;
	color: #666;
}

.m-word-length-hint {
	margin-top: 10px;
	position: relative;
	display: inline-block;
	cursor: default;
}

.m-hint-icon {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 20px;
	height: 20px;
	border-radius: 50%;
	background: #e3f2fd;
	color: #1976d2;
	font-size: 13px;
	font-weight: 600;
	line-height: 1;
}

.m-hint-text {
	display: none;
	margin-left: 8px;
	font-size: 13px;
	color: #666;
}

.m-word-length-hint:hover .m-hint-text {
	display: inline;
}

/* Modal styles */
.ui.modal {
	position: fixed;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
	z-index: 1000;
	background: white;
	padding: 24px;
	border-radius: 8px;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
	text-align: center;
}

.ui.modal.active {
	display: block;
}

.ui.modal .content h3 {
	margin-top: 0;
	color: #28a745;
}

.ui.modal .actions {
	display: flex;
	justify-content: center;
	gap: 12px;
	margin-top: 20px;
}
</style>
