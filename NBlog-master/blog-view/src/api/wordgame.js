import axios from 'axios'

const API_BASE = '/api/v1'
// const API_BASE = 'http://localhost:8100/api/v1'

// const API_BASE = 'http://localhost:8100/api/v1' // 替换为你的后端API地址
function getAuthHeaders() {
    const identification = window.localStorage.getItem('identification')
    return identification ? { identification } : {}
}

export function getGuessWordList(skip = 0, limit = 100) {
    return axios.get(`${API_BASE}/guess-words`, {
        params: { skip, limit },
        headers: getAuthHeaders()
    })
}

export function getGuessWord(wordId) {
    return axios.get(`${API_BASE}/guess-words/${wordId}`, {
        headers: getAuthHeaders()
    })
}

export function createGuessWord(data) {
    return axios.post(`${API_BASE}/guess-words`, data, {
        headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' }
    })
}

export function submitGuess(wordId, guess) {
    return axios.post(`${API_BASE}/guess-words/${wordId}/guess`, { guess }, {
        headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' }
    })
}

export function getGuessRecords(wordId, skip = 0, limit = 100) {
    return axios.get(`${API_BASE}/guess-words/${wordId}/records`, {
        params: { skip, limit },
        headers: getAuthHeaders()
    })
}

export function getGuessWordStats() {
    return axios.get(`${API_BASE}/guess-words/stats/count`, {
        headers: getAuthHeaders()
    })
}
